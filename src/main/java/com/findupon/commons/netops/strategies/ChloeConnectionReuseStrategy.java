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
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.HttpContext;


public class ChloeConnectionReuseStrategy extends DefaultConnectionReuseStrategy {
	public static final ChloeConnectionReuseStrategy INSTANCE = new ChloeConnectionReuseStrategy();

	/**
	 * If a proxied request was executed and IP response headers were received ({@link RequestMeta#forceProxyReAuth} will be {@code true}) from
	 * {@link com.findupon.commons.netops.ChloeHttpRequestExecutor#doReceiveResponse(HttpRequest, HttpClientConnection, HttpContext)}),
	 * do not persist the connection, forcing re-auth to the proxy where we can safely set our new request headers.
	 */
	@Override
	public boolean keepAlive(HttpResponse response, HttpContext context) {
		RequestMeta requestMeta = (RequestMeta)context.getAttribute(ContextOps.CTX_AGENT_REQUEST_META);
		int statusCode = response.getStatusLine().getStatusCode();

		if(requestMeta.proxyUsed() && response.getFirstHeader(ChloeHttpRequestExecutor.proxyErrorResponseHeader) != null) {
			return false;
		}
		if(statusCode == HttpStatusCode.SC_OK) {
			if(requestMeta.isForceProxyReAuth()) {
				requestMeta.setForceProxyReAuth(false);
				return false;
			}
		}
		return !HttpStatusCode.isBackoffCode(statusCode) && super.keepAlive(response, context);
	}
}
