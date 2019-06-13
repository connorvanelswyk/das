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

import com.findupon.commons.netops.entity.*;
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static com.findupon.commons.utilities.ConsoleColors.red;
import static com.findupon.commons.utilities.ConsoleColors.yellow;


public class ChloeResponseHandler implements ResponseHandler<AgentResponse> {
	private static final Logger logger = LoggerFactory.getLogger(ChloeResponseHandler.class);

	private final HttpClientContext context;
	private final RequestMeta requestMeta;
	private final String requestUrl;


	ChloeResponseHandler(HttpClientContext context) {
		this.requestMeta = Objects.requireNonNull(context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META, RequestMeta.class));
		this.requestUrl = Objects.requireNonNull(context.getAttribute(ContextOps.CTX_AGENT_CURRENT_URL, String.class));
		this.context = context;
	}

	@Override
	public AgentResponse handleResponse(HttpResponse response) throws IOException {
		String content;
		try {
			if(requestUrl.endsWith(".gz")) {
				content = decompressGzip(response.getEntity());
			} else {
				content = EntityUtils.toString(response.getEntity());
			}
		} finally {
			EntityUtils.consumeQuietly(response.getEntity());
		}
		AgentDecision agentDecision = getAndProcessResponseDecision(response);
		AgentResponse agentResponse = new AgentResponse(agentDecision, content);

		handleBotBlockers(agentResponse);
		return agentResponse;
	}

	private AgentDecision getAndProcessResponseDecision(HttpResponse response) {
		AgentDecision agentDecision;
		StatusLine line = response.getStatusLine();
		int status = line.getStatusCode();

		if(HttpStatusCode.isSuccessful(status)) {
			// all systems go
			agentDecision = new AgentDecision(RequestedAction.PROCEED, status);
		} else {
			ServerResponse serverResponse;
			if(requestMeta.proxyUsed() && response.getFirstHeader(ChloeHttpRequestExecutor.proxyErrorResponseHeader) != null) {
				// proxy error
				serverResponse = ServerResponseProcessor.getProxyResponse(line);
				if(HttpStatusCode.isProxyIgnorable(status)) {
					reportStatusError(status, line, serverResponse, true);
				} else {
					logger.error("{}[ChloeResponseHandler] - ProxyMesh error! Code: [{}] Message: [{}] URL: [{}]",
							ContextOps.nodePre(context), status, serverResponse.getAgentDecision().getMessage(), requestUrl);
				}
				handleProxyState(serverResponse.getProxyState());
			} else {
				// remote site error
				serverResponse = ServerResponseProcessor.getRemoteResponse(line);
				reportStatusError(status, line, serverResponse, false);
			}
			// muy rare case
			if(RequestedAction.ABORT_CRAWL.equals(serverResponse.getAgentDecision().getAction())) {
				logger.error("[ChloeResponseHandler] - {}Decision made to abort crawl [{}] based on remote status code [{}]",
						ContextOps.nodePre(context), ScoutServices.parseByProtocolAndHost(requestUrl), status);
			}
			agentDecision = serverResponse.getAgentDecision();
		}
		return agentDecision;
	}

	private void handleProxyState(ProxyState proxyState) {
		switch(proxyState) {
			case SUCCESS:
				break;
			case IP_SWITCH_NEEDED:
				requestMeta.switchNextSession(context);
				break;
			case OPEN_PROXY_NEEDED:
				logger.error("[ChloeResponseHandler] - Source needs to be switched to proxy mode open [{}]", requestMeta.getHost());
				break;
			case FATAL:
				// maybe handle this a bit more gracefully?
				throw new RuntimeException(ContextOps.nodePre(context) + "Fatal proxy error");
		}
	}

	private void handleBotBlockers(AgentResponse agentResponse) {
		if(StringUtils.containsIgnoreCase(agentResponse.getContent(), "distilnetworks")) {
			String error = String.format(ContextOps.nodePre(context) + "Found Distil Networks managed site, abort! [%s]", requestUrl);
			agentResponse.getDecision().setAction(RequestedAction.ABORT_CRAWL);
			agentResponse.getDecision().setMessage(error + " Raw HTML: \n\n" + agentResponse.getContent());
		}
	}

	private String decompressGzip(HttpEntity entity) throws IOException {
		String content = null;
		boolean badCompression = false;
		try(InputStream gzipStream = new GZIPInputStream(entity.getContent())) {
			content = IOUtils.toString(gzipStream, StandardCharsets.UTF_8);
		} catch(Exception e) {
			logger.warn("{}[ChloeResponseHandler] - Error decompressing gzip file. Attempting w/o compression. File: [{}] Error: [{}]",
					ContextOps.nodePre(context), requestUrl, ExceptionUtils.getRootCauseMessage(e));
			badCompression = true;
		}
		if(!badCompression) {
			EntityUtils.consumeQuietly(entity);
		} else {
			content = EntityUtils.toString(entity);
		}
		return content;
	}

	private void reportStatusError(int statusCode, StatusLine statusLine, ServerResponse serverResponse, boolean fromProxy) {
		String reporter = fromProxy ? "Proxy" : "Remote";
		if(HttpStatusCode.isBackoffCode(statusCode)) {
			logger.warn(red("{}[ChloeResponseHandler] - {} backoff code: [{}] Server reason: [{}] Translation: [{}] URL: [{}]"),
					ContextOps.nodePre(context), reporter, statusCode,
					StringUtils.isBlank(statusLine.getReasonPhrase()) ? "none provided" : statusLine.getReasonPhrase(),
					serverResponse.getAgentDecision().getMessage(), requestUrl);
		} else {
			logger.debug(yellow("{}[ChloeResponseHandler] - {} status error code: [{}] Server reason: [{}] Translation: [{}] URL: [{}]"),
					ContextOps.nodePre(context), reporter, statusCode, statusLine.getReasonPhrase(),
					serverResponse.getAgentDecision().getMessage(), requestUrl);
		}
	}

	private String getFormattedHeadersAndReason(HttpResponse response) {
		StringBuilder headerBuilder = new StringBuilder();
		headerBuilder.append("Reason: [").append(response.getStatusLine().getReasonPhrase()).append("] ");
		for(Header header : response.getAllHeaders()) {
			headerBuilder.append(header.getName()).append(": [").append(header.getValue()).append("] ");
		}
		return headerBuilder.toString();
	}
}
