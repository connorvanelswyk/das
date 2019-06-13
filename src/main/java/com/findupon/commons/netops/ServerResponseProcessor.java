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

import com.findupon.commons.netops.entity.AgentDecision;
import com.findupon.commons.netops.entity.ProxyState;
import com.findupon.commons.netops.entity.RequestedAction;
import com.findupon.commons.netops.entity.ServerResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.StatusLine;

import static com.findupon.commons.netops.entity.HttpStatusCode.*;


public final class ServerResponseProcessor {

	static ServerResponse getProxyResponse(StatusLine statusLine) {
		RequestedAction action;
		ProxyState proxyState;
		String message;

		switch(statusLine.getStatusCode()) {
			case SC_REQUEST_TIMEOUT:
				message = "Request timeout";
				proxyState = ProxyState.SUCCESS;
				action = RequestedAction.PROCEED;
				break;
			case SC_PROXY_AUTHENTICATION_REQUIRED:
				message = "Your IP is not authorized or your Basic authorization header has an incorrect format, missing username, or bad password";
				proxyState = ProxyState.FATAL;
				action = RequestedAction.ABORT_CRAWL;
				break;
			case SC_PAYMENT_REQUIRED:
				message = "Account error or you are not authorized on the particular proxy server";
				proxyState = ProxyState.FATAL;
				action = RequestedAction.ABORT_CRAWL;
				break;
			case SC_FORBIDDEN:
				message = "The remote site has been blacklisted and can only be accessed through the open proxy server";
				proxyState = ProxyState.OPEN_PROXY_NEEDED;
				action = RequestedAction.ABORT_CRAWL;
				break;
			case SC_INTERNAL_SERVER_ERROR:
				message = "Internal server error - this is not good if it's really coming from the proxy";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				action = RequestedAction.PROCEED;
				break;
			case SC_SERVICE_UNAVAILABLE:
				if(StringUtils.containsIgnoreCase(statusLine.getReasonPhrase(), "too many errors")) {
					message = "Your requests have generated more than 60 response errors (with a status code of 400 or greater) over the past 30 seconds. " +
							"You need to fix what you're doing to stop producing so many errors.";
					proxyState = ProxyState.FATAL;
					action = RequestedAction.ABORT_CRAWL;
				} else {
					message = "Service unavailable - refer to reason for details, this should be temporary";
					proxyState = ProxyState.IP_SWITCH_NEEDED;
					action = RequestedAction.PROCEED;
				}
				break;
			case SC_BANDWIDTH_LIMIT_EXCEEDED:
				message = "You have exceeded your bandwidth limit. This response will continue until your next bill has been processed, or you raise the limit";
				proxyState = ProxyState.FATAL;
				action = RequestedAction.ABORT_CRAWL;
				break;
			default:
				message = "Non-documented status code";
				proxyState = ProxyState.SUCCESS;
				action = RequestedAction.PROCEED;
				break;
		}
		return new ServerResponse(proxyState, new AgentDecision(action, message, statusLine.getStatusCode()));
	}

	static ServerResponse getRemoteResponse(StatusLine statusLine) {
		RequestedAction action = RequestedAction.PROCEED;
		ProxyState proxyState = ProxyState.SUCCESS;
		String message;

		switch(statusLine.getStatusCode()) {
			case SC_BAD_REQUEST:
				message = "Bad request";
				break;
			case SC_UNAUTHORIZED:
				message = "Unauthorized";
				break;
			case SC_FORBIDDEN:
				message = "Forbidden";
				break;
			case SC_NOT_FOUND:
				message = "Not Found";
				break;
			case SC_NOT_ACCEPTABLE:
				message = "Not Acceptable - server probably did not accept our user agent or other headers";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				break;
			case SC_REQUEST_TIMEOUT:
				message = "Request Timeout - the server wants to shut down this connection";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				break;
			case SC_TOO_MANY_REQUESTS:
				message = "Too Many Requests! Too many requests have been sent in X amount of time";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				break;
			case SC_UNAVAILABLE_FOR_LEGAL:
				message = "Unavailable For Legal Reasons - we requested an illegal resource, such as a web page censored by a government";
				action = RequestedAction.ABORT_CRAWL;
				break;
			case SC_INTERNAL_SERVER_ERROR:
				message = "Internal Server Error";
				break;
			case SC_BAD_GATEWAY:
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				message = "Bad Gateway";
				break;
			case SC_SERVICE_UNAVAILABLE:
				message = "Fuck";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				break;
			case SC_GATEWAY_TIMEOUT:
				message = "Gateway Timeout";
				proxyState = ProxyState.IP_SWITCH_NEEDED;
				break;
			default:
				switch(String.valueOf(statusLine.getStatusCode()).charAt(0)) {
					case '3':
						message = "Non-handled redirection code";
						break;
					case '4':
						message = "Non-handled client error";
						break;
					case '5':
						message = "Non-handled server error";
						break;
					default:
						message = "Something strange...";
						break;
				}
		}
		return new ServerResponse(proxyState, new AgentDecision(action, message, statusLine.getStatusCode()));
	}
}
