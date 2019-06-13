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
import org.apache.http.client.config.RequestConfig;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


public final class RequestHelper {
	private static final int connectTimeout = 28 * 1000;
	private static final int socketTimeout = connectTimeout;
	private static final int maxRedirects = 20;
	private static final int minSessionCount = 64;
	private static final int maxSessionCount = 128;
	private static final long requestMapTTL = 1000 * 60 * 5L;


	public static RequestConfig fromProxyMode(ProxyMode proxyMode) {
		RequestConfig.Builder builder = RequestConfig.custom()
				.setConnectTimeout(connectTimeout)
				.setSocketTimeout(socketTimeout)
				.setMaxRedirects(maxRedirects)
				.setContentCompressionEnabled(true)
				.setCircularRedirectsAllowed(false);

		if(proxyMode.isTunneled()) {
			builder.setProxy(Proxy.getByMode(proxyMode).getHost());
		}
		return builder.build();
	}

	public static UserAgent fromAgentMode(AgentMode agentMode, UserAgent current) {
		Objects.requireNonNull(agentMode);
		switch(agentMode) {
			case ROTATE:
				return UserAgentLoader.getNewUserAgent(current);
			case PUBLIC:
				return UserAgent.getPublic();
			default:
				throw new IllegalArgumentException("Invalid agent mode");
		}
	}

	public static int generateSessionThreshold() {
		return ThreadLocalRandom.current().nextInt(minSessionCount, maxSessionCount + 1);
	}

	public static String generateSessionId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	static boolean isRequestStateExpired(RequestMeta requestMeta) {
		return System.currentTimeMillis() - requestMeta.getLastRequestTime() > requestMapTTL;
	}

	static int getConnectTimeoutSeconds() {
		return connectTimeout / 1000;
	}
}
