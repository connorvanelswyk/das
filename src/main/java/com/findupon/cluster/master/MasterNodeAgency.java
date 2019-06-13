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
import com.findupon.cluster.entity.MessageType;
import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.cluster.entity.worker.NodeConnectionStatus;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.repository.WorkerNodeRepo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Service
public class MasterNodeAgency {
	private static final Logger logger = LoggerFactory.getLogger(MasterNodeAgency.class);

	@Autowired private com.findupon.commons.utilities.DataSourceOperations dataSourceOperations;
	@Autowired private WorkerNodeRepo workerNodeRepo;

	private final Object mutex = new Object();


	/**
	 * If the node is still working on other sources, keep it in the working pool and update its description.
	 * Otherwise, transition its state and move to the wait pool.
	 */
	void waitTransition(WorkerNode node, ClusterTransmission transmission) {
		synchronized(mutex) {
			boolean stillWorking = !getSentWorkRequestsSnapshotForNode(node).isEmpty();
			if(stillWorking) {
				logger.trace("[Node {}] - Still working, updating work description for wait transition...", node.getId());
			} else {
				logger.trace("[Node {}] - Transitioning to waiting...", node.getId());
			}
			if(waitingNodes.contains(node)) {
				logger.error("[Node {}] - Already in the waiting pool, transition to waiting fails!", node.getId(),
						new UnsupportedOperationException("Wait transition failure"));
			} else {
				if(stillWorking) {
					if(transmission.getDataSource() != null) {
						List<String> workingSources = new ArrayList<>(Arrays.asList(node.getWorkDescription().split(" ~ ")));
						workingSources.remove(transmission.getDataSource().getUrl());
						node.setWorkDescription(workingSources.stream().collect(Collectors.joining(" ~ ")));
					}
				} else {
					node.setIsWorking(false);
					node.setWorkDescription(null);
					workingNodes.remove(node);
					waitingNodes.add(node);
				}
				workerNodeRepo.saveAndFlush(node);
				if(!stillWorking) {
					logger.debug("[Node {}] - Transitioned to waiting", node.getId());
				}
			}
		}
		logger.trace("[Node {}] - Waiting transition lock released", node.getId());
	}

	/**
	 * If the node is waiting, transition its state and move to the work pool.
	 * If the node is already working on another source, update its description and carry on.
	 */
	void workTransition(WorkerNode node, ClusterTransmission transmission) {
		synchronized(mutex) {
			boolean alreadyWorking = workingNodes.contains(node);
			if(alreadyWorking) {
				logger.trace("[Node {}] - Already working, updating work description for work transition...", node.getId());
			} else {
				logger.trace("[Node {}] - Transitioning to working...", node.getId());
			}
			if(MessageType.WORK_ORDER.equals(transmission.getMessage().getType())) {
				com.findupon.commons.entity.datasource.DataSource ds = transmission.getDataSource();
				if(ds == null || ds.getAssetType() == null || StringUtils.isEmpty(ds.getUrl())) {
					logger.error("[Node {}] - Invalid datasource on transmission, transition to working fails! [{}]",
							node.getId(), ds, new UnsupportedOperationException());
					return;
				} else {
					if(alreadyWorking && StringUtils.isNotEmpty(node.getWorkDescription())) {
						List<String> workingSources = new ArrayList<>(Arrays.asList(node.getWorkDescription().split(" ~ ")));
						workingSources.add(ds.getUrl());
						node.setWorkDescription(workingSources.stream().collect(Collectors.joining(" ~ ")));
					} else {
						node.setWorkDescription(ds.getUrl());
					}
				}
			}
			if(!alreadyWorking) {
				node.setIsWorking(true);
				if(waitingNodes.remove(node)) {
					workingNodes.add(node);
					workerNodeRepo.saveAndFlush(node);
					logger.debug("[Node {}] - Transitioned to working", node.getId());
				} else {
					logger.warn("[Node {}] - Transition to working failed! Not in waiting nodes", node.getId());
				}
			} else {
				workerNodeRepo.saveAndFlush(node);
			}
		}
		logger.trace("[Node {}] - Working transition lock released", node.getId());
	}

	void failureTransition(WorkerNode node, String reason) {
		if(!NodeConnectionStatus.FAILURE.equals(node.getConnectionStatus())) {
			node.setConnectionStatus(NodeConnectionStatus.FAILURE);
			node.setIsWorking(false);
			node.setWorkDescription(reason);

			logger.debug("[Node {}] - Transitioning to failure...", node.getId());
			synchronized(mutex) {
				waitingNodes.remove(node);
				workingNodes.remove(node);
				if(workerNodeRepo.existsById(node.getId())) {
					workerNodeRepo.saveAndFlush(node);
				}
			}
			logger.trace("[Node {}] - Failure transition lock released", node.getId());
			logger.error("[Node {}] - Transitioned to failure! Reason [{}] Address {}:{}",
					node.getId(), reason, node.getAddress(), node.getPort());
		} else {
			logger.warn("[Node {}] - Already in failure status at [{}:{}]", node.getId(), node.getAddress(), node.getPort());
		}
	}

	public void handleTimedOutSentRequest(SentRequest request) {
		logger.error("[Node {}] - Address [{}:{}] sent request [{}] has timed out (no response for more than {}). Work url(s): {}",
				request.getNode().getId(),
				request.getNode().getAddress(),
				request.getNode().getPort(),
				request.toString(),
				com.findupon.commons.utilities.TimeUtils.format(request.getTransmission().getMessage().getTimeout()),
				request.getTransmission().getUrlsToWork() == null ? "none" :
						request.getTransmission().getUrlsToWork().stream().collect(Collectors.joining(", ", "\n", "")));

		Optional<WorkerNode> nodeOptional = workerNodeRepo.findById(request.getNode().getId());
		if(nodeOptional.isPresent()) {
			WorkerNode node = nodeOptional.get();
			logger.warn("[Node {}] - Sent request timed out. Handling datasource transition accordingly", node.getId());
			String error = "Sent request timed out";

			ClusterTransmission transmission = request.getTransmission();

			if(MessageType.WORK_ORDER.equals(transmission.getMessage().getType())) {
				com.findupon.commons.entity.datasource.DataSource dataSource = transmission.getDataSource();

				if(dataSource.isGeneric()) {
					dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.TIMEOUT);
					dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
					dataSource.setDetails(error);
				}
				dataSourceOperations.handleDataSourceTransmissionResponse(transmission);
			} else if(!NodeConnectionStatus.FAILURE.equals(node.getConnectionStatus())) {
				// only transition to failure on non-work orders
				failureTransition(node, error);
			}
		} else {
			logger.warn("[Node {}] - Sent request timed out because it was not found in the database (probably shutdown) last address [{}:{}]",
					request.getNode().getId(), request.getNode().getAddress(), request.getNode().getPort());
		}
	}

	public void performHandshake(WorkerNode node) {
		ClusterTransmission handshake = new ClusterTransmission(node.getId(), MasterMessage.HANDSHAKE);
		try {
			sendTransmission(node, handshake);
		} catch(IOException e) {
			failureTransition(node, String.format("Handshake failure: %s", ExceptionUtils.getRootCauseMessage(e)));
		}
	}

	// TODO: make this concurrent by implementing a send pool similar to the response pool
	void sendTransmission(WorkerNode node, ClusterTransmission transmission) throws IOException {
		SentRequest sentRequest = new SentRequest(node, new Date(), transmission);
		if(!MessageType.NO_RESPONSE.equals(transmission.getMessage().getType())) {
			sentRequests.add(sentRequest);
		}
		for(int attempt = 1; attempt <= SOCKET_CONNECT_MAX_ATTEMPTS; attempt++) {
			boolean success = true;
			try {
				internalSendTransmission(sentRequest);
			} catch(IOException e) {
				if(!master.getRunning()) {
					return;
				}
				success = false;
				if(attempt == SOCKET_CONNECT_MAX_ATTEMPTS) {
					logger.error("Error sending transmission [{}] to node. Final attempt [{}/{}]. Removing sent request. Node [{} - {}:{}]",
							transmission.getMessage().getDirective(), attempt, SOCKET_CONNECT_MAX_ATTEMPTS, node.getId(), node.getAddress(), node.getPort(), e);
					sentRequests.remove(sentRequest);
					throw e;
				} else {
					logger.warn("Error sending transmission [{}] to node. Attempt [{}/{}]. Node [{} - {}:{}]",
							transmission.getMessage().getDirective(), attempt, SOCKET_CONNECT_MAX_ATTEMPTS, node.getId(), node.getAddress(), node.getPort(), e);
				}
			}
			if(success) {
				break;
			}
		}
	}

	private void internalSendTransmission(SentRequest sentRequest) throws IOException {
		try(Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(sentRequest.getNode().getAddress(),
					sentRequest.getNode().getPort()), SOCKET_CONNECT_TIMEOUT_MILLIS);

			try(PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
				logger.debug("[Node {}] - Sending transmission with directive [{}]",
						sentRequest.getNode().getId(), sentRequest.getTransmission().getMessage().getDirective());

				writer.println(sentRequest.getTransmission().toEncryptedJsonString());
				logger.trace("[Node {}] - Transmission sent", sentRequest.getNode().getId());

			} catch(UnsupportedOperationException e) {
				logger.error("[Node {}] - Error sending transmission (invalid transmission attempted)", sentRequest.getNode().getId(), e);
				sentRequests.remove(sentRequest);
				throw e;
			}
		}
	}
}
