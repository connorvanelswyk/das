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

import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.cluster.master.MasterNodeAgency;
import com.findupon.repository.WorkerNodeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Service
public class NodeAlivenessTester extends NodeHousekeeper {

	@Autowired private MasterNodeAgency masterNodeAgency;
	@Autowired private WorkerNodeRepo workerNodeRepo;

	@Override
	public void run() {
		while(run.get()) {
			if(!sleep()) {
				return;
			}
			if(!workingNodes.isEmpty() || !waitingNodes.isEmpty()) {
				logger.debug("[NodeHousekeeper] - Testing connected nodes for aliveness...");
				sendHandshakes(getAllNodesSnapshot());
			}
			if(!sleep()) {
				return;
			}
			List<WorkerNode> failureNodes = workerNodeRepo.findFailureNodes();
			if(!failureNodes.isEmpty()) {
				logger.debug("[NodeHousekeeper] - Attempting to re-connect with failure nodes...");
				sendHandshakes(failureNodes);
			}
		}
	}

	private void sendHandshakes(List<WorkerNode> nodes) {
		for(WorkerNode node : nodes) {
			if(!run.get()) {
				return;
			}
			boolean alreadySentHandshake = getSentHandshakeRequest(node.getId()) != null;
			if(!alreadySentHandshake) {
				masterNodeAgency.performHandshake(node);
			}
		}
	}

	@Override
	protected long getTimeoutInterval() {
		return 1000 * 60 * 5;
	}
}
