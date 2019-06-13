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

package com.findupon.commons.netops.strategies;

import com.findupon.commons.netops.ChloeHttpRequestExecutor;
import com.findupon.commons.netops.ContextOps;
import com.findupon.commons.netops.entity.HttpStatusCode;
import com.findupon.commons.netops.entity.RequestMeta;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


public class ChloeRetryStrategy implements ServiceUnavailableRetryStrategy {
	public static final ChloeRetryStrategy INSTANCE = new ChloeRetryStrategy();
	private static final Logger logger = LoggerFactory.getLogger(ChloeRetryStrategy.class);
	private static final long retryInterval = 8 * 1000;
	private static final int maxRetries = 2;


	@Override
	public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
		Objects.requireNonNull(response);
		Objects.requireNonNull(context);

		int status = response.getStatusLine().getStatusCode();

		if(HttpStatusCode.isSuccessful(status)) {
			return false;
		}
		if(HttpStatusCode.isRedirectCode(status)) {
			return false;
		}
		if(!HttpStatusCode.isRetriableCode(status)) {
			return false;
		}

		HttpClientContext clientContext = HttpClientContext.adapt(context);
		RequestMeta requestMeta = clientContext.getAttribute(ContextOps.CTX_AGENT_REQUEST_META, RequestMeta.class);
		String currentUrl = clientContext.getAttribute(ContextOps.CTX_AGENT_CURRENT_URL, String.class);

		if(executionCount > maxRetries) {
			logger.debug("{}[ChloeRetryStrategy] - Max retries [{}] reached. Status: [{}] URL: [{}]",
					ContextOps.nodePre(clientContext), maxRetries, status, currentUrl);
			return false;
		}
		Boolean explicitRetry = clientContext.getAttribute(ContextOps.CTX_AGENT_RETRY, Boolean.class);
		if(explicitRetry != null && !explicitRetry) {
			return false;
		}
		if(requestMeta.proxyUsed()) {
			Header proxyError = response.getFirstHeader(ChloeHttpRequestExecutor.proxyErrorResponseHeader);
			if(proxyError != null) {
				if(!HttpStatusCode.isProxyIgnorable(status)) {
					return false;
				}
				logger.debug("{}[ChloeRetryStrategy] - Retry request [{}/{}] triggered based on proxy error [{}]",
						ContextOps.nodePre(context), executionCount, maxRetries, proxyError.getValue());
				return true;
			}
		} else if(HttpStatusCode.isBackoffCode(status)) {
			return false; // public connection + backoff status = no go
		}
		logger.debug("{}[ChloeServiceUnavailableRetryStrategy] - Retry request [{}/{}] waiting [~{}] seconds. Status: [{}] URL: [{}]",
				ContextOps.nodePre(context), executionCount, maxRetries, retryInterval / 1000, status, currentUrl);
		return true;
	}

	@Override
	public long getRetryInterval() {
		return ThreadLocalRandom.current().nextLong(retryInterval, retryInterval + 4000);
	}
}
