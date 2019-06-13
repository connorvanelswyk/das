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
import com.findupon.commons.netops.entity.HttpStatusCode;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.ConsoleColors;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;


public class ChloeRedirectStrategy extends DefaultRedirectStrategy {
	public static final ChloeRedirectStrategy INSTANCE = new ChloeRedirectStrategy();
	private static final Logger logger = LoggerFactory.getLogger(ChloeRedirectStrategy.class);


	@Override
	public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
		Objects.requireNonNull(request);
		Objects.requireNonNull(response);

		String method = request.getRequestLine().getMethod();
		switch(response.getStatusLine().getStatusCode()) {
			case HttpStatusCode.SC_MOVED_TEMPORARILY:
				return isRedirectable(method) && response.getFirstHeader("location") != null;
			case HttpStatusCode.SC_MOVED_PERMANENTLY:
			case HttpStatusCode.SC_TEMPORARY_REDIRECT:
			case HttpStatusCode.SC_PERMANENT_REDIRECT:
				return isRedirectable(method);
			case HttpStatusCode.SC_SEE_OTHER:
				return true;
			default:
				return false;
		}
	}

	@Override
	public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
		URI locationUri = getUriFromLocation(request, response, context);
		String method = request.getRequestLine().getMethod();

		if(method.equalsIgnoreCase(HttpGet.METHOD_NAME)) {
			return new HttpGet(locationUri);
		} else {
			int status = response.getStatusLine().getStatusCode();
			if(status == HttpStatus.SC_TEMPORARY_REDIRECT) {
				return RequestBuilder.copy(request).setUri(locationUri).build();
			} else {
				return new HttpGet(locationUri);
			}
		}
	}

	private URI getUriFromLocation(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
		HttpClientContext clientContext = HttpClientContext.adapt(context);
		Header locationHeader = response.getFirstHeader("location");
		if(locationHeader == null) {
			throw new ProtocolException("Received redirect response [" + response.getStatusLine() + "] but no location header");
		}
		RequestConfig config = clientContext.getRequestConfig();
		String location = locationHeader.getValue();
		URI locationUri = null;
		URL requestUrl = null;
		try {
			requestUrl = new URL(request.getRequestLine().getUri());
		} catch(MalformedURLException e) {
			logger.debug("{}[ChloeRedirectStrategy] - Malformed request URL [{}]",
					ContextOps.nodePre(context), request.getRequestLine().getUri());
		}
		if(requestUrl != null) {
			URL locationUrl = createUrlFromLocation(requestUrl, location, context);
			if(locationUrl != null) {
				try {
					locationUri = locationUrl.toURI();
				} catch(URISyntaxException e) {
					logger.debug("{}[ChloeRedirectStrategy] - Redirect URI syntax exception [{}]",
							ContextOps.nodePre(context), locationUrl.toExternalForm());
				}
			}
		}
		if(locationUri == null) {
			locationUri = createLocationURI(location);
		}
		try {
			if(!locationUri.isAbsolute()) {
				if(!config.isRelativeRedirectsAllowed()) {
					throw new ProtocolException("Relative redirect location [" + locationUri + "] not allowed");
				}
				// adjust location URI
				HttpHost target = clientContext.getTargetHost();
				if(target == null) {
					throw new URISyntaxException(locationUri.toString(), "Missing target");
				}
				URI requestURI = new URI(request.getRequestLine().getUri());
				URI absoluteRequestURI = URIUtils.rewriteURI(requestURI, target, false);
				locationUri = URIUtils.resolve(absoluteRequestURI, locationUri);
			}
		} catch(URISyntaxException ex) {
			throw new ProtocolException(ex.getMessage(), ex);
		}
		RedirectLocations redirectLocations = (RedirectLocations)clientContext.getAttribute(HttpClientContext.REDIRECT_LOCATIONS);
		if(redirectLocations == null) {
			redirectLocations = new RedirectLocations();
			context.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, redirectLocations);
		}
		if(!config.isCircularRedirectsAllowed()) {
			if(redirectLocations.contains(locationUri)) {
				throw new CircularRedirectException("Circular redirect to [" + locationUri + "]");
			}
		}
		redirectLocations.add(locationUri);
		return locationUri;
	}

	private URL createUrlFromLocation(URL url, String location, HttpContext context) {
		String redirectUrlStr;
		if(StringUtils.isBlank(location)) {
			logger.debug(ConsoleColors.yellow("{}[ChloeRedirectStrategy] - Missing location from response header upon redirect! From URL: [{}]"),
					ContextOps.nodePre(context), url.toString());
			return null;
		}
		location = ScoutServices.encodeSpacing(location.trim());
		if(location.startsWith("//www")) {
			redirectUrlStr = url.getProtocol() + ":" + location;
		} else if(location.startsWith("/www")) {
			redirectUrlStr = url.getProtocol() + ":/" + location;
		} else if(location.startsWith("www")) {
			redirectUrlStr = url.getProtocol() + "://" + location;
		} else if(location.startsWith("/")) {
			redirectUrlStr = url.getProtocol() + "://" + url.getHost() + "/" + location.substring(1);
		} else if(location.startsWith("http")) {
			redirectUrlStr = location;
		} else {
			logger.debug(ConsoleColors.yellow("{}[ChloeRedirectStrategy] - Malformed location from response header upon redirect! From URL: [{}] Location: [{}]"),
					ContextOps.nodePre(context), url.toString(), location);
			return null;
		}
		if(url.toString().equalsIgnoreCase(redirectUrlStr)) {
			logger.debug(ConsoleColors.yellow("{}[ChloeRedirectStrategy] - Site attempted to redirect to the same page: [{}] to [{}]"),
					ContextOps.nodePre(context), url.toString(), redirectUrlStr);
			return null;
		}
		URL redirectUrl;
		try {
			redirectUrl = new URL(redirectUrlStr);
		} catch(MalformedURLException e) {
			logger.debug(ConsoleColors.yellow("{}[ChloeRedirectStrategy] - Malformed parsed redirect URL! From URL: [{}] Parse URL: [{}]"),
					ContextOps.nodePre(context), url.toString(), redirectUrlStr);
			return null;
		}
		logger.debug("{}[ChloeRedirectStrategy] - Redirected from [{}] to [{}]", ContextOps.nodePre(context), url.toString(), redirectUrl.toString());
		return redirectUrl;
	}
}
