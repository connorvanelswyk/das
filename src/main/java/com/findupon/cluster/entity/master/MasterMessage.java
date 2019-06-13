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

package com.findupon.cluster.entity.master;

import com.findupon.cluster.entity.ClusterMessage;
import com.findupon.cluster.entity.MessageType;

import java.util.concurrent.TimeUnit;


public enum MasterMessage implements ClusterMessage {

	HANDSHAKE("HANDSHAKE", TimeUnit.SECONDS.toMillis(30), MessageType.HANDSHAKE),
	LISTING_GATHER("LISTING_GATHER", TimeUnit.HOURS.toMillis(24), MessageType.WORK_ORDER),
	LISTING_BUILD("LISTING_BUILD", TimeUnit.HOURS.toMillis(8), MessageType.WORK_ORDER),
	GENERIC_GATHER_AND_BUILD("GENERIC_GATHER_AND_BUILD", TimeUnit.HOURS.toMillis(12), MessageType.WORK_ORDER),
	SHUTDOWN("SHUTDOWN", null, MessageType.NO_RESPONSE);

	private final String command;
	private final Long timeout;
	private final MessageType type;


	MasterMessage(String command, Long timeout, MessageType type) {
		this.command = command;
		this.timeout = timeout;
		this.type = type;
	}

	@Override
	public String getDirective() {
		return command;
	}

	@Override
	public Long getTimeout() {
		return timeout;
	}

	@Override
	public MessageType getType() {
		return type;
	}
}
