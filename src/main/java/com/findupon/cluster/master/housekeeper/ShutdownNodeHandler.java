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

package com.findupon.cluster.master.housekeeper;

import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.MessageType;
import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.entity.master.MasterNodeObjects;
import com.findupon.cluster.entity.worker.NodeMessage;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.repository.WorkerNodeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Service
public class ShutdownNodeHandler extends NodeHousekeeper {

	@Autowired private com.findupon.commons.utilities.DataSourceOperations dataSourceOperations;
	@Autowired private WorkerNodeRepo workerNodeRepo;

	@Override
	public void run() {
		while(run.get()) {
			if(!sleep()) {
				return;
			}
			logger.debug("[NodeHousekeeper] - Cleaning up any shutdown nodes...");

			List<WorkerNode> nodesInDb = workerNodeRepo.findAll();
			int waitingPoolSizeBefore = waitingNodes.size();
			int workPoolSizeBefore = workingNodes.size();

			// remove every node in the waiting or working pool that doesn't exist in the db
			// if the dropped node was working and the listingDataSource is still running, put the work url back on the stack
			if(waitingPoolSizeBefore > 0) {
				boolean removed;
				synchronized(waitingNodes) {
					removed = waitingNodes.retainAll(nodesInDb);
				}
				if(removed) {
					logger.info("[NodeHousekeeper] - Removed [{}] shutdown nodes from the waiting pool", waitingPoolSizeBefore - waitingNodes.size());
				}
			}
			if(workPoolSizeBefore > 0) {
				boolean removed;

				List<WorkerNode> droppedNodes = getWorkingNodesSnapshot();
				synchronized(workingNodes) {
					removed = workingNodes.retainAll(nodesInDb);
				}
				if(removed) {
					droppedNodes.removeAll(getWorkingNodesSnapshot());

					// remove any sent requests if they exists. if it was working, handle its work order
					for(WorkerNode node : droppedNodes) {
						for(SentRequest request : MasterNodeObjects.getSentRequestsSnapshot(node.getId())) {
							if(!sentRequests.remove(request)) {
								logger.warn("[NodeHousekeeper] - Sent request failed to remove from shutdown, the request was missing [{}]", request.toString());
							} else {
								logger.debug("[NodeHousekeeper] - Sent request removed successfully from shutdown [{}]", request.toString());
							}
							ClusterTransmission transmission = request.getTransmission();

							if(MessageType.WORK_ORDER.equals(transmission.getMessage().getType())) {
								com.findupon.commons.entity.datasource.DataSource dataSource = transmission.getDataSource();

								if(dataSource.isGeneric()) {
									transmission.setMessage(NodeMessage.WORK_FINISH_FAILURE);
									dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.SHUTDOWN);
									dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
									dataSource.setDetails("Picked up from shutdown status from the node housekeeper");
								}
								dataSourceOperations.handleDataSourceTransmissionResponse(transmission);
							}
						}
					}
					logger.info("[NodeHousekeeper] - Removed [{}] shutdown nodes from the working pool", workPoolSizeBefore - workingNodes.size());
				}
			}
			logger.trace("[NodeHousekeeper] - Shutdown node cleanup complete");
		}
	}

	@Override
	protected long getTimeoutInterval() {
		return 1000 * 20;
	}
}
