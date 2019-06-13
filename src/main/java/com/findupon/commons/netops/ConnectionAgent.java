/*
 * Copyright 2015-2019 Connor Van Elswyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.findupon.commons.netops;

import com.findupon.cluster.entity.master.MasterNodeObjects;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.netops.entity.*;
import com.findupon.commons.netops.strategies.*;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.NetworkUtils;
import com.findupon.commons.utilities.TimeUtils;
import com.findupon.utilities.PropertyLoader;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.ConnectionClosedException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.CookieSpecRegistries;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class ConnectionAgent implements Closeable {
	public static final ConnectionAgent INSTANCE = new ConnectionAgent();

	private final Logger logger = LoggerFactory.getLogger(ConnectionAgent.class);
	private final CloseableHttpClient client;
	private final PoolingHttpClientConnectionManager connectionManager;
	private final ChloeConnectionPoolMonitor connectionPoolMonitor;
	private final Map<String, RequestMeta> hostRequestMetaMap = new ConcurrentHashMap<>();
	private final int maxConnectionsPerRoute = 16;
	private final HostRequestStateMode hostRequestStateMode = HostRequestStateMode.HOST_THREAD;

	private final boolean proxyModeOverride;


	private ConnectionAgent() {
		logger.debug("[ConnectionAgent] - Starting...");
		SSLContext sslContext;
		try {
			sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();
		} catch(NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
			throw new RuntimeException(e);
		}
		connectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", new SSLConnectionSocketFactory(sslContext, (s, session) -> true))
				.build());
		connectionManager.setDefaultSocketConfig(SocketConfig.custom()
				.setTcpNoDelay(true)
				.build());
		connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
		connectionManager.setMaxTotal(maxConnectionsPerRoute / 4 *
				(MasterNodeObjects.MAX_GENERIC_WORK_ORDERS_PER_NODE + MasterNodeObjects.MAX_LISTING_WORK_ORDERS_PER_NODE));

		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
				.setRequestExecutor(ChloeHttpRequestExecutor.INSTANCE)
				.setRedirectStrategy(ChloeRedirectStrategy.INSTANCE)
				.setDefaultCookieSpecRegistry(CookieSpecRegistries.createDefault())
				.setUserTokenHandler(context -> Thread.currentThread().getId())
				.setKeepAliveStrategy(ChloeKeepAliveStrategy.INSTANCE)
				.setServiceUnavailableRetryStrategy(ChloeRetryStrategy.INSTANCE)
				.setRetryHandler(ChloeHttpRequestRetryHandler.INSTANCE)
				.setConnectionBackoffStrategy(ChloeBackoffStrategy.INSTANCE)
				.setBackoffManager(new ChloeBackoffManager(connectionManager))
				.setConnectionReuseStrategy(ChloeConnectionReuseStrategy.INSTANCE)
				.setConnectionManager(connectionManager)
				.setProxyAuthenticationStrategy(ChloeProxyAuthenticationStrategy.INSTANCE);

		client = httpClientBuilder.build();
		connectionPoolMonitor = new ChloeConnectionPoolMonitor(connectionManager, hostRequestMetaMap);
		connectionPoolMonitor.start();
		Runtime.getRuntime().addShutdownHook(new Thread(this::closeQuietly));
		proxyModeOverride = !PropertyLoader.getBoolean("production");
		logger.debug("[ConnectionAgent] - Start completed. {}", proxyModeOverride ? "Public connections will be intercepted for non-production." : "");
	}

	public AgentResponse stealthDownload(String url) {
		return download(url, ProxyMode.ROTATE_LOCATION, AgentMode.ROTATE);
	}

	public AgentResponse download(String url, DataSource dataSource) {
		return download(url, dataSource.getProxyMode(), dataSource.getAgentMode());
	}

	public AgentResponse download(String url, DataSource dataSource, Long nodeId) {
		return download(url, dataSource.getProxyMode(), dataSource.getAgentMode(), true, nodeId);
	}

	public AgentResponse download(String url, ProxyMode proxyMode, AgentMode agentMode) {
		return download(url, proxyMode, agentMode, true);
	}

	public AgentResponse download(String url, ProxyMode proxyMode, AgentMode agentMode, boolean retryRequest) {
		return download(url, proxyMode, agentMode, retryRequest, null);
	}

	/**
	 * @param proxyMode PUBLIC will use one of our known IPs.
	 * @param agentMode PUBLIC will use our publicly facing user agent.
	 * @implNote MAKE SURE YOU KNOW WHAT YOU ARE DOING AND UNDERSTAND THE EXPOSURE OF USING THIS METHOD AND ITS OVERLOADED CALLERS
	 */
	public AgentResponse download(String url, ProxyMode proxyMode, AgentMode agentMode, boolean retryRequest, Long nodeId) {
		URI uri = ScoutServices.getUriFromString(url);
		if(uri == null && proxyModeOverride) {
			logger.debug("[ConnectionAgent] - Non-production manual URI formation for request [{}]", url);
			try {
				uri = new URI(url);
			} catch(URISyntaxException e) {
				logger.debug("[ConnectionAgent] - Could not manually parse URI from [{}]", url);
			}
		}
		if(uri == null) {
			return new AgentResponse(new AgentDecision(RequestedAction.PROCEED, "Malformed URI", HttpStatusCode.ELSE));
		}
		HttpGet request = new HttpGet(uri);
		RequestMeta requestMeta = setupRequestMeta(request, uri, proxyMode, agentMode);
		HttpClientContext context = ContextOps.create(requestMeta, url, nodeId, retryRequest);

		AgentResponse agentResponse;
		try {
			long start = System.currentTimeMillis();
			agentResponse = client.execute(request, new ChloeResponseHandler(context), context);
			requestLog(context, System.currentTimeMillis() - start);

			boolean switchOverride = false;
			if(requestMeta.proxyUsed()) {
				boolean captcha = StringUtils.containsIgnoreCase(agentResponse.getContent(), "Please verify you're a human");
				boolean openProxy = ProxyMode.ROTATE_OPEN.equals(proxyMode);
				boolean badStatus = HttpStatusCode.isNotSuccessful(agentResponse.getDecision().getStatusCode());

				if(captcha || openProxy && badStatus) {
					requestMeta.switchNextSession(context);
					switchOverride = true;
				}
			}
			if(!switchOverride && isSuccessfulResponse(agentResponse, true)) {
				agentResponse.setContent(ScoutServices.normalize(agentResponse.getContent(), false));
				Document document = Jsoup.parse(agentResponse.getContent(), lastRedirect(uri, context).toString());
				agentResponse.setDocument(document);
			}
		} catch(Exception e) {
			AgentDecision agentDecision = new AgentDecision(RequestedAction.PROCEED, ExceptionUtils.getRootCauseMessage(e), exceptionStatusTranslator(e, uri));
			agentResponse = new AgentResponse(agentDecision);
			if(ProxyMode.ROTATE_OPEN.equals(proxyMode)) {
				requestMeta.switchNextSession(context);
			}
		}
		return agentResponse;
	}

	public Document xmlDownload(String url, DataSource dataSource) {
		return xmlDownload(url, dataSource.getProxyMode(), dataSource.getAgentMode());
	}

	public Document xmlDownload(String url, ProxyMode proxyMode, AgentMode agentMode) {
		Document document = download(url, proxyMode, agentMode).getDocument();
		if(document == null) {
			return null;
		} else {
			try {
				return Jsoup.parse(document.html(), url, Parser.xmlParser());
			} catch(Exception e) {
				logger.error("[ConnectionAgent] - Error parsing XML document at [{}]", url);
				return null;
			}
		}
	}

	public Pair<BaseRobotRules, String> downloadRobotRules(String url, DataSource dataSource) {
		return downloadRobotRules(url, dataSource.getProxyMode(), dataSource.getAgentMode());
	}

	public Pair<BaseRobotRules, String> downloadRobotRules(String url, ProxyMode proxyMode, AgentMode agentMode) {
		SimpleRobotRulesParser rulesParser = new SimpleRobotRulesParser();
		BaseRobotRules rules = null;
		String content = StringUtils.EMPTY;

		String robotsUrl = ScoutServices.parseByProtocolAndHost(url);
		if(robotsUrl != null) {
			robotsUrl += "robots.txt";
		}
		URI robotsUri = ScoutServices.getUriFromString(robotsUrl);
		if(robotsUri == null) {
			logger.debug("[ConnectionAgent] - Invalid robots.txt URI, assuming allow all [{}]", url);
			rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
		}
		if(rules == null) {
			AgentResponse response = download(robotsUri.toString(), proxyMode, agentMode, true);

			if(!isSuccessfulResponse(response, false)) {
				int statusCode = response.getDecision().getStatusCode();
				logger.debug("[ConnectionAgent] - Error downloading robots.txt, handling failed fetch rules. Status code: [{}] Error: [{}] URL: [{}]",
						statusCode, response.getDecision().getMessage(), robotsUrl);

				if(HttpStatusCode.ELSE == statusCode) {
					logger.debug("[ConnectionAgent] - Unhandled exception downloading robots.txt, assuming allow all [{}]", robotsUrl);
					rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);

				} else if(HttpStatusCode.isNotSuccessful(statusCode)) {
					rules = rulesParser.failedFetch(response.getDecision().getStatusCode());

				} else {
					rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_NONE);
					rules.setDeferVisits(true);
				}
			}
			// great success
			if(rules == null) {
				if(response.getContent() != null) {
					content = response.getContent();
				}
				String botName = ProxyMode.PUBLIC.equals(proxyMode) ? UserAgent.getPublic().getAgent() : "Vo4nidN4LpeOU8xTdEcNPZm6kLdbURG8";
				rules = rulesParser.parseContent(url, content.getBytes(), "text/html", botName);
			}
		}
		if(!rules.isAllowed("/")) {
			logger.debug("[ConnectionAgent] - Root directory not allowed, treating as defer visits [{}]", url);
			rules.setDeferVisits(true);
		}
		if(rules.getCrawlDelay() > 10_000) {
			logger.debug("[ConnectionAgent] - Crawl delay [{}] > 10, setting to 10", rules.getCrawlDelay());
			rules.setCrawlDelay(10_000);
		} else if(rules.getCrawlDelay() < 0) {
			rules.setCrawlDelay(0);
		}
		logger.debug("[ConnectionAgent] - Robots.txt stats: Defer visits [{}] Allow none [{}] Allow all [{}] Crawl delay [{}] URL [{}]",
				rules.isDeferVisits(), rules.isAllowNone(), rules.isAllowAll(), rules.getCrawlDelay(), robotsUrl);
		return Pair.of(rules, content);
	}

	private RequestMeta setupRequestMeta(HttpRequestBase request, URI uri, ProxyMode proxyMode, AgentMode agentMode) {
		Objects.requireNonNull(proxyMode, "Proxy mode must be set! Request URI attempted: " + uri.toString());
		Objects.requireNonNull(agentMode, "Agent mode must be set! Request URI attempted: " + uri.toString());

		if(proxyModeOverride && ProxyMode.PUBLIC.equals(proxyMode)) {
			proxyMode = ProxyMode.ROTATE_LOCATION;
		}
		if(proxyModeOverride && AgentMode.PUBLIC.equals(agentMode)) {
			agentMode = AgentMode.ROTATE;
		}
		RequestMeta requestMeta;
		String key;
		switch(hostRequestStateMode) {
			case HOST_THREAD:
				key = uri.getHost() + "~" + Thread.currentThread().getId();
				break;
			case HOST_ONLY:
				key = uri.getHost();
				break;
			default:
				throw new IllegalStateException("Invalid hostRequestStateMode");
		}
		synchronized(hostRequestMetaMap) {
			if((requestMeta = hostRequestMetaMap.get(key)) == null) {
				requestMeta = new RequestMeta(proxyMode, agentMode, uri);
				hostRequestMetaMap.put(key, requestMeta);

			} else if(requestMeta.differentMode(proxyMode, agentMode)) {
				requestMeta = new RequestMeta(proxyMode, agentMode, uri);
				if(hostRequestMetaMap.replace(key, requestMeta) == null) {
					logger.error("[ConnectionAgent] - Host request meta map replace came back null! " +
							"There must be a previous key mapping at this stage. Connection attempted: [{}]", uri.toString());
				}
			}
		}
		request.setConfig(requestMeta.getConfig());
		requestMeta.setLastRequestTime(System.currentTimeMillis());
		return requestMeta;
	}

	private void requestLog(HttpClientContext context, long downloadTimeMillis) {
		RequestMeta requestMeta = context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META, RequestMeta.class);
		String url = context.getAttribute(ContextOps.CTX_AGENT_CURRENT_URL, String.class);
		String ip = requestMeta.proxyUsed() ? requestMeta.getProxyIp() == null ? "undetermined" : requestMeta.getProxyIp() : NetworkUtils.getExternalIp();
		ip = StringUtils.substringBefore(ip, ":");
		logger.debug(String.format("%s%-24s %-26s %-22s %-52s %s",
				ContextOps.nodePre(context),
				"DL: [" + TimeUtils.formatConditionalSeconds(downloadTimeMillis, 2000) + "]",
				"PX: [" + (requestMeta.proxyUsed() ? requestMeta.getConfig().getProxy().getHostName() : "not in use") + "]",
				"IP: [" + ip + "]",
				"AG: [" + requestMeta.getUserAgent().getDescription() + "]",
				"URL: [" + url + "]"));
	}

	private URI lastRedirect(URI currentUri, HttpClientContext context) {
		List<URI> locations = context.getRedirectLocations();
		if(locations != null && !locations.isEmpty()) {
			return locations.get(locations.size() - 1);
		}
		return currentUri;
	}

	private boolean isSuccessfulResponse(AgentResponse agentResponse, boolean verifyContentLength) {
		return agentResponse.getDecision() != null
				&& agentResponse.getDecision().getStatusCode() > 199
				&& agentResponse.getDecision().getStatusCode() < 300
				&& agentResponse.getContent() != null
				&& (!verifyContentLength || agentResponse.getContent().length() > 128);
	}

	private int exceptionStatusTranslator(Exception e, URI uri) {
		int statusCode;
		if(e instanceof IllegalStateException) {
			logger.trace(ConsoleColors.red("[ConnectionAgent] - Illegal state, shutdown was probably triggered [{}]"), ExceptionUtils.getRootCauseMessage(e));
			statusCode = HttpStatusCode.ELSE;
		} else if(e instanceof ConnectTimeoutException) {
			logger.debug(ConsoleColors.red("[ConnectionAgent] - Connection timeout interpreted from IO exception [{}]"), uri.toString());
			statusCode = HttpStatusCode.SC_REQUEST_TIMEOUT;
		} else if(e instanceof SocketTimeoutException) {
			logger.debug(ConsoleColors.red("[ConnectionAgent] - Socket timeout interpreted from IO exception [{}]"), uri.toString());
			statusCode = HttpStatusCode.SC_REQUEST_TIMEOUT;
		} else if(e instanceof ClientProtocolException) {
			logger.debug(ConsoleColors.red("[ConnectionAgent] - Client protocol exception (possible redirect gone bad) [{}]"), uri.toString());
			statusCode = HttpStatusCode.SC_SEE_OTHER;
		} else if(e instanceof ConnectionClosedException) {
			logger.debug(ConsoleColors.red("[ConnectionAgent] - Client connection was closed, shutdown was probably triggered"));
			statusCode = HttpStatusCode.ELSE;
		} else if(e instanceof SocketException) {
			logger.debug(ConsoleColors.red("[ConnectionAgent] - Socket exception [{}] URI: [{}]"), ExceptionUtils.getRootCauseMessage(e), uri.toString());
			statusCode = HttpStatusCode.ELSE;
		} else {
			logger.warn(ConsoleColors.red("[ConnectionAgent] - Non-handled exception! [{}] URI: [{}]"), ExceptionUtils.getRootCauseMessage(e), uri.toString());
			statusCode = HttpStatusCode.ELSE;
		}
		return statusCode;
	}

	@Override
	public void close() throws IOException {
		logger.info("[ConnectionAgent] - Shutting down...");
		connectionPoolMonitor.shutdown();
		client.close();
		connectionManager.close();
		logger.info("[ConnectionAgent] - Shutdown completed.");
	}

	private void closeQuietly() {
		try {
			close();
		} catch(IOException e) {
			logger.error("[ConnectionAgent] - Error closing the agent!", e);
		}
	}
}
