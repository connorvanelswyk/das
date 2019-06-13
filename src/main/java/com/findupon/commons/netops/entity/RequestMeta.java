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

package com.findupon.commons.netops.entity;

import com.findupon.commons.netops.ContextOps;
import com.findupon.commons.netops.RequestHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.SetCookie;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


public class RequestMeta implements Serializable {
	private static final long serialVersionUID = -2722221010446085840L;
	private static final Logger logger = LoggerFactory.getLogger(RequestMeta.class);

	private final AtomicInteger sessionCount;
	private boolean forceProxyReAuth;
	private int sessionThreshold;
	private long lastRequestTime;
	private String sessionId;

	private final ProxyMode proxyMode;
	private final AgentMode agentMode;
	private final URI requestUri;
	private RequestConfig config;
	private UserAgent userAgent;
	private String proxyIp;


	public boolean incrementAndCheckSessionExpired() {
		return sessionCount.incrementAndGet() > sessionThreshold;
	}

	public void switchNextSession(HttpContext context) {
		this.userAgent = RequestHelper.fromAgentMode(agentMode, userAgent);
		this.config = RequestHelper.fromProxyMode(proxyMode);
		this.sessionThreshold = RequestHelper.generateSessionThreshold();
		this.sessionCount.set(0);
		this.sessionId = RequestHelper.generateSessionId();
		this.proxyIp = null;
		this.forceProxyReAuth = true;

		HttpClientContext clientContext = HttpClientContext.adapt(context);
		Date now = new Date();
		int cookiesCleared = 0;

		for(Cookie cookie : clientContext.getCookieStore().getCookies()) {
			if(StringUtils.containsIgnoreCase(cookie.getDomain(), requestUri.getHost())) {
				((SetCookie)cookie).setExpiryDate(now);
				cookiesCleared++;
			}
		}
		logger.debug("{}[RequestMeta] - Session switch triggered. Cookies cleared [{}] Next after [{}] requests. Host [{}]",
				ContextOps.nodePre(clientContext), cookiesCleared, sessionThreshold, requestUri.getHost());
	}

	public RequestMeta(ProxyMode proxyMode, AgentMode agentMode, URI uri) {
		this.proxyMode = Objects.requireNonNull(proxyMode);
		this.agentMode = Objects.requireNonNull(agentMode);
		this.requestUri = Objects.requireNonNull(uri);
		this.userAgent = RequestHelper.fromAgentMode(agentMode, null);
		this.config = RequestHelper.fromProxyMode(proxyMode);
		this.sessionThreshold = RequestHelper.generateSessionThreshold();
		this.sessionId = RequestHelper.generateSessionId();
		this.sessionCount = new AtomicInteger();
	}

	public boolean proxyUsed() {
		return !ProxyMode.PUBLIC.equals(proxyMode) && config.getProxy() != null;
	}

	public ProxyMode getProxyMode() {
		return proxyMode;
	}

	public String getProxyIp() {
		return proxyIp;
	}

	public void setProxyIp(String proxyIp) {
		this.proxyIp = proxyIp;
	}

	public RequestConfig getConfig() {
		return config;
	}

	public String getHost() {
		return requestUri.getHost();
	}

	public UserAgent getUserAgent() {
		return userAgent;
	}

	public long getLastRequestTime() {
		return lastRequestTime;
	}

	public void setLastRequestTime(long lastRequestTime) {
		this.lastRequestTime = lastRequestTime;
	}

	public boolean isForceProxyReAuth() {
		return forceProxyReAuth;
	}

	public void setForceProxyReAuth(boolean forceProxyReAuth) {
		this.forceProxyReAuth = forceProxyReAuth;
	}

	public boolean nonSSL() {
		return !"https".equalsIgnoreCase(requestUri.getScheme());
	}

	public boolean differentMode(ProxyMode proxyMode, AgentMode agentMode) {
		return !this.proxyMode.equals(proxyMode) || !this.agentMode.equals(agentMode);
	}

	public int getSessionCount() {
		return sessionCount.get();
	}
}
