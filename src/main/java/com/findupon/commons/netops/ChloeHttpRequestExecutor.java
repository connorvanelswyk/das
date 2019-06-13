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

import com.findupon.commons.netops.entity.ProxyMode;
import com.findupon.commons.netops.entity.RequestMeta;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;


/**
 * Used to capture and set proxy headers during the multiple stages of HTTPS negotiation while intercepting pertinent stages of
 * all protocol requests and responses to handle cross-cutting message transformations.
 *
 * Caveats to take note of:
 * 1) Pooled connections will persist their proxy connection (cache + preemptive auth) so the IP response can only be captured
 * initially.
 * 2) HTTPS request headers can only be sent on the initial connect, forcing two non-persistent connections to set headers. One
 * to receive the response, and two to actually set the headers.
 * 3) Non-HTTPS IP headers can (and should) be sent with every request. However the prefer-country header can only be sent first
 * to receive the proper IP response.
 * 4) This class works with {@link com.findupon.commons.netops.strategies.ChloeConnectionReuseStrategy} to invalidate
 * connections when a session switch is triggered from the client, a necessity for capturing the new proxy response and updating
 * the next request accordingly.
 */
public class ChloeHttpRequestExecutor extends HttpRequestExecutor {
	public static final ChloeHttpRequestExecutor INSTANCE = new ChloeHttpRequestExecutor();

	private final Logger logger = LoggerFactory.getLogger(ChloeHttpRequestExecutor.class);

	/* proxy response headers */
	public static final String proxyErrorResponseHeader = "X-ProxyMesh-Error";
	private static final String proxyIpResponseHeader = "X-ProxyMesh-IP";
	private static final String proxyIpNotFoundResponseHeader = "X-ProxyMesh-IP-Not-Found";

	/* proxy request headers */
	private static final String proxySetIpRequestHeader = "X-ProxyMesh-IP";
	private static final String proxyCountryRequestHeader = "X-ProxyMesh-Country";
	private static final String proxyTimeoutRequestHeader = "X-ProxyMesh-Timeout";


	/**
	 * Insert proxy headers on HTTPS requests.
	 * HTTPS requests with custom headers are difficult because the actual request headers are encrypted.
	 * The only point at which unencrypted data is sent to the proxy server is with the initial CONNECT method.
	 * When a client {@link RequestMeta} triggers a session switch,
	 * {@link com.findupon.commons.netops.strategies.ChloeConnectionReuseStrategy} will ensure that connection will is not
	 * persistent in the pool, thus forcing the initial CONNECT to trigger where we can assign a new IP. This has to happen
	 * twice - first to return the new assigned IP from the proxy and second to set our custom headers from their response.
	 * This is handled with {@link ChloeHttpRequestExecutor#processProxyIpResponse(HttpResponse, HttpContext, RequestMeta)}.
	 */
	@Override
	public void preProcess(HttpRequest request, HttpProcessor processor, HttpContext context) throws IOException, HttpException {
		RequestMeta requestMeta = (RequestMeta)context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META);
		if("CONNECT".equalsIgnoreCase(request.getRequestLine().getMethod())) {
			if(requestMeta.proxyUsed()) {
				if(requestMeta.getProxyIp() != null) {
					logger.debug("[ChloeHttpRequestExecutor] - Initial HTTPS CONNECT, setting IP proxy header. IP [{}] Host [{}]", requestMeta.getProxyIp(), requestMeta.getHost());
					request.setHeader(proxySetIpRequestHeader, requestMeta.getProxyIp());
				}
				if(ProxyMode.ROTATE_OPEN.equals(requestMeta.getProxyMode())) {
					logger.debug("[ChloeHttpRequestExecutor] - Initial HTTPS CONNECT, setting country and timeout headers. Host [{}]", requestMeta.getHost());
					request.setHeader(proxyCountryRequestHeader, Locale.US.getCountry());
					request.setHeader(proxyTimeoutRequestHeader, String.valueOf(RequestHelper.getConnectTimeoutSeconds()));
				}
			}
		}
		super.preProcess(request, processor, context);
	}

	/**
	 * Insert proxy headers on non-HTTPS requests.
	 * The proxy prefer IP header can (and should) be sent with every non-HTTPS request.
	 * The proxy location header (used only for open) MUST only be sent on the initial request before an IP is assigned. If you
	 * insert this header on every request, the proxy will not remove it and they will get passed to the remote host.
	 */
	@Override
	protected HttpResponse doSendRequest(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
		RequestMeta requestMeta = (RequestMeta)context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META);
		request.setHeader("Accept-encoding", "gzip");
		request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		request.setHeader("Connection", "keep-alive");
		request.setHeader("User-agent", requestMeta.getUserAgent().getAgent());
		request.setHeader("Upgrade-insecure-requests", "1");

		if(requestMeta.nonSSL() && requestMeta.proxyUsed()) {
			if(requestMeta.getProxyIp() != null) {
				request.setHeader(proxySetIpRequestHeader, requestMeta.getProxyIp());
			} else if(ProxyMode.ROTATE_OPEN.equals(requestMeta.getProxyMode())) {
				logger.debug("[ChloeHttpRequestExecutor] - Initial HTTP request, setting country and timeout headers. Host [{}]", requestMeta.getHost());
				request.setHeader(proxyCountryRequestHeader, Locale.US.getCountry());
				request.setHeader(proxyTimeoutRequestHeader, String.valueOf(RequestHelper.getConnectTimeoutSeconds()));
			}
		}
		return super.doSendRequest(request, conn, context);
	}

	/**
	 * Intercept all responses to catch any proxy headers via
	 * {@link ChloeHttpRequestExecutor#processProxyIpResponse(HttpResponse, HttpContext, RequestMeta)}.
	 */
	@Override
	protected HttpResponse doReceiveResponse(HttpRequest request, HttpClientConnection conn, HttpContext context) throws HttpException, IOException {
		RequestMeta requestMeta = (RequestMeta)context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META);

		boolean httpsConnect = "CONNECT".equalsIgnoreCase(request.getRequestLine().getMethod());
		boolean switchNextSession = false;

		if(!httpsConnect && (switchNextSession = requestMeta.incrementAndCheckSessionExpired())) {
			requestMeta.switchNextSession(context);
		}
		HttpResponse response = super.doReceiveResponse(request, conn, context);

		/* no need to handle any proxy headers if the next session is set to switch */
		if(requestMeta.proxyUsed() && (httpsConnect || !switchNextSession)) {
			processProxyIpResponse(response, context, requestMeta);
		}
		return response;
	}

	/**
	 * Handle the proxy response, if relevant, containing the IP meta response information.
	 * If the proxy headers do exist, {@link RequestMeta#forceProxyReAuth} is called which triggers a re-auth from the proxy
	 * where we can safely set our headers.
	 */
	private void processProxyIpResponse(HttpResponse response, HttpContext context, RequestMeta requestMeta) {
		Header proxyIpNotFoundHeader = response.getFirstHeader(proxyIpNotFoundResponseHeader);
		Header proxyIpHeader = response.getFirstHeader(proxyIpResponseHeader);

		if(proxyIpNotFoundHeader != null) {
			logger.warn("[ChloeHttpRequestExecutor] - Proxy IP not found, switching next session. Host: [{}] IP attempted: [{}] Session count: [{}]",
					requestMeta.getHost(), requestMeta.getProxyIp(), requestMeta.getSessionCount());
			requestMeta.switchNextSession(context);
			return;
		}
		String ip;
		if(proxyIpHeader != null
				&& StringUtils.isNotBlank((ip = proxyIpHeader.getValue()))
				&& requestMeta.getProxyIp() == null
				&& requestMeta.proxyUsed()) {

			logger.debug("[ChloeHttpRequestExecutor] - Proxy IP response received, set for next request. IP [{}] Host [{}]", ip, requestMeta.getHost());
			requestMeta.setProxyIp(ip);
			requestMeta.setForceProxyReAuth(true);
		}
	}
}
