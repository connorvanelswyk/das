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

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


public class ChloeHttpRequestRetryHandler implements HttpRequestRetryHandler {
	public static final ChloeHttpRequestRetryHandler INSTANCE = new ChloeHttpRequestRetryHandler();

	/**
	 * Whether or not methods that have successfully sent their request will be retried
	 */
	private final boolean requestSentRetryEnabled = false;
	private final int maxRetryCount = 3;
	private final Set<Class<? extends IOException>> nonRetryExceptions = new HashSet<>(Arrays.asList(
			InterruptedIOException.class,
			UnknownHostException.class,
			ConnectException.class,
			SSLException.class
	));


	@Override
	public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
		Objects.requireNonNull(exception);
		Objects.requireNonNull(context);

		if(executionCount > this.maxRetryCount) {
			return false;
		}
		if(this.nonRetryExceptions.contains(exception.getClass())) {
			return false;
		} else {
			for(final Class<? extends IOException> rejectException : this.nonRetryExceptions) {
				if(rejectException.isInstance(exception)) {
					return false;
				}
			}
		}
		HttpClientContext clientContext = HttpClientContext.adapt(context);
		HttpRequest request = clientContext.getRequest();
		if(requestIsAborted(request)) {
			return false;
		}
		if(handleAsIdempotent(request)) {
			// retry if the request is considered idempotent
			return true;
		}
		if(!clientContext.isRequestSent() || this.requestSentRetryEnabled) {
			// retry if the request has not been sent fully or if it's ok to retry methods that have been sent
			return true;
		}
		// otherwise do not retry
		return false;
	}

	private boolean handleAsIdempotent(final HttpRequest request) {
		return !(request instanceof HttpEntityEnclosingRequest);
	}

	@SuppressWarnings("deprecation")
	private boolean requestIsAborted(final HttpRequest request) {
		HttpRequest req = request;
		if(request instanceof RequestWrapper) {
			// does not forward request to original
			req = ((RequestWrapper)request).getOriginal();
		}
		return (req instanceof HttpUriRequest && ((HttpUriRequest)req).isAborted());
	}
}
