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

package com.findupon.cluster.master;

import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.repository.WorkerNodeRepo;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;
import static com.findupon.commons.utilities.ConsoleColors.blue;
import static com.findupon.commons.utilities.ConsoleColors.cyan;


@Component
public class MasterWorkDelegate implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(MasterWorkDelegate.class);
	private final AtomicBoolean run = new AtomicBoolean(true);
	private final int POLL_FREQUENCY = 8;
	private int statCounter = 0;

	private enum AllowedWorkType {AGNOSTIC, GENERIC, LISTING}

	@Autowired private MasterNodeAgency masterNodeAgency;
	@Autowired private WorkerNodeRepo workerNodeRepo;


	public synchronized void shutdown() {
		run.set(false);
	}

	@Override
	public void run() {
		while(run.get()) {
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_FREQUENCY));
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error("[MasterWorkDelegate] - Thread interrupt during sleep... this should not happen here, aborting", e);
				return;
			}
			if(run.get() && statCounter++ % POLL_FREQUENCY == 0) {
				printStats();
				statCounter = 1;
			}
			if(isValidDelegationState()) {
				List<DataSource> runningDataSources = getRunningDataSourcesSnapshot();
				boolean areListingDataSourcesRunning = runningDataSources.stream().anyMatch(d -> !d.isGeneric());
				boolean areGenericDataSourcesRunning = runningDataSources.stream().anyMatch(com.findupon.commons.entity.datasource.DataSource::isGeneric);

				if(!areGenericDataSourcesRunning && !areListingDataSourcesRunning) {
					logger.info("[MasterWorkDelegate] - No data sources are running");
					continue;
				}
				Map<WorkerNode, AllowedWorkType> readyNodes = new LinkedHashMap<>();
				getWaitingNodesSnapshot().forEach(n -> readyNodes.put(n, AllowedWorkType.AGNOSTIC));

				for(WorkerNode node : getWorkingNodesSnapshot()) {
					List<SentRequest> existingWorkRequests = getSentWorkRequestsSnapshotForNode(node);

					LongAdder sentListingRequests = new LongAdder();
					LongAdder sentGenericRequests = new LongAdder();

					existingWorkRequests.stream()
							.map(SentRequest::getTransmission)
							.filter(t -> t.getDataSource() != null)
							.map(ClusterTransmission::getDataSource)
							.forEach(d -> {
								if(d.isGeneric()) {
									sentGenericRequests.increment();
								} else {
									sentListingRequests.increment();
								}
							});
					boolean canTakeMoreListing = sentListingRequests.longValue() < MAX_LISTING_WORK_ORDERS_PER_NODE;
					boolean canTakeMoreGeneric = sentGenericRequests.longValue() < MAX_GENERIC_WORK_ORDERS_PER_NODE;

					if(canTakeMoreListing && canTakeMoreGeneric) {
						readyNodes.put(node, AllowedWorkType.AGNOSTIC);

					} else if(canTakeMoreListing && areListingDataSourcesRunning) {
						readyNodes.put(node, AllowedWorkType.LISTING);

					} else if(canTakeMoreGeneric && areGenericDataSourcesRunning) {
						readyNodes.put(node, AllowedWorkType.GENERIC);
					}
				}
				if(readyNodes.isEmpty()) {
					logger.debug("[MasterWorkDelegate] - No nodes available to accept work requests");
					continue;
				}
				sendWorkRequests(readyNodes);
			}
		}
	}

	/**
	 * Send requests to nodes based on their allowed work type.
	 * If a node is already working on a listing data source, it is not allowed another one.
	 * If the work queue polls a type that is not allowed, put it back on the front of the queue after a request is sent or no valid type is found.
	 */
	private void sendWorkRequests(Map<WorkerNode, AllowedWorkType> readyNodes) {
		for(Map.Entry<WorkerNode, AllowedWorkType> entry : readyNodes.entrySet()) {
			if(workQueue.isEmpty()) {
				logger.debug("[MasterWorkDelegate] - No requests to send, work queue is empty");
				break;
			}
			if(!run.get()) {
				break;
			}
			List<ClusterTransmission> transmissionsToPutBack = new ArrayList<>();
			AllowedWorkType allowedWorkType = entry.getValue();
			WorkerNode node = entry.getKey();
			ClusterTransmission nextWorkOrder;

			// go through the work queue looking for an order the node can accept
			while(true) {
				if((nextWorkOrder = workQueue.poll()) == null || !run.get()) {
					// no more work requests, put any polled back at the front and continue to the next node
					transmissionsToPutBack.forEach(workQueue::offerFirst);
					break;
				} else if(AllowedWorkType.AGNOSTIC.equals(allowedWorkType)
						|| (AllowedWorkType.GENERIC.equals(allowedWorkType) && nextWorkOrder.getDataSource().isGeneric())
						|| (AllowedWorkType.LISTING.equals(allowedWorkType) && !nextWorkOrder.getDataSource().isGeneric())) {

					boolean clearToSend = true;
					if(!nextWorkOrder.getDataSource().isGeneric()) {
						// make sure the node is not working on the same listing data source
						// poll sent requests again as its state may have changed
						for(SentRequest existingRequest : getSentWorkRequestsSnapshotForNode(node)) {
							if(nextWorkOrder.getDataSource().equals(existingRequest.getTransmission().getDataSource())) {
								transmissionsToPutBack.add(nextWorkOrder);
								clearToSend = false;
								break;
							}
						}
					}
					if(clearToSend) {
						// found an allowed work request, put any polled back at the front and send the request to the node
						transmissionsToPutBack.forEach(workQueue::offerFirst);
						break;
					}
				} else {
					// work request not allowed, save to put back later
					transmissionsToPutBack.add(nextWorkOrder);
				}
			}
			if(nextWorkOrder != null && run.get()) {
				nextWorkOrder.setNodeId(node.getId());
				try {
					masterNodeAgency.sendTransmission(node, nextWorkOrder);
				} catch(Exception e) {
					logger.warn("[Node {}] - Did not receive work order! Adding back to the work queue. Cause [{}]",
							node.getId(), ExceptionUtils.getRootCauseMessage(e));
					nextWorkOrder.setNodeId(null);
					workQueue.offerFirst(nextWorkOrder);
				}
			}
		}
	}

	private boolean isValidDelegationState() {
		if(!run.get() || !master.getRunning()) {
			return false;
		}
		if(waitingNodes.isEmpty() && workingNodes.isEmpty()) {
			logger.debug("[MasterWorkDelegate] - No nodes available");
			return false;
		}
		return true;
	}

	private void printStats() {
		logger.trace("[MasterWorkDelegate] - Polling work queues and sent requests for stats...");
		List<ClusterTransmission> workQueue = new ArrayList<>(getWorkQueueSnapshot());
		int listingQueue = 0, genericQueue = 0, listingRunning = 0, genericRunning = 0;
		for(ClusterTransmission t : workQueue) {
			com.findupon.commons.entity.datasource.DataSource d = t.getDataSource();
			if(d.isGeneric()) {
				genericQueue++;
			} else {
				listingQueue++;
			}
		}
		List<SentRequest> sentWork = getSentWorkRequestsSnapshot();
		for(SentRequest s : sentWork) {
			com.findupon.commons.entity.datasource.DataSource d = s.getTransmission().getDataSource();
			if(d != null) {
				if(d.isGeneric()) {
					genericRunning++;
				} else {
					listingRunning++;
				}
			}
		}
		logger.info("\n" +
						"IN PROGRESS \tTotal: [{}]\t Listing: [{}]\t Generic: [{}]\n" +
						"QUEUED      \tTotal: [{}]\t Listing: [{}]\t Generic: [{}]\n" +
						"NODE POOL   \tWork:  [{}]\t Wait:    [{}]\t DB:      [{}]\n",
				cyan(String.valueOf(sentWork.size())), cyan(String.valueOf(listingRunning)), cyan(String.valueOf(genericRunning)),
				cyan(String.valueOf(workQueue.size())), cyan(String.valueOf(listingQueue)), cyan(String.valueOf(genericQueue)),
				blue(String.valueOf(workingNodes.size())), blue(String.valueOf(waitingNodes.size())), blue(String.valueOf(workerNodeRepo.count())));
	}
}
