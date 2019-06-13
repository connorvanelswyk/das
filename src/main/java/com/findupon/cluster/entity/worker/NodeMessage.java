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

package com.findupon.cluster.entity.worker;

import com.findupon.cluster.entity.ClusterMessage;
import com.findupon.cluster.entity.MessageType;


public enum NodeMessage implements ClusterMessage {

	HANDSHAKE_SUCCESS("HANDSHAKE_SUCCESS", 15000L, MessageType.HANDSHAKE),
	HANDSHAKE_FAILURE("HANDSHAKE_FAILURE", 15000L, MessageType.HANDSHAKE),
	HANDSHAKE_ALREADY_WORKING("HANDSHAKE_ALREADY_WORKING", 15000L, MessageType.HANDSHAKE),
	WORK_START_SUCCESS("WORK_START_SUCCESS", 15000L, MessageType.WORK_ORDER),
	WORK_START_FAILURE("WORK_START_FAILURE", 15000L, MessageType.WORK_ORDER),
	WORK_FINISH_SUCCESS("WORK_FINISH_SUCCESS", 15000L, MessageType.WORK_ORDER),
	WORK_FINISH_FAILURE("WORK_FINISH_FAILURE", 15000L, MessageType.WORK_ORDER),
	WORK_REQUESTS_EXCEEDED("WORK_REQUESTS_EXCEEDED", 15000L, MessageType.WORK_ORDER);

	private final String response;
	private final Long timeout;
	private final MessageType type;


	NodeMessage(String response, Long timeout, MessageType type) {
		this.response = response;
		this.timeout = timeout;
		this.type = type;
	}

	@Override
	public String getDirective() {
		return response;
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
