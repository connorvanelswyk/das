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

import com.findupon.commons.netops.entity.UserAgent;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.stream.Stream;


final class UserAgentLoader {
	private static final String agentsPath = "/user-agents.txt";
	private static final UserAgent[] userAgents = loadUserAgents();

	/**
	 * @param currentUserAgent to ensure the same one is not used twice, null if new.
	 * @return the new user agent.
	 */
	static UserAgent getNewUserAgent(UserAgent currentUserAgent) {
		UserAgent userAgent = userAgents[new Random().nextInt(userAgents.length)];
		if(userAgent.equals(currentUserAgent)) {
			return getNewUserAgent(currentUserAgent);
		}
		return userAgent;
	}

	private static UserAgent[] loadUserAgents() {
		try(Stream<String> lines = Files.lines(Paths.get(UserAgentLoader.class.getResource(agentsPath).toURI()))) {
			return lines.filter(StringUtils::isNotBlank)
					.map(s -> {
						String description = StringUtils.substringAfter(s, "Mozilla/5.0 (");
						description = StringUtils.substringBefore(description, " ");
						if(!StringUtils.endsWith(description, ";")) {
							description += ";";
						}
						description = description + " " + StringUtils.substringAfterLast(s, ") ");
						if(StringUtils.length(description) > 44) {
							description = StringUtils.substring(description, 0, 44);
						}
						description = StringUtils.trim(description);
						return UserAgent.createNew(s, description);
					}).toArray(UserAgent[]::new);
		} catch(Exception e) {
			throw new RuntimeException("Could not load user agents");
		}
	}
}
