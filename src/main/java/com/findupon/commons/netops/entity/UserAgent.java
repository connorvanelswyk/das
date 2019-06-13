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

import java.io.Serializable;
import java.util.Objects;


public final class UserAgent implements Serializable {
	private static final long serialVersionUID = -473481653074458482L;

	private static final UserAgent publicAgent = new UserAgent("Mozilla/5.0 (compatible; FindUponBot/1.0; +https://findupon.com/bot)", "FindUponBot");
	private String agent;
	private String description;

	public static UserAgent createNew(String agent, String description) {
		return new UserAgent(agent, description);
	}

	public static UserAgent getPublic() {
		return publicAgent;
	}

	private UserAgent(String agent, String description) {
		this.agent = agent;
		this.description = description;
	}

	@Override
	public String toString() {
		return description;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		UserAgent userAgent = (UserAgent)o;
		return Objects.equals(agent, userAgent.agent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(agent, description);
	}

	public String getAgent() {
		return agent;
	}

	public void setAgent(String agent) {
		this.agent = agent;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
