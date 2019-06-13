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

import com.findupon.commons.netops.ContextOps;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class ChloeProxyAuthenticationStrategy implements AuthenticationStrategy {
	public static final ChloeProxyAuthenticationStrategy INSTANCE = new ChloeProxyAuthenticationStrategy();

	private final Logger logger = LoggerFactory.getLogger(ChloeProxyAuthenticationStrategy.class);


	@Override
	public boolean isAuthenticationRequested(HttpHost host, HttpResponse response, HttpContext context) {
		return response.getStatusLine().getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED;
	}

	@Override
	public Map<String, Header> getChallenges(HttpHost authHost, HttpResponse response, HttpContext context) throws MalformedChallengeException {
		if(response == null) {
			throw new IllegalArgumentException("Response may not be null");
		}
		Header[] headers = response.getHeaders(AUTH.PROXY_AUTH);
		Map<String, Header> headerMap = new HashMap<>(headers.length);
		for(Header header : headers) {
			CharArrayBuffer buffer;
			int pos;
			if(header instanceof FormattedHeader) {
				buffer = ((FormattedHeader)header).getBuffer();
				pos = ((FormattedHeader)header).getValuePos();
			} else {
				String value = header.getValue();
				if(value == null) {
					throw new MalformedChallengeException("Header value is null");
				}
				buffer = new CharArrayBuffer(value.length());
				buffer.append(value);
				pos = 0;
			}
			while(pos < buffer.length() && HTTP.isWhitespace(buffer.charAt(pos))) {
				pos++;
			}
			int beginIndex = pos;
			while(pos < buffer.length() && !HTTP.isWhitespace(buffer.charAt(pos))) {
				pos++;
			}
			int endIndex = pos;
			headerMap.put(buffer.substring(beginIndex, endIndex).toLowerCase(Locale.ROOT), header);
		}
		return headerMap;
	}

	@Override
	public Queue<AuthOption> select(Map<String, Header> challenges, HttpHost authHost, HttpResponse response, HttpContext context) throws MalformedChallengeException {
		if(challenges == null || authHost == null || response == null || context == null) {
			throw new IllegalArgumentException("Select params may not be null");
		}
		HttpClientContext clientContext = HttpClientContext.adapt(context);
		Queue<AuthOption> options = new LinkedList<>();
		Lookup<AuthSchemeProvider> registry = clientContext.getAuthSchemeRegistry();
		if(registry == null) {
			return options;
		}
		CredentialsProvider credentialsProvider = clientContext.getCredentialsProvider();
		if(credentialsProvider == null) {
			logger.debug("{}Credentials provider not set in the context", ContextOps.nodePre(context));
			return options;
		}
		Header challenge = challenges.get(AuthSchemes.BASIC.toLowerCase(Locale.ROOT));
		if(challenge != null) {
			AuthSchemeProvider authSchemeProvider = registry.lookup(AuthSchemes.BASIC);
			if(authSchemeProvider == null) {
				throw new IllegalArgumentException("Authentication scheme not supported");
			}
			AuthScheme authScheme = authSchemeProvider.create(context);
			authScheme.processChallenge(challenge);
			Credentials credentials = credentialsProvider.getCredentials(new AuthScope(
					authHost.getHostName(),
					authHost.getPort(),
					authScheme.getRealm(),
					authScheme.getSchemeName()));
			if(credentials != null) {
				options.add(new AuthOption(authScheme, credentials));
			}
		} else {
			throw new IllegalArgumentException("Challenge for authentication scheme not available");
		}
		return options;
	}

	@Override
	public void authSucceeded(HttpHost authHost, AuthScheme authScheme, HttpContext context) {
		if(authHost == null || authScheme == null || context == null) {
			throw new IllegalArgumentException("Auth succeeded params may not be null");
		}
	}

	@Override
	public void authFailed(HttpHost authHost, AuthScheme authScheme, HttpContext context) {
		if(authHost == null || context == null) {
			throw new IllegalArgumentException("Auth failed params may not be null");
		}
		logger.warn("{}Proxy authentication failure! Host [{}]", ContextOps.nodePre(context), authHost.toString());
	}
}
