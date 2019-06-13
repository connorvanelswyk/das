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

import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.master.MasterNodeAgency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.findupon.cluster.entity.master.MasterNodeObjects.sentRequests;


@Service
public class TimedOutSentRequestHandler extends NodeHousekeeper {

	@Autowired private MasterNodeAgency masterNodeAgency;

	@Override
	public void run() {
		while(run.get()) {
			if(!sleep()) {
				return;
			}
			logger.debug("[NodeHousekeeper] - Checking for timed-out sent requests...");
			int successfullyRemoved = 0, failedToRemove = 0;
			List<SentRequest> requestsToRemove = new ArrayList<>();
			synchronized(sentRequests) {
				for(SentRequest sentRequest : sentRequests) {
					if(hasSentRequestTimedOut(sentRequest)) {
						requestsToRemove.add(sentRequest);
					}
				}
				for(SentRequest sentRequest : requestsToRemove) {
					if(!sentRequests.remove(sentRequest)) {
						logger.warn("[NodeHousekeeper] - Sent request failed to remove from timeout, the request was missing [{}]", sentRequest.toString());
						failedToRemove++;
					} else {
						logger.debug("[NodeHousekeeper] - Sent request removed successfully from timeout [{}]", sentRequest.toString());
						successfullyRemoved++;
					}
				}
			}
			requestsToRemove.forEach(masterNodeAgency::handleTimedOutSentRequest);
			logger.debug("[NodeHousekeeper] - Timed-out sent request process completed. Successfully removed: [{}] Failed to remove: [{}]", successfullyRemoved, failedToRemove);
		}
	}

	private boolean hasSentRequestTimedOut(SentRequest sentRequest) {
		long timeout = sentRequest.getTransmission().getMessage().getTimeout();
		return sentRequest.getSentTime().before(new Date(new Date().getTime() - timeout));
	}

	@Override
	protected long getTimeoutInterval() {
		return 1000 * 80;
	}
}
