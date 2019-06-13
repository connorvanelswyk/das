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

import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.MessageType;
import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.commons.entity.datasource.DataSource;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;


public class MasterNodeObjects {
	public static final List<WorkerNode> waitingNodes = Collections.synchronizedList(new ArrayList<>());
	public static final List<WorkerNode> workingNodes = Collections.synchronizedList(new ArrayList<>());
	public static final List<SentRequest> sentRequests = Collections.synchronizedList(new ArrayList<>());

	// A queue of built ClusterTransmission's without a node ID
	public static final Deque<ClusterTransmission> workQueue = new ConcurrentLinkedDeque<>();
	public static final List<DataSource> runningDataSources = Collections.synchronizedList(new ArrayList<>());
	public static final Map<DataSource, LongAdder> failedOrders = Collections.synchronizedMap(new HashMap<>());
	private static final int MAX_FAILED_ORDERS = 32;

	public static volatile MasterNode master;

	public static final int SOCKET_CONNECT_TIMEOUT_MILLIS = 10_000;
	public static final int SOCKET_CONNECT_MAX_ATTEMPTS = 2;

	public static final int MAX_QUEUED_LISTING_DATA_SOURCES = 6;
	public static final int MAX_GENERIC_WORK_ORDERS_PER_NODE = 12;
	public static final int MAX_LISTING_WORK_ORDERS_PER_NODE = 4;


	public static int getMaxQueuedGenericDataSources() {
		int allNodes = getAllNodesSnapshot().size();
		allNodes = allNodes < 10 ? 10 : allNodes;
		allNodes *= MAX_GENERIC_WORK_ORDERS_PER_NODE;
		return (int)(allNodes * 1.5d);
	}

	public static List<WorkerNode> getWaitingNodesSnapshot() {
		return new ArrayList<>(waitingNodes);
	}

	public static List<WorkerNode> getWorkingNodesSnapshot() {
		return new ArrayList<>(workingNodes);
	}

	public static List<WorkerNode> getAllNodesSnapshot() {
		List<WorkerNode> allNodes = getWaitingNodesSnapshot();
		allNodes.addAll(getWorkingNodesSnapshot());
		return allNodes;
	}

	public static List<SentRequest> getSentRequestsSnapshot() {
		return new ArrayList<>(sentRequests);
	}

	public static List<DataSource> getRunningDataSourcesSnapshot() {
		return new ArrayList<>(runningDataSources);
	}

	public static Queue<ClusterTransmission> getWorkQueueSnapshot() {
		return new ConcurrentLinkedQueue<>(workQueue);
	}

	public static List<SentRequest> getSentWorkRequestsSnapshot() {
		return getSentRequestsSnapshot().stream()
				.filter(r -> r.getTransmission().getMessage().getType().equals(MessageType.WORK_ORDER))
				.collect(Collectors.toList());
	}

	public static List<SentRequest> getSentWorkRequestsSnapshotForNode(WorkerNode node) {
		return getSentRequestsSnapshot().stream()
				.filter(r -> r.getTransmission().getMessage().getType().equals(MessageType.WORK_ORDER))
				.filter(r -> node.equals(r.getNode()))
				.collect(Collectors.toList());
	}

	public static List<SentRequest> getSentWorkRequestsSnapshot(com.findupon.commons.entity.datasource.DataSource dataSource, MasterMessage message) {
		return getSentRequestsSnapshot().stream()
				.filter(r -> r.getTransmission().getDataSource() != null
						&& dataSource.equals(r.getTransmission().getDataSource()))
				.filter(r -> message.equals(r.getTransmission().getMessage()))
				.collect(Collectors.toList());
	}

	public static List<SentRequest> getSentRequestsSnapshot(Long nodeId) {
		return getSentRequestsSnapshot().stream()
				.filter(r -> r.getNode().getId().equals(nodeId))
				.collect(Collectors.toList());
	}

	public static SentRequest getSentRequest(ClusterTransmission transmission) {
		List<SentRequest> existingRequests = getSentRequestsSnapshot().stream()
				.filter(r -> transmission.equals(r.getTransmission()))
				.collect(Collectors.toList());
		if(existingRequests.isEmpty()) {
			return null;
		}
		if(existingRequests.size() > 1) {
			throw new IllegalStateException("More than one sent request with same transmission:\n" + transmission);
		}
		return existingRequests.get(0);
	}

	public static SentRequest getSentHandshakeRequest(Long id) {
		List<SentRequest> existingRequests = getSentRequestsSnapshot().stream()
				.filter(r -> r.getNode().getId().equals(id)
						&& r.getTransmission().getMessage().getType().equals(MasterMessage.HANDSHAKE.getType()))
				.collect(Collectors.toList());
		if(existingRequests.isEmpty()) {
			return null;
		}
		if(existingRequests.size() > 1) {
			throw new IllegalStateException("More than one sent request of the same type " + MasterMessage.HANDSHAKE.getType() + " node id " + id);
		}
		return existingRequests.get(0);
	}

	public static List<DataSource> getQueuedListingDataSourcesSnapshot(Long dataSourceId) {
		return getWorkQueueSnapshot().stream()
				.filter(t -> t.getDataSource() != null)
				.map(ClusterTransmission::getDataSource)
				.filter(d -> dataSourceId.equals(d.getId()))
				.filter(d -> !d.isGeneric())
				.collect(Collectors.toList());
	}

	public static List<DataSource> getQueuedGenericDataSourcesSnapshot() {
		return getWorkQueueSnapshot().stream()
				.filter(t -> t.getDataSource() != null)
				.map(ClusterTransmission::getDataSource)
				.filter(com.findupon.commons.entity.datasource.DataSource::isGeneric)
				.collect(Collectors.toList());
	}

	public static void removeAllQueuedWorkOrders(com.findupon.commons.entity.datasource.DataSource dataSource) {
		synchronized(workQueue) {
			workQueue.removeIf(t -> dataSource.equals(t.getDataSource()));
		}
	}

	public static boolean hasDataSourceFailedMoreThanAllowed(com.findupon.commons.entity.datasource.DataSource dataSource) {
		return failedOrders.get(dataSource) != null && failedOrders.get(dataSource).intValue() >= MAX_FAILED_ORDERS;
	}
}
