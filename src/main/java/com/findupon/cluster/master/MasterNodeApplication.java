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
import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.cluster.entity.master.MasterNode;
import com.findupon.cluster.master.housekeeper.NodeAlivenessTester;
import com.findupon.cluster.master.housekeeper.NodeRecruiter;
import com.findupon.cluster.master.housekeeper.ShutdownNodeHandler;
import com.findupon.cluster.master.housekeeper.TimedOutSentRequestHandler;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.repository.MasterNodeRepo;
import com.findupon.utilities.MemoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Component
public class MasterNodeApplication {
	private static final Logger logger = LoggerFactory.getLogger(MasterNodeApplication.class);
	@Value("${production}") private Boolean production;

	@Autowired private com.findupon.commons.utilities.DataSourceOperations dataSourceOperations;
	@Autowired private com.findupon.commons.repository.datasource.DataSourceRepo dataSourceRepo;
	@Autowired private MasterNodeRepo masterNodeRepo;
	@Autowired private com.findupon.commons.utilities.SlackMessenger slackMessenger;
	@Autowired private JdbcTemplate jdbcTemplate;

	/* Cluster Management Threads */
	@Autowired private MasterWorkDelegate masterWorkDelegate;
	@Autowired private NodeResponseListener nodeResponseListener;
	@Autowired private TimedOutSentRequestHandler timedOutSentRequestHandler;
	@Autowired private NodeAlivenessTester nodeAlivenessTester;
	@Autowired private ShutdownNodeHandler shutdownNodeHandler;
	@Autowired private NodeRecruiter nodeRecruiter;

	private final ListingDatasourceScheduler listingDatasourceScheduler = new ListingDatasourceScheduler();
	private final GenericDatasourceScheduler genericDatasourceScheduler = new GenericDatasourceScheduler();


	public static void main(String... args) {
		logger.info("[MasterNodeApplication] - Starting...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:master-context.xml");
		MasterNodeApplication instance = applicationContext.getBean(MasterNodeApplication.class);
		instance.init();
	}

	private void init() {
		if(masterNodeRepo.count() > 0) {
			logger.error("[MasterNodeApplication] - Found existing master in the database. Start fails!",
					new UnsupportedOperationException("Only one master node is allowed"));
			return;
		}
		jdbcTemplate.update("alter table master_node auto_increment = 1;");
		master = MasterNode.getStarted();
		masterNodeRepo.saveAndFlush(master);

		// consider moving these to @Scheduled by configuring your own executor service bean
		new Thread(nodeResponseListener).start();
		new Thread(nodeRecruiter).start();
		new Thread(nodeAlivenessTester).start();
		new Thread(timedOutSentRequestHandler).start();
		new Thread(shutdownNodeHandler).start();
		new Thread(masterWorkDelegate).start();

		new Thread(listingDatasourceScheduler).start();
		new Thread(genericDatasourceScheduler).start();

		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));

		if(production) {
			slackMessenger.sendMessageWithArgs("*[MasterNodeApplication]* - Start completed. Listening at `%s %d`", master.getAddress(), master.getPort());
		}
		logger.info("Current env: [{}] Current heap size: [{}] Maximum heap size: [{}] JVM bit size: [{}]",
				production ? "prod" : "dev", MemoryUtils.getCurrentHeapSizeStr(), MemoryUtils.getMaxHeapSizeStr(), System.getProperty("sun.arch.data.model"));
	}

	private void shutdownHook() {
		master.setRunning(false);

		nodeResponseListener.shutdown();
		masterWorkDelegate.shutdown();
		listingDatasourceScheduler.shutdown();
		genericDatasourceScheduler.shutdown();

		timedOutSentRequestHandler.shutdown();
		nodeAlivenessTester.shutdown();
		shutdownNodeHandler.shutdown();
		nodeRecruiter.shutdown();

		logger.info("[ShutdownHook] - Master shutdown sequence initiated");

		// used for auditing logs post-shutdown
		jdbcTemplate.update("drop table if exists last_node_addresses");
		jdbcTemplate.update("create table last_node_addresses as select id, address from worker_node");

		ExecutorService endService = Executors.newFixedThreadPool(8);
		List<DataSource> running = getRunningDataSourcesSnapshot();
		running.forEach(d -> endService.execute(() -> {
			d.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.SHUTDOWN);
			d.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
			d.setDetails("Master was shutdown during run");
			dataSourceOperations.endDataSourceRun(d, false);
		}));
		endService.shutdown();
		try {
			if(!endService.awaitTermination(5, TimeUnit.MINUTES)) {
				logger.error("[ShutdownHook] - Timeout occurred awaiting end service termination");
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("[ShutdownHook] - Thread has been interrupted awaiting end service termination... this should not happen");
		}
		dataSourceOperations.clearStaged();
		masterNodeRepo.deleteById(master.getId());
		masterNodeRepo.flush();
		if(production) {
			slackMessenger.sendMessageWithArgs("*[ShutdownHook]* - Shutdown sequence complete. %d data sources will continue next startup", running.size());
		} else {
			logger.info("[ShutdownHook] - Shutdown sequence complete");
		}
	}


	private class ListingDatasourceScheduler implements Runnable {
		private final AtomicBoolean run = new AtomicBoolean(true);
		private final List<Thread> runnerThreads = Collections.synchronizedList(new ArrayList<>());

		public synchronized void shutdown() {
			run.set(false);
			runnerThreads.stream()
					.filter(Thread::isAlive)
					.filter(t -> !t.isInterrupted())
					.forEach(Thread::interrupt);
		}

		@Override
		public void run() {
			while(run.get()) {
				if(master != null && master.getRunning()) {
					logger.debug("[ListingDatasourceScheduler] - Scanning database for data sources to queue...");
					List<DataSource> listingDataSources = dataSourceRepo.findTopListingReadyToRun();
					Queue<DataSource> readyToRun = new PriorityQueue<>(listingDataSources);
					int queued = 0;

					if(readyToRun.isEmpty()) {
						logger.debug("[ListingDatasourceScheduler] - All data sources are up-to-date or queued");
					} else {
						while(master.getRunning() && !readyToRun.isEmpty()) {
							if(!run.get()) {
								return;
							}
							if(dataSourceOperations.hasCapacityToQueue(readyToRun.peek())) {
								com.findupon.commons.entity.datasource.DataSource dataSource = Objects.requireNonNull(readyToRun.poll());
								logger.info("[ListingDatasourceScheduler] - Queuing & running data source [{}]", dataSource.getUrl());
								if(dataSourceOperations.transitionToRunning(dataSource)) {
									ListingDataSourceRunner runner = (ListingDataSourceRunner) com.findupon.commons.utilities.SpringUtils.getBean("listingDataSourceRunner");
									runner.listingDataSource = dataSource;
									Thread thread = new Thread(runner);
									runnerThreads.add(thread);
									thread.start();
									queued++;
								} else {
									break;
								}
							} else {
								logger.info("[ListingDatasourceScheduler] - Max queued data sources [{}] met with [{}] waiting.",
										MAX_QUEUED_LISTING_DATA_SOURCES, readyToRun.size());
								sleep(32);
							}
						}
						String submitted = listingDataSources.stream().map(com.findupon.commons.entity.datasource.DataSource::getUrl).collect(Collectors.joining(", ", "[", "]"));
						logger.info("[ListingDatasourceScheduler] - Finished queueing [{}] data source(s) to run: {}", queued, submitted);
					}
				}
				sleep(32);
				synchronized(runnerThreads) {
					runnerThreads.removeIf(t -> !t.isAlive());
				}
			}
		}
	}

	private class GenericDatasourceScheduler implements Runnable {
		private final AtomicBoolean run = new AtomicBoolean(true);
		private final Queue<DataSource> staged = new PriorityQueue<>();
		private int maxStaged;

		public synchronized void shutdown() {
			run.set(false);
		}

		@Override
		public void run() {
			while(run.get()) {
				if(master != null && master.getRunning()) {

					int queued = getQueuedGenericDataSourcesSnapshot().size();
					maxStaged = getMaxQueuedGenericDataSources() - queued;
					maxStaged = maxStaged < 0 ? 0 : maxStaged;
					if(staged.size() < maxStaged) {
						getAndUpdateAsStaged();
					}

					logger.info("[GenericDatasourceScheduler] - Queued: [{}] Staged: [{}] Allowed: [{}] Total: [{}]",
							queued, staged.size(), maxStaged, queued + staged.size());

					if(staged.isEmpty()) {
						logger.debug("[GenericDatasourceScheduler] - All data sources are up-to-date or queued");
						sleep(32);
						continue;
					}

					// queue as many allowed before continuing and updating the queue
					while(!staged.isEmpty() && dataSourceOperations.hasCapacityToQueue(staged.peek())) {
						if(!run.get()) {
							return;
						}
						com.findupon.commons.entity.datasource.DataSource dataSource = Objects.requireNonNull(staged.poll());
						logger.debug("[GenericDatasourceScheduler] - Submitting data source [{}] to the work queue...", dataSource.getUrl());
						if(dataSourceOperations.transitionToRunning(dataSource)) {
							ClusterTransmission workOrder = new ClusterTransmission();
							workOrder.setMessage(MasterMessage.GENERIC_GATHER_AND_BUILD);
							workOrder.setDataSource(dataSource);
							workOrder.setUrlsToWork(Collections.singletonList(dataSource.getUrl()));
							workQueue.offer(workOrder);
						} else {
							break;
						}
					}
				}
				sleep(30);
			}
		}

		private void getAndUpdateAsStaged() {
			logger.debug("[GenericDatasourceScheduler] - Scanning database for data sources to stage...");
			List<DataSource> readyToStage = dataSourceRepo.findTopGenericReadyToStage(maxStaged);
			ExecutorService stagingService = Executors.newFixedThreadPool(8);
			readyToStage.forEach(d -> stagingService.execute(() -> {
				if(!run.get()) {
					return;
				}
				d.setStaged(true);
				staged.add(d);
				dataSourceRepo.save(d);
			}));
			stagingService.shutdown();
			try {
				if(!stagingService.awaitTermination(5, TimeUnit.MINUTES)) {
					logger.error("[GenericDatasourceScheduler] - Timeout occurred awaiting staging service termination attempting [{}] data sources", readyToStage.size());
				}
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				if(run.get()) {
					logger.error("[GenericDatasourceScheduler] - Thread has been interrupted awaiting staging service termination while running");
				} else {
					logger.debug("[GenericDatasourceScheduler] - Interrupt status picked up, aborting staging service");
				}
			}
		}
	}

	private void sleep(int seconds) {
		try {
			Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("[MasterNodeApplication] - Thread interrupted during scheduler sleep... this should not happen here", e);
		}
	}
}
