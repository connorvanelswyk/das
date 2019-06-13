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


public class AgentDecision {
	private RequestedAction action;
	private String message;
	private final int statusCode;

	public AgentDecision(RequestedAction action, int statusCode) {
		this.action = action;
		this.statusCode = statusCode;
	}

	public AgentDecision(RequestedAction action, String message, int statusCode) {
		this.action = action;
		this.message = message;
		this.statusCode = statusCode;
	}

	public RequestedAction getAction() {
		return action;
	}

	public void setAction(RequestedAction action) {
		this.action = action;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
