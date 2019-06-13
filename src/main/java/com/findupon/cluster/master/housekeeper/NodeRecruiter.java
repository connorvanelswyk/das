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

import static com.findupon.cluster.entity.master.MasterNodeObjects.getSentRequestsSnapshot;


@Service
public class NodeRecruiter extends NodeHousekeeper {

	@Autowired private MasterNodeAgency masterNodeAgency;
	@Autowired private WorkerNodeRepo workerNodeRepo;

	@Override
	public void run() {
		while(run.get()) {
			if(!sleep()) {
				return;
			}
			logger.debug("[NodeHousekeeper] - Checking the database for new nodes...");
			for(WorkerNode potentialNode : workerNodeRepo.findAllAwaitingConnection()) {
				if(!run.get()) {
					return;
				}
				if(getSentRequestsSnapshot(potentialNode.getId()).isEmpty()) {
					logger.debug("[Node {}] - Found waiting at [{}:{}]", potentialNode.getId(), potentialNode.getAddress(), potentialNode.getPort());
					logger.trace("[Node {}] - Attempting handshake...", potentialNode.getId());
					masterNodeAgency.performHandshake(potentialNode);
				}
			}
		}
	}

	@Override
	protected long getTimeoutInterval() {
		return 1000 * 20;
	}
}
