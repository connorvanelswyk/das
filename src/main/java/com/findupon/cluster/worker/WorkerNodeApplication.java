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

package com.findupon.cluster.worker;

import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.MessageType;
import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.cluster.entity.master.MasterNode;
import com.findupon.cluster.entity.master.MasterNodeObjects;
import com.findupon.cluster.entity.worker.NodeConnectionStatus;
import com.findupon.cluster.entity.worker.NodeMessage;
import com.findupon.cluster.entity.worker.TimedThread;
import com.findupon.cluster.entity.worker.WorkerNode;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.repository.MasterNodeRepo;
import com.findupon.repository.WorkerNodeRepo;
import com.findupon.utilities.MemoryUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static com.findupon.cluster.entity.master.MasterNodeObjects.SOCKET_CONNECT_MAX_ATTEMPTS;
import static com.findupon.cluster.entity.master.MasterNodeObjects.SOCKET_CONNECT_TIMEOUT_MILLIS;
import static com.findupon.commons.utilities.ConsoleColors.purple;
import static com.findupon.commons.utilities.ConsoleColors.red;


@Component
public class WorkerNodeApplication {
	private static final Logger logger = LoggerFactory.getLogger(WorkerNodeApplication.class);
	private static final Map<DataSource, TimedThread> workerThreadMap = Collections.synchronizedMap(new HashMap<>());
	private static final AtomicBoolean nodeShutdown = new AtomicBoolean();
	private static final long genericThreadTimeout = TimeUnit.HOURS.toMillis(8);
	private static final long listingThreadTimeout = TimeUnit.HOURS.toMillis(4);
	private static final Object transitionMutex = new Object();
	private static volatile MasterNode master;
	private static volatile WorkerNode node;

	@Value("${production}") private Boolean production;

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private WorkerNodeRepo workerNodeRepo;
	@Autowired private MasterNodeRepo masterNodeRepo;

	private final MasterCommandListener masterCommandListener = new MasterCommandListener();
	private final MasterUpdateHandler masterUpdateHandler = new MasterUpdateHandler();
	private final NodeHousekeeper nodeHousekeeper = new NodeHousekeeper();


	public static void main(String... args) {
		logger.info("[WorkerNodeApplication] - Starting...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:worker-context.xml");
		WorkerNodeApplication instance = applicationContext.getBean(WorkerNodeApplication.class);
		instance.init();
	}

	private void init() {
		resetDatabaseIdsIfFirstToStart();
		node = WorkerNode.newIdleNode();
		workerNodeRepo.saveAndFlush(node);

		new Thread(masterCommandListener).start();
		new Thread(masterUpdateHandler).start();
		new Thread(nodeHousekeeper).start();

		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));

		logger.info(nodePre() + "Start completed.");
		logger.info("Current env: [{}] Current heap size: [{}] Maximum heap size: [{}] JVM bit size: [{}]",
				production ? "prod" : "dev", MemoryUtils.getCurrentHeapSizeStr(), MemoryUtils.getMaxHeapSizeStr(), System.getProperty("sun.arch.data.model"));
	}

	private void shutdownHook() {
		nodeShutdown.set(true);
		logger.info(nodePre() + "Shutdown sequence initiated");

		masterCommandListener.shutdown();
		masterUpdateHandler.shutdown();
		nodeHousekeeper.shutdown();
		interruptAllWorkerThreadsIfAny();

		jdbcTemplate.update("delete from worker_node where id = ?", node.getId());
		logger.info(nodePre() + "Shutdown sequence complete");
	}

	private void resetDatabaseIdsIfFirstToStart() {
		if(workerNodeRepo.count() == 0) {
			jdbcTemplate.update("alter table worker_node auto_increment = 1;");
		}
	}

	private class MasterCommandListener implements Runnable {
		private ServerSocket serverSocket;
		private final AtomicBoolean run = new AtomicBoolean(true);

		public synchronized void shutdown() {
			run.set(false);
			try {
				serverSocket.close();
			} catch(IOException e) {
				logger.warn("[MasterCommandListener] - Error closing response server socket", e);
			}
			logger.debug("[MasterCommandListener] - Shutdown complete");
		}

		@Override
		public void run() {
			logger.debug(nodePre() + "Starting command listener...");
			openServerSocket();
			logger.debug(nodePre() + "Command listener start completed. Listening at [{}:{}]",
					node.getAddress(), serverSocket.getLocalPort());

			while(run.get()) {
				try(Socket masterSocket = serverSocket.accept()) {
					logger.trace(nodePre() + "Client [{}:{}] opened socket for communication. Reading and processing response",
							masterSocket.getInetAddress(), masterSocket.getLocalPort());

					try(BufferedReader reader = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()))) {
						processRawTransmission(reader.readLine());

					} catch(IOException e) {
						logger.error(nodePre() + "Unable to process/handle command", e);
						ClusterTransmission errorResponse = new ClusterTransmission(node.getId());
						errorResponse.setMessage(NodeMessage.WORK_START_FAILURE);
						errorResponse.setDetails(ExceptionUtils.getRootCauseMessage(e));
						try {
							sendTransmission(errorResponse);
						} catch(IOException e1) {
							logger.error(nodePre() + "Could not communicate error response, we are properly fucked", e1);
						}
					}
				} catch(IOException e) {
					if(run.get()) {
						logger.error(nodePre() + "Server socket acceptance error", e);
					}
				}
			}
		}

		private void openServerSocket() {
			try {
				serverSocket = new ServerSocket(node.getPort());
			} catch(IOException e) {
				throw new RuntimeException("Cannot open server socket. Node: " + node.getId(), e);
			}
		}

		private void processRawTransmission(String rawTransmission) throws IOException {
			ClusterTransmission transmission = ClusterTransmission.fromEncryptedJsonString(rawTransmission, MasterMessage.class);
			if(transmission == null) {
				throw new IOException("Could not parse master transmission: " + rawTransmission);
			}
			logger.debug(nodePre() + "Command received from master [{}]", transmission.getMessage().getDirective());

			switch((MasterMessage)transmission.getMessage()) {
				case HANDSHAKE:
					sendTransmission(handshakeResponse(transmission));
					break;
				case LISTING_GATHER:
				case LISTING_BUILD:
				case GENERIC_GATHER_AND_BUILD:
					ClusterTransmission response = respondToWorkOrder(transmission);
					Thread workerThread = null;

					// put the worker thread in the map first (defines a working state) in case a handshake comes in
					if(NodeMessage.WORK_START_SUCCESS.equals(response.getMessage())) {
						workerThread = new Thread(new Worker(transmission));
						workerThreadMap.put(transmission.getDataSource(), new TimedThread(workerThread));
					}
					sendTransmission(response);

					// start the thread if valid after the response is sent
					if(workerThread != null) {
						try {
							// wait to not flood the NodeResponseListener with worst-case subsequent microsecond interval responses
							Thread.sleep(500);
						} catch(InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
						workerThread.start();
					}
					break;
				case SHUTDOWN:
					System.exit(0);
					break;
				default:
					logger.error(nodePre() + "You need to update the response processor - ClusterMessage received: {}",
							transmission.getMessage().getDirective(), new Exception());
					break;
			}
		}

		private ClusterTransmission handshakeResponse(ClusterTransmission incomingTransmission) {
			if(!workerThreadMap.isEmpty()) {
				incomingTransmission.setMessage(NodeMessage.HANDSHAKE_ALREADY_WORKING);
			} else {
				incomingTransmission.setMessage(NodeMessage.HANDSHAKE_SUCCESS);
			}
			if(invalidNodeId(incomingTransmission)) {
				incomingTransmission.setMessage(NodeMessage.HANDSHAKE_FAILURE);
				incomingTransmission.setDetails("Invalid node ID");
			}
			return incomingTransmission;
		}

		private ClusterTransmission respondToWorkOrder(ClusterTransmission transmission) {
			ClusterTransmission response = new ClusterTransmission(transmission.getNodeId(), transmission.getDataSource());
			LongAdder runningGenerics = new LongAdder();
			LongAdder runningListings = new LongAdder();

			synchronized(workerThreadMap) {
				workerThreadMap.forEach((k, v) -> {
					if(k.isGeneric()) {
						runningGenerics.increment();
					} else {
						runningListings.increment();
					}
				});
			}
			if(workerThreadMap.get(transmission.getDataSource()) != null) {
				logger.warn(nodePre() + "Same data source attempted to run more than once. Already running [{} - {}]", transmission.getDataSource().getUrl(), transmission.getDataSource().getId());
				response.setMessage(NodeMessage.WORK_REQUESTS_EXCEEDED);

			} else if(transmission.getDataSource().isGeneric() && runningGenerics.longValue() >= MasterNodeObjects.MAX_GENERIC_WORK_ORDERS_PER_NODE) {
				logger.warn(nodePre() + "Running generic data sources [{}] exceeds allowed work requests", runningGenerics.longValue());
				response.setMessage(NodeMessage.WORK_REQUESTS_EXCEEDED);

			} else if(!transmission.getDataSource().isGeneric() && runningListings.longValue() >= MasterNodeObjects.MAX_LISTING_WORK_ORDERS_PER_NODE) {
				logger.warn(nodePre() + "Running listing data sources [{}] exceeds allowed work requests", runningListings.longValue());
				response.setMessage(NodeMessage.WORK_REQUESTS_EXCEEDED);

			} else {
				String errorResponse = validateWorkOrder(transmission);
				if(errorResponse.isEmpty()) {
					response.setMessage(NodeMessage.WORK_START_SUCCESS);
				} else {
					logger.error(nodePre() + "Errors found in the work start order. Errors [{}] Raw transmission [{}]",
							errorResponse, transmission.toString());
					response.setMessage(NodeMessage.WORK_START_FAILURE);
					response.setDetails(errorResponse);
				}
			}
			return response;
		}

		private String validateWorkOrder(ClusterTransmission transmission) {
			String errorResponse = StringUtils.EMPTY;
			if(invalidNodeId(transmission)) {
				errorResponse += "Invalid node ID; ";
			}
			com.findupon.commons.entity.datasource.DataSource dataSource = transmission.getDataSource();
			if(dataSource == null) {
				errorResponse += "Missing data source; ";
			} else {
				if(transmission.getMessage().getType().equals(MessageType.WORK_ORDER)) {
					if(dataSource.getAssetType() == null) {
						errorResponse += "Missing asset type required for work order; ";
					}
					if(StringUtils.isEmpty(dataSource.getUrl())) {
						errorResponse += "Missing data source URL required for work order; ";
					}
					List<String> urlsToWork = transmission.getUrlsToWork();
					if(CollectionUtils.isEmpty(urlsToWork)) {
						errorResponse += "Missing URLs to work on transmission required for work order; ";
					} else {
						if(dataSource.isGeneric()) {
							if(urlsToWork.size() > 1) {
								errorResponse += "More than one URL received for generic data source work order; ";
							}
						} else {
							if(StringUtils.isEmpty(dataSource.getBotClass())) {
								errorResponse += "Missing bot class required to run listing data source; ";
							}
						}
					}
				}
			}
			return errorResponse;
		}

		private boolean invalidNodeId(ClusterTransmission incomingTransmission) {
			if(incomingTransmission.getNodeId() == null || !incomingTransmission.getNodeId().equals(node.getId())) {
				logger.error(nodePre() + "Received directive [{}] does not match current node id",
						incomingTransmission.getMessage().getDirective(), new UnsupportedOperationException());
				return true;
			}
			return false;
		}
	}

	private class Worker implements Runnable {
		private final ClusterTransmission transmission;

		Worker(ClusterTransmission transmission) {
			this.transmission = transmission;
		}


		@Override
		public void run() {
			try {
				logger.debug(nodePre() + "Worker thread launched for command [{}]", transmission.getMessage().getDirective());
				ClusterTransmission response = new ClusterTransmission(node.getId());

				com.findupon.commons.entity.datasource.DataSource dataSource = transmission.getDataSource();
				dataSource.setStatus(null);
				dataSource.setStatusReason(null);
				dataSource.setDetails(null);

				try {
					switch((MasterMessage)transmission.getMessage()) {
						case LISTING_GATHER:
						case LISTING_BUILD:
							initiateListingRun(); // no need to return the data source here, all updates are handled in ListingDataSourceRunner
							dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.SUCCESS); // not be persisted for listing, only used to set the response as success
							break;
						case GENERIC_GATHER_AND_BUILD:
							dataSource = initiateGenericRun(dataSource);
							break;
						default:
							throw new UnsupportedOperationException("No matching MasterMessage for work order");
					}
				} catch(Exception e) {
					logger.error(nodePre() + red("Fatal error while running [{}]"), transmission.getMessage().getDirective(), e);
					dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
					dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.EXCEPTION);
					dataSource.setDetails(ExceptionUtils.getStackTrace(e));
				}
				if(Thread.currentThread().isInterrupted()) {
					logger.debug(nodePre() + "Worker thread was interrupted");
					dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
					if(nodeShutdown.get()) {
						dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.SHUTDOWN);
					} else {
						dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.TIMEOUT);
					}
				}
				if(dataSource.getStatus() == null) {
					String error = "Null status after data source run";
					logger.error(nodePre() + error + " [{}]", dataSource.getUrl(), new IllegalStateException("Data source must have a status post-run"));
					dataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
					dataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.EXCEPTION);
					dataSource.setDetails(error);
				}
				switch(dataSource.getStatus()) {
					case SUCCESS:
						response.setMessage(NodeMessage.WORK_FINISH_SUCCESS);
						logger.debug(nodePre() + "Successfully finished running command [{}]", transmission.getMessage().getDirective());
						break;
					case FAILURE:
						response.setMessage(NodeMessage.WORK_FINISH_FAILURE);
						break;
					default:
						logger.error(nodePre() + "Invalid status (how did this happen?) after generic run for data source [{}]", dataSource.getUrl(),
								new IllegalStateException("Unknown data source status"));
				}
				response.setDataSource(dataSource);
				try {
					sendTransmission(response);
				} catch(Exception e) {
					logger.error(nodePre() + "Error sending response back to master!", e);
				}
			} finally {
				synchronized(workerThreadMap) {
					logger.debug(nodePre() + "Removing data source [{}] from worker thread map", transmission.getDataSource().getUrl());
					if(workerThreadMap.remove(transmission.getDataSource()) == null) {
						logger.error(nodePre() + "Data source was not removed from the worker map after run! ID: [{}] URL: [{}]",
								transmission.getDataSource().getId(), transmission.getDataSource().getUrl());
					}
				}
			}
		}

		private void initiateListingRun() throws Exception {
			if(transmission.getDataSource().isGeneric()) {
				throw new UnsupportedOperationException("Transmission DataSource is missing required bot class for: " +
						transmission.getMessage().getDirective());
			}
			com.findupon.commons.entity.datasource.DataSource listingDataSource = transmission.getDataSource();
			Class<?> clazz = Class.forName(listingDataSource.getBotClass());

			if(com.findupon.commons.bot.ListingBot.class.isAssignableFrom(clazz)) {
				Object bot = clazz.getConstructor().newInstance();
				com.findupon.commons.utilities.SpringUtils.autowire(bot);
				Method method;
				switch((MasterMessage)transmission.getMessage()) {
					case LISTING_GATHER:
						method = clazz.getDeclaredMethod("gatherProductUrls", List.class, long.class);
						break;
					case LISTING_BUILD:
						method = clazz.getMethod("buildAndPersist", List.class, long.class);
						break;
					default:
						throw new IllegalArgumentException("Non listing gather or build message for listing run. Node: " + node.getId());
				}
				method.setAccessible(true);
				method.invoke(bot, transmission.getUrlsToWork(), node.getId());
			} else {
				throw new IllegalAccessException("Bot class is not of type ListingAutomobileBot. Provided type:" +
						clazz.getCanonicalName() + " Node: " + node.getId());
			}
		}

		private com.findupon.commons.entity.datasource.DataSource initiateGenericRun(com.findupon.commons.entity.datasource.DataSource dataSource) {
			if(!dataSource.isGeneric()) {
				throw new UnsupportedOperationException("Transmission DataSource has a bot class for: " +
						transmission.getMessage().getDirective() + " Node: " + node.getId());
			}
			com.findupon.commons.searchparty.AbstractProductGatherer gatherer = new com.findupon.commons.searchparty.AutomotiveGatherer();
			com.findupon.commons.utilities.SpringUtils.autowire(gatherer);
			return gatherer.initiate(dataSource, node.getId());
		}
	}

	private synchronized void sendTransmission(ClusterTransmission transmission) throws IOException {
		if(master == null) {
			if(refreshMasterNodeCheckFailure()) {
				logger.warn(nodePre() + "Attempting to send response to nothing... master was probably shutdown");
				return;
			}
		}
		for(int attempt = 1; attempt <= SOCKET_CONNECT_MAX_ATTEMPTS; attempt++) {
			boolean success = true;
			try {
				internalSendTransmission(transmission);
			} catch(IOException e) {
				if(refreshMasterNodeCheckFailure()) {
					logger.warn(nodePre() + "Attempt [{}] to send response to nothing... master was probably shutdown", attempt);
					return;
				}
				success = false;
				if(attempt == SOCKET_CONNECT_MAX_ATTEMPTS) {
					logger.error(nodePre() + "Error sending transmission to master. Final attempt: [{}/{}] Transmission: [{}]",
							attempt, SOCKET_CONNECT_MAX_ATTEMPTS, transmission.toString(), e);
					throw e;
				} else {
					logger.warn(nodePre() + "Error sending transmission to master. Attempt: [{}/{}] Transmission: [{}]",
							attempt, SOCKET_CONNECT_MAX_ATTEMPTS, transmission.toString(), e);
					try {
						Thread.sleep(2000L);
					} catch(InterruptedException e1) {
						return;
					}
				}
			}
			if(success) {
				break;
			}
		}
	}

	private void internalSendTransmission(ClusterTransmission transmission) throws IOException {
		try(Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(master.getAddress(), master.getPort()), SOCKET_CONNECT_TIMEOUT_MILLIS);

			try(PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
				String responseLog = nodePre() + String.format("Sending transmission with directive [%s]", transmission.getMessage().getDirective());
				if(transmission.getMessage().isHandshake() && workerThreadMap.isEmpty()) {
					logger.info(responseLog);
				} else {
					logger.debug(responseLog);
				}
				writer.println(transmission.toEncryptedJsonString());
			}
		}
	}

	private class MasterUpdateHandler implements Runnable {
		private final AtomicLong updateIntervalMillis = new AtomicLong(8000L);
		private final AtomicBoolean run = new AtomicBoolean(true);

		public synchronized void shutdown() {
			run.set(false);
			logger.debug(nodePre() + "MasterUpdateHandler shutdown complete");
		}

		@Override
		public void run() {
			while(run.get()) {
				if(nodeShutdown.get()) {
					shutdown();
					continue;
				}
				logger.debug(nodePre() + "MasterUpdateHandler running...");

				if(refreshMasterNodeCheckFailure()) {
					updateIntervalMillis.set(8000L);
					logger.debug(nodePre() + "No master found; will check back again in [{}] seconds", updateIntervalMillis.get() / 1000);
					if(!NodeConnectionStatus.IDLE.equals(node.getConnectionStatus())) {
						if(workerThreadMap.isEmpty()) {
							node.setWorkDescription("Waiting on master to go back online");
						}
						transitionToIdle();
					}
				} else {
					updateIntervalMillis.set(60000L);
					if(master.getRunning() && NodeConnectionStatus.IDLE.equals(node.getConnectionStatus())) {
						transitionToStarted();
					} else {
						// check if master has updated this node as failure. if so, interrupt any worker threads
						try {
							Optional<WorkerNode> nodeOptional = workerNodeRepo.findById(node.getId());
							if(nodeOptional.isPresent()) {
								if(NodeConnectionStatus.FAILURE.equals(nodeOptional.get().getConnectionStatus())) {
									interruptAllWorkerThreadsIfAny();
								}
							} else {
								logger.error(nodePre() + "Self does not exist in the DB", new IllegalStateException("how did this even happen"));
							}
						} catch(Exception e) {
							logger.error(nodePre() + "Error finding self in the DB", e);
						}
					}
				}
				logger.debug(nodePre() + "MasterUpdateHandler run completed");
				try {
					Thread.sleep(updateIntervalMillis.get());
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.debug(nodePre() + "MasterUpdateHandler thread has been interrupted, shutting down thread");
					shutdown();
				}
			}
		}
	}

	private class NodeHousekeeper implements Runnable {
		private final AtomicInteger statCounter = new AtomicInteger(1);
		private final AtomicBoolean run = new AtomicBoolean(true);
		private final long updateInterval = 60000L;

		public synchronized void shutdown() {
			run.set(false);
			logger.debug(nodePre() + "NodeHousekeeper shutdown complete");
		}

		@Override
		public void run() {
			while(run.get()) {
				if(nodeShutdown.get()) {
					shutdown();
					continue;
				}
				logger.debug(nodePre() + "NodeHousekeeper running...");
				if(statCounter.getAndIncrement() % 10 == 0) {
					statCounter.set(1);
					long currentTime = System.currentTimeMillis();
					int running;
					StringBuffer buffer = new StringBuffer();
					synchronized(workerThreadMap) {
						running = workerThreadMap.size();
						workerThreadMap.forEach((k, v) -> buffer.append(k.getUrl()).append(": [")
								.append(TimeUnit.MILLISECONDS.toMinutes(currentTime - v.getStartTime()))
								.append(" minutes] "));
					}
					logger.debug(nodePre() + "Currently running working threads [{}]", running);
					if(running > 0) {
						logger.debug(nodePre() + buffer.toString());
					}
				}
				// check for timed out worker threads and interrupt if so
				logger.debug(nodePre() + "Checking for timed out worker threads...");
				long currentTime = System.currentTimeMillis();
				synchronized(workerThreadMap) {
					workerThreadMap.entrySet().stream()
							.filter(e -> currentTime - e.getValue().getStartTime() > (e.getKey().isGeneric() ? genericThreadTimeout : listingThreadTimeout))
							.forEach(e -> {
								logger.warn(nodePre() + "Thread for [{}] has timed out. Interrupting...", red(e.getKey().getUrl()));
								e.getValue().getThread().interrupt();
							});
				}
				try {
					Thread.sleep(updateInterval);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.debug(nodePre() + "NodeHousekeeper thread has been interrupted, shutting down thread");
					shutdown();
				}
			}
		}
	}

	private boolean refreshMasterNodeCheckFailure() {
		try {
			master = masterNodeRepo.findCurrentMaster();
			if(master != null) {
				return false;
			}
		} catch(Exception e) {
			logger.error(nodePre() + "Error finding current master in the DB", e);
		}
		return true;
	}

	private void interruptAllWorkerThreadsIfAny() {
		if(!workerThreadMap.isEmpty()) {
			logger.warn(nodePre() + "Interrupting currently running worker threads [{}]", workerThreadMap.size());
			synchronized(workerThreadMap) {
				workerThreadMap.entrySet().stream()
						.map(e -> e.getValue().getThread())
						.filter(t -> t.isInterrupted() && t.isAlive())
						.forEach(Thread::interrupt);
			}
		}
	}

	private void transitionToIdle() {
		synchronized(transitionMutex) {
			Optional<WorkerNode> nodeOptional;
			try {
				nodeOptional = workerNodeRepo.findById(node.getId());
			} catch(Exception e) {
				logger.error(nodePre() + "Error finding self in the DB, transition to idle fails", e);
				return;
			}
			if(nodeOptional.isPresent()) {
				node = nodeOptional.get();
				node.setConnectionStatus(NodeConnectionStatus.IDLE);
				workerNodeRepo.saveAndFlush(node);
				logger.info(nodePre() + "Transitioned to idle");
			} else {
				logger.error(nodePre() + "Could not find self in the DB", new Exception("Shutdown was probably initiated during transition"));
			}
		}
	}

	private synchronized void transitionToStarted() {
		synchronized(transitionMutex) {
			if(workerThreadMap.isEmpty()) {
				node.setWorkDescription(null);
			}
			node.setConnectionStatus(NodeConnectionStatus.STARTED);
			workerNodeRepo.saveAndFlush(node);
			logger.info(nodePre() + "Transitioned to started");
		}
	}

	private static String nodePre() {
		return "[" + purple("Node " + (node.getId() < 10 ? "0" : "") + node.getId()) + "] - ";
	}
}
