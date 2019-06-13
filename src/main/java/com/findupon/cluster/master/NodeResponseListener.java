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
import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.cluster.entity.worker.NodeConnectionStatus;
import com.findupon.cluster.entity.worker.NodeMessage;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.commons.utilities.DataSourceOperations;
import com.findupon.repository.WorkerNodeRepo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Service
public class NodeResponseListener implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(NodeResponseListener.class);
    private final ExecutorService responsePool = Executors.newFixedThreadPool(64);
    private ServerSocket serverSocket;
    private final AtomicBoolean run = new AtomicBoolean(true);

    @Autowired
    private DataSourceOperations dataSourceOperations;
    @Autowired
    private MasterNodeAgency masterNodeAgency;
    @Autowired
    private WorkerNodeRepo workerNodeRepo;


    public synchronized void shutdown() {
        run.set(false);
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("[NodeResponseListener] - Error closing response server socket from shutdown", e);
        }
    }

    @Override
    public void run() {
        try {
            logger.info("[NodeResponseListener] - Starting...");
            openServerSocket();
            logger.info("[NodeResponseListener] - Start completed.");

            while (run.get()) {
                Socket clientSocket = serverSocket.accept();
                responsePool.execute(new NodeResponseProcessor(clientSocket));
            }
        } catch (IOException e) {
            if (run.get()) {
                logger.warn("[NodeResponseListener] - Unable to process node response", e);
            } else {
                logger.debug("[NodeResponseListener] - Shutdown triggered [{}]", ExceptionUtils.getMessage(e));
            }
        }
    }

    private void openServerSocket() {
        try {
            serverSocket = new ServerSocket(master.getPort());
        } catch (IOException e) {
            throw new RuntimeException("[NodeResponseListener] - Could not open response listener server socket", e);
        }
    }


    private class NodeResponseProcessor implements Runnable {
        private final Socket socket;

        NodeResponseProcessor(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            logger.debug("[NodeResponseProcessor] - Client [{}:{}] opened socket for communication. " +
                    "Reading and processing response", socket.getInetAddress(), socket.getLocalPort());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String response = reader.readLine();

                if (StringUtils.equals(response, "su")) {
                    openCommandCenter(reader);
                } else {
                    processNodeResponse(response);
                }
            } catch (IOException e) {
                logger.warn("[NodeResponseProcessor] - Error processing response from [{}]", socket.getInetAddress(), e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("[NodeResponseProcessor] - Error closing response socket", e);
                }
            }
        }

        private void processNodeResponse(String response) {
            ClusterTransmission transmission = ClusterTransmission.fromEncryptedJsonString(response, NodeMessage.class);
            if (transmission == null) {
                logger.error("Could not parse node response. Raw response:\n {}", response);
                return;
            }
            SentRequest sentRequest = getSentRequest(transmission);

            if (sentRequest != null) {
                logger.debug("[Node {}] - Response received and matched to sent request [{}]", transmission.getNodeId(),
                        transmission.getMessage().getDirective());

                Optional<WorkerNode> nodeOptional = workerNodeRepo.findById(transmission.getNodeId());
                WorkerNode node;
                if (!nodeOptional.isPresent()) {
                    logger.warn("[Node {}] - Not present in DB from sent request directive [{}]. Removing sent request.",
                            sentRequest.getNode().getId(), sentRequest.getTransmission().getMessage().getDirective());
                    sentRequests.remove(sentRequest);
                    return;
                } else {
                    node = nodeOptional.get();
                }

                switch ((NodeMessage) transmission.getMessage()) {
                    case HANDSHAKE_SUCCESS: {
                        sentRequests.remove(sentRequest);
                        if (!waitingNodes.contains(node)) {
                            String p = StringUtils.EMPTY;
                            if (node.getConnectionStatus().equals(NodeConnectionStatus.FAILURE)) {
                                p = "Previously failed, re-";
                            }
                            node.setConnectionStatus(NodeConnectionStatus.SUCCESS);
                            masterNodeAgency.waitTransition(node, sentRequest.getTransmission());
                            String message = String.format("[Node %d] - %sconnected and joined the pool from %s:%d",
                                    node.getId(), p, node.getAddress(), node.getPort());
                            logger.info(message);
                        }
                        break;
                    }
                    case HANDSHAKE_FAILURE: {
                        sentRequests.remove(sentRequest);
                        masterNodeAgency.failureTransition(node, "Node responded with handshake failure");
                        break;
                    }
                    case HANDSHAKE_ALREADY_WORKING: {
                        sentRequests.remove(sentRequest);
                        break;
                    }
                    case WORK_START_SUCCESS: {
                        // keep the sent request open
                        masterNodeAgency.workTransition(node, sentRequest.getTransmission());
                        break;
                    }
                    case WORK_REQUESTS_EXCEEDED: {
                        sentRequests.remove(sentRequest);
                        logger.warn("Node [{}] - Allowed work requests exceeded; adding transmission back to the queue", node.getId());
                        sentRequest.getTransmission().setNodeId(null);
                        workQueue.offer(sentRequest.getTransmission());
                        break;
                    }
                    case WORK_FINISH_SUCCESS:
                    case WORK_FINISH_FAILURE: {
                        sentRequests.remove(sentRequest);
                        masterNodeAgency.waitTransition(node, sentRequest.getTransmission());
                        dataSourceOperations.handleDataSourceTransmissionResponse(transmission);
                        break;
                    }
                    case WORK_START_FAILURE: {
                        sentRequests.remove(sentRequest);
                        dataSourceOperations.handleDataSourceTransmissionResponse(transmission);
                        break;
                    }
                }
            } else {
                logger.warn("[Node {}] - Response not in sent requests. It could have been previously removed from a timeout status. Transmission details: [{}]",
                        transmission.getNodeId(), transmission.toString(), new IllegalStateException("Response not in sent requests"));
            }
        }

        private void openCommandCenter(BufferedReader reader) {
            try (PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                boolean authenticated = false;
                String response;

                writer.println("password: ");
                while ((response = reader.readLine()) != null && !response.equals("exit")) {
                    logger.info("[CommandCenter] - Telnet message [{}] from client [{}]", response,
                            String.format("[%s:%d]", socket.getInetAddress(), socket.getLocalPort()));

                    if (!authenticated) {
                        if (response.equals("riblet")) {
                            writer.println("\nWelcome to the cluster command center!\n"
                                    + "Available commands:\n" + "\n"
                                    + "> stats       (see stats of the application)\n"
                                    + "> nodes       (display info on all nodes in the db)\n"
                                    + "> shutdown    (shutdown all connected nodes and then master)\n"
                                    + "> exit        (close the telnet session)\n");
                            authenticated = true;
                            continue;
                        } else {
                            break;
                        }
                    }
                    switch (response) {
                        case "shutdown": {
                            List<WorkerNode> pooledNodes = getWaitingNodesSnapshot();
                            pooledNodes.addAll(getWorkingNodesSnapshot());

                            for (WorkerNode node : pooledNodes) {
                                masterNodeAgency.sendTransmission(node, new ClusterTransmission(
                                        node.getId(), MasterMessage.SHUTDOWN));
                            }
                            String shutdownResponse = String.format("%nShutdown command sent to [%d] node(s)%n", pooledNodes.size());
                            long dbDifference = workerNodeRepo.count() - pooledNodes.size();
                            if (dbDifference > 0) {
                                shutdownResponse += String.format("Warning! [%d] nodes found in DB were not " +
                                        "connected and did not receive shutdown command%n", dbDifference);
                            }
                            writer.println(shutdownResponse + "Shutdown sequence complete. Goodbye\n");
                            System.exit(0);
                            break;
                        }
                        case "stats": {
                            String stats = String.format("%n" +
                                            "Master address:           %s:%d%n" +
                                            "Database node size:       %d%n" +
                                            "Idle node pool size:      %d%n" +
                                            "Working node pool size:   %d%n" +
                                            "Sent requests out:        %d%n"
                                    , master.getAddress(), master.getPort()
                                    , workerNodeRepo.count()
                                    , waitingNodes.size()
                                    , workingNodes.size()
                                    , sentRequests.size());

                            writer.println(stats);
                            break;
                        }
                        case "nodes": {
                            List<WorkerNode> dbNodes = workerNodeRepo.findAll();
                            if (dbNodes.isEmpty()) {
                                writer.println("\nNo nodes found in the database!\n");
                            } else {
                                StringBuilder stringBuilder = new StringBuilder("\n");
                                dbNodes.forEach(workerNode -> stringBuilder.append(workerNode.toString()).append("\n"));
                                writer.println(stringBuilder.toString());
                            }
                            break;
                        }
                        default: {
                            writer.println("Invalid command!");
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("[CommandCenter] - Telnet session error", e);
            }
        }
    }
}
