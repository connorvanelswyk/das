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

package com.findupon.frontier;

import com.google.common.base.Stopwatch;
import com.plainviewrd.commons.entity.frontier.VisitedSite;
import com.plainviewrd.utilities.MemoryUtils;
import crawlercommons.robots.BaseRobotRules;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FrontierApplication {
	private static final Logger logger = LoggerFactory.getLogger(FrontierApplication.class);
	private final Set<String> linkPriorityKeywords = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private Comparator<String> priority;
	private final FrontierRunner frontierRunner = new FrontierRunner();

	@Value("${production}") private Boolean production;
	@Autowired private com.plainviewrd.commons.learning.AssetRecognizer assetRecognizer;
	@Autowired private com.plainviewrd.commons.repository.frontier.VisitedSiteRepo visitedSiteRepo;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private com.plainviewrd.commons.utilities.AutomobileAttributeMatcher attributeMatcher;


	public static void main(String... args) {
		logger.info("[FrontierApplication] - Starting...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:frontier-context.xml");
		FrontierApplication instance = applicationContext.getBean(FrontierApplication.class);
		instance.init();
	}

	private void init() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
		logger.info("Current env: [{}] Current heap size: [{}] Maximum heap size: [{}] JVM bit size: [{}]",
				production ? "prod" : "dev",
				MemoryUtils.getCurrentHeapSizeStr(),
				MemoryUtils.getMaxHeapSizeStr(),
				System.getProperty("sun.arch.data.model"));

		attributeMatcher.getFullAttributeMap().entrySet().stream()
				.map(e -> e.getValue().getAttribute()).forEach(s -> {
			if(s.contains("-")) {
				linkPriorityKeywords.add(s.replace("-", ""));
			}
			if(s.contains(" ")) {
				linkPriorityKeywords.add(s.replace(" ", "-"));
				linkPriorityKeywords.add(s.replace(" ", ""));
			}
			linkPriorityKeywords.add(s);
		});
		linkPriorityKeywords.add("vw");
		linkPriorityKeywords.add("auto");
		linkPriorityKeywords.add("cars");
		linkPriorityKeywords.add("vehicle");
		linkPriorityKeywords.add("dealer");
		linkPriorityKeywords.add("drive");
		priority = (s1, s2) -> {
			boolean p1 = linkPriorityKeywords.stream().anyMatch(s -> StringUtils.containsIgnoreCase(s1, s));
			boolean p2 = linkPriorityKeywords.stream().anyMatch(s -> StringUtils.containsIgnoreCase(s2, s));
			return ((p1 && p2) || (!p1 && !p2)) ? s1.compareTo(s2) : p1 ? -1000 : 1000;
		};
		Thread thread = new Thread(frontierRunner);
		thread.start();
		try {
			thread.join();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("Main init thread was interrupted. This should not happen.");
		}
	}

	private void shutdownHook() {
		logger.info("[ShutdownHook] - Shutdown sequence initiated");
		frontierRunner.shutdown();
		logger.info("[ShutdownHook] - Shutdown sequence complete");
	}

	private class FrontierRunner implements Runnable {
		private final AtomicBoolean run = new AtomicBoolean(true);
		private final LongAdder counter = new LongAdder();
		private final int statThreshold = 40;
		private final int visitedPurgeThreshold = 100;

		private final Queue<String> frontierQueue = new PriorityQueue<>(priority);
		private final Set<String> insensitiveVisitedUrls = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
		private final Set<String> insensitiveDeterminedAssetUrls = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
		private final List<VisitedSite> visitedSites = Collections.synchronizedList(new ArrayList<>());
		private final Set<String> domainKeywordsToAvoid = new HashSet<>(Arrays.asList("login", "accessories"));

		private final ExecutorService frontierService = Executors.newFixedThreadPool(44);
		private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());
		private final List<Thread> persistThreads = Collections.synchronizedList(new ArrayList<>());


		public synchronized void shutdown() {
			run.set(false);
			futures.stream().filter(f -> !f.isDone() && !f.isCancelled()).forEach(f -> f.cancel(true));
			persistThreads.forEach(Thread::interrupt);
			frontierService.shutdownNow();
		}

		private void seed() {
			String sql = "select url from data_source where perm_disabled = 0 and bot_class is null";
			List<String> urls = jdbcTemplate.queryForList(sql, String.class);
			frontierQueue.addAll(urls);
			insensitiveDeterminedAssetUrls.addAll(urls);
		}

		@Override
		public void run() {
			seed();
			while(run.get()) {
				final String nextUrl;
				synchronized(frontierQueue) {
					nextUrl = frontierQueue.poll();
				}
				if(nextUrl != null) {
					if(!insensitiveVisitedUrls.add(nextUrl)) {
						continue;
					}
					futures.add(frontierService.submit(() -> {
						com.plainviewrd.commons.entity.frontier.VisitedSite visitedSite = new com.plainviewrd.commons.entity.frontier.VisitedSite(nextUrl);
						Pair<BaseRobotRules, String> rules = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.downloadRobotRules(nextUrl, com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE);
						if(Thread.currentThread().isInterrupted()) {
							return;
						}
						boolean isAllowed = !rules.getLeft().isAllowNone() && !rules.getLeft().isDeferVisits();
						visitedSite.setRobotsAllow(isAllowed);
						if(isAllowed) {
							Document document = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.download(nextUrl, com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE, false).getDocument();
							if(Thread.currentThread().isInterrupted()) {
								return;
							}
							if(document != null) {
								boolean redirected = !StringUtils.equalsIgnoreCase(nextUrl, document.location());
								if(redirected) {
									String parsed = com.plainviewrd.commons.searchparty.ScoutServices.parseByProtocolAndHost(document.location());
									if(parsed == null) {
										parsed = document.location();
									}
									insensitiveVisitedUrls.add(parsed);
									visitedSite.setUrl(parsed);
								}
								if(insensitiveDeterminedAssetUrls.add(nextUrl)
										|| (redirected && insensitiveDeterminedAssetUrls.add(visitedSite.getUrl()))) {
									assetRecognizer.determineAssetType(document).ifPresent(visitedSite::setAssetTypeGuess);
									com.plainviewrd.commons.entity.datasource.PageMeta pageMeta = com.plainviewrd.commons.bot.PageMetaBot.buildPageMeta(document);
									visitedSite.setDescription(pageMeta.getDescription());
									visitedSite.setKeywords(pageMeta.getKeywords());
									visitedSite.setAuthor(pageMeta.getAuthor());
									visitedSite.setTitle(pageMeta.getTitle());
									visitedSites.add(visitedSite);
								}
								URL url = com.plainviewrd.commons.searchparty.ScoutServices.getUrlFromString(nextUrl, true);
								if(url != null) {
									String domainName = com.plainviewrd.commons.searchparty.ScoutServices.getDomainName(url);
									Set<String> externalLinks = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
									externalLinks.addAll(document.select("a[href]").stream()
											.map(e -> e.attr("abs:href"))
											.filter(StringUtils::isNotBlank)
											.filter(s -> !StringUtils.containsIgnoreCase(s, domainName)) // external links only please
											.filter(com.plainviewrd.commons.searchparty.ScoutServices::clearToVisitDomain)
											.map(com.plainviewrd.commons.searchparty.ScoutServices::parseByProtocolAndHost)
											.filter(Objects::nonNull)  // valid only please
											.filter(com.plainviewrd.commons.searchparty.ScoutServices::acceptedDomainExtension)
											.filter(s -> domainKeywordsToAvoid.stream().noneMatch(d -> StringUtils.containsIgnoreCase(s, d)))
											.collect(Collectors.toSet()));
									if(!externalLinks.isEmpty()) {
										logger.debug("Adding [{}] external links to the frontier queue from [{}]", externalLinks.size(), nextUrl);
										synchronized(frontierQueue) {
											frontierQueue.addAll(externalLinks);
										}
									}
								} else {
									logger.warn("URL malformed [{}]", nextUrl);
								}
							} else {
								logger.warn("Document came back null from the connection agent [{}]", nextUrl);
							}
						} else {
							logger.warn("Robots.txt allow none or defer visits [{}]", nextUrl);
						}
					}));
				} else {
					logger.info(com.plainviewrd.commons.utilities.ConsoleColors.yellow("The frontier queue is empty"));
					sleep(5000L);
				}
				sleep(200L);

				counter.increment();
				if(counter.longValue() % statThreshold == 0) {
					int running;
					synchronized(futures) {
						futures.removeIf(Future::isDone);
						running = futures.size();
					}
					synchronized(persistThreads) {
						persistThreads.removeIf(t -> !t.isAlive());
					}
					int frontierSize;
					synchronized(frontierQueue) {
						frontierSize = frontierQueue.size();
					}
					logger.info(com.plainviewrd.commons.utilities.ConsoleColors.purple("Frontier Queue Size: [{}]  Futures: [{}]  Visited URLs: [{}]  Visited to Persist: [{}]  Running persist Threads: [{}]"),
							frontierSize, running, insensitiveVisitedUrls.size(), visitedSites.size(), persistThreads.size());
				}
				if(visitedSites.size() >= visitedPurgeThreshold) {
					synchronized(visitedSites) {
						logger.info("Visited sites >= persist threshold [{}] queueing thread to save & clearing...", visitedPurgeThreshold);
						List<VisitedSite> sitesToSave = new ArrayList<>(visitedSites);
						synchronized(persistThreads) {
							Thread persistThread = new Thread(() -> {
								Stopwatch stopwatch = Stopwatch.createStarted();
								for(com.plainviewrd.commons.entity.frontier.VisitedSite visitedSite : sitesToSave) {
									if(Thread.currentThread().isInterrupted()) {
										break;
									}
									visitedSiteRepo.save(visitedSite);
								}
								logger.info("Persist complete in [{}]", com.plainviewrd.commons.utilities.TimeUtils.format(stopwatch));
							});
							persistThreads.add(persistThread);
							persistThread.start();
						}
						visitedSites.clear();
					}
				}
			}
		}

		private void sleep(long millis) {
			try {
				Thread.sleep(millis);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				shutdown();
			}
		}
	}
}
