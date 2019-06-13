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

import org.apache.http.HttpStatus;


/**
 * Includes some codes {@link org.apache.http.HttpStatus} was missing and some helper methods
 */
public interface HttpStatusCode extends HttpStatus {
	int ELSE = -1;
	int SC_PERMANENT_REDIRECT = 308;
	int SC_TOO_MANY_REQUESTS = 429;
	int SC_UNAVAILABLE_FOR_LEGAL = 451;
	int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;
	int SC_CONNECTION_TIMEOUT = 522;
	int SC_TIMEOUT_OCCURRED = 522;


	static boolean isBackoffCode(int statusCode) {
		for(int backOffCode : StatusCodes.backOffCodes) {
			if(statusCode == backOffCode) {
				return true;
			}
		}
		return false;
	}

	static boolean isRedirectCode(int statusCode) {
		for(int redirectCode : StatusCodes.redirectCodes) {
			if(statusCode == redirectCode) {
				return true;
			}
		}
		return false;
	}

	static boolean isRetriableCode(int statusCode) {
		for(int retryCode : StatusCodes.retriableCodes) {
			if(statusCode == retryCode) {
				return true;
			}
		}
		return false;
	}

	static boolean isProxyIgnorable(int statusCode) {
		for(int ignorableCode : StatusCodes.ignorableProxyCodes) {
			if(statusCode == ignorableCode) {
				return true;
			}
		}
		return false;
	}

	static boolean isSuccessful(int statusCode) {
		return !isNotSuccessful(statusCode);
	}

	static boolean isNotSuccessful(int statusCode) {
		return statusCode > 299;
	}
}

class StatusCodes {
	static int[] backOffCodes = new int[]{
			HttpStatusCode.SC_TOO_MANY_REQUESTS,
			HttpStatusCode.SC_SERVICE_UNAVAILABLE,
			HttpStatusCode.SC_REQUEST_TIMEOUT,
			HttpStatusCode.SC_GATEWAY_TIMEOUT
	};
	static int[] redirectCodes = new int[]{
			HttpStatusCode.SC_MOVED_PERMANENTLY,
			HttpStatusCode.SC_MOVED_TEMPORARILY,
			HttpStatusCode.SC_TEMPORARY_REDIRECT,
			HttpStatusCode.SC_PERMANENT_REDIRECT,
			HttpStatusCode.SC_SEE_OTHER
	};
	static int[] retriableCodes = new int[]{
			HttpStatusCode.SC_TOO_MANY_REQUESTS,
			HttpStatusCode.SC_SERVICE_UNAVAILABLE,
			HttpStatusCode.SC_REQUEST_TIMEOUT,
			HttpStatusCode.SC_GATEWAY_TIMEOUT,
			HttpStatusCode.SC_FORBIDDEN,
			HttpStatusCode.SC_INTERNAL_SERVER_ERROR,
			HttpStatusCode.SC_CONNECTION_TIMEOUT,
			HttpStatusCode.SC_TIMEOUT_OCCURRED
	};
	static int[] ignorableProxyCodes = new int[]{
			HttpStatusCode.SC_REQUEST_TIMEOUT,
			HttpStatusCode.SC_BAD_GATEWAY,
			HttpStatusCode.SC_PROXY_AUTHENTICATION_REQUIRED,
			HttpStatusCode.SC_SERVICE_UNAVAILABLE
	};
}
