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

import com.findupon.commons.netops.entity.Proxy;
import com.findupon.commons.netops.entity.RequestMeta;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.protocol.HttpContext;


public interface ContextOps {

	String CTX_AGENT_CURRENT_URL = "agent.current-url";
	String CTX_AGENT_NODE_ID = "agent.node-id";
	String CTX_AGENT_RETRY = "agent.retry-request";
	String CTX_AGENT_REQUEST_META = "agent.request-meta";


	static String nodePre(HttpContext context) {
		Object nodeIdObj = context.getAttribute(CTX_AGENT_NODE_ID);
		if(nodeIdObj == null) {
			return StringUtils.EMPTY;
		}
		Long nodeId;
		try {
			nodeId = Long.parseLong(nodeIdObj.toString());
		} catch(NumberFormatException e) {
			return StringUtils.EMPTY;
		}
		return "[Node " + (nodeId < 10 ? "0" : "") + nodeId + "] - ";
	}

	static HttpClientContext create(RequestMeta requestMeta, String url, Long nodeId, boolean retryRequest) {
		HttpClientContext context = HttpClientContext.create();
		context.setAttribute(ContextOps.CTX_AGENT_REQUEST_META, requestMeta);
		context.setAttribute(ContextOps.CTX_AGENT_CURRENT_URL, url);
		context.setAttribute(ContextOps.CTX_AGENT_NODE_ID, nodeId);
		context.setAttribute(ContextOps.CTX_AGENT_RETRY, retryRequest);
		context.setUserToken(Thread.currentThread().getId());

		if(requestMeta.proxyUsed()) {
			// remove to disable preemptive auth
			BasicAuthCache authCache = new BasicAuthCache();
			authCache.put(requestMeta.getConfig().getProxy(), new BasicScheme());
			context.setAuthCache(authCache);

			CredentialsProvider provider = new BasicCredentialsProvider();
			provider.setCredentials(new AuthScope(requestMeta.getConfig().getProxy()), Proxy.getCredentials());
			context.setCredentialsProvider(provider);
		}
		return context;
	}
}
