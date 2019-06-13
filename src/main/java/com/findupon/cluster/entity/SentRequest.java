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

package com.findupon.cluster.entity;

import com.findupon.cluster.entity.worker.WorkerNode;

import java.util.Date;


public class SentRequest {

	private WorkerNode node;
	private Date sentTime;
	private ClusterTransmission transmission;


	public SentRequest(WorkerNode node, Date sentTime, ClusterTransmission transmission) {
		this.node = node;
		this.sentTime = sentTime;
		this.transmission = transmission;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		SentRequest that = (SentRequest)o;
		return node.equals(that.node) && sentTime.equals(that.sentTime) && transmission.equals(that.transmission);
	}

	@Override
	public int hashCode() {
		int result = node.hashCode();
		result = 31 * result + sentTime.hashCode();
		result = 31 * result + transmission.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "SentRequest{" +
				"node=" + node +
				", sentTime=" + sentTime +
				", transmission=" + transmission +
				'}';
	}

	public WorkerNode getNode() {
		return node;
	}

	public void setNode(WorkerNode node) {
		this.node = node;
	}

	public Date getSentTime() {
		return sentTime;
	}

	public void setSentTime(Date sentTime) {
		this.sentTime = sentTime;
	}

	public ClusterTransmission getTransmission() {
		return transmission;
	}

	public void setTransmission(ClusterTransmission transmission) {
		this.transmission = transmission;
	}
}
