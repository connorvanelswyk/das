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

package com.findupon.commons.searchparty;

import com.google.common.base.Stopwatch;
import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.commons.bot.PageMetaBot;
import com.findupon.commons.building.ProductUtils;
import com.findupon.commons.dao.AutomobileDao;
import com.findupon.commons.dao.core.JdbcFacade;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.entity.datasource.DataSourceStatus;
import com.findupon.commons.entity.datasource.DataSourceStatusReason;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.netops.ConnectionAgent;
import com.findupon.commons.netops.entity.AgentResponse;
import com.findupon.commons.netops.entity.HttpStatusCode;
import com.findupon.commons.netops.entity.RequestedAction;
import com.findupon.commons.utilities.DataSourceOperations;
import com.findupon.commons.utilities.SlackMessenger;
import com.findupon.commons.utilities.TimeUtils;
import crawlercommons.robots.BaseRobotRules;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.findupon.commons.utilities.ConsoleColors.*;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AbstractProductGatherer<P extends Product & Serializable> {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, Queue<String>> adjacencyMap = Collections.synchronizedMap(new TreeMap<>(parentPriority()));
	private final int maxParentSize = 512;
	private final int maxEdgeSize = 4096;
	private final int maxPoolSize = 1;
	private final int absoluteMaxDepth = 16;
	private final int maxDepthIncreaseRate = 4;
	private final int longCrawlTimeMinutes = 45;
	private int drillLog = 0;
	private long nodeId = 0;

	// for every x amount of time, we need at least y products or z identifiers to continue. standard range == 15 minute increments (~n^2.2)
	private final AbortThreshold productAbortThreshold = AbortThreshold.fromStandardRange(1, 5, 11, 20, 34, 52, 72, 97, 143, 182);
	private final AbortThreshold identifierAbortThreshold = AbortThreshold.fromStandardRange(1, 6, 13, 23, 39, 60, 83, 108, 156, 195);
	private final List<Long> backoffStatusCodeTimestamps = Collections.synchronizedList(new ArrayList<>());

	// during x amount of time only allow y backoff codes before aborting (i.e. 7 codes within 10 minutes = failure)
	// keep in mind ChloeRetryStrategy has a wait period when a such a code is encountered (~12 seconds)
	private int baseCrawlRate = 3000;
	private final int minBaseCrawlRate = 2000;
	private final int maxBaseCrawlRate = 10_000;
	private final int crawlRateChange = 1000;
	private final long maxBackoffStatusCodesInRange = 8;
	private final long maxBackoffStatusCodesTimeRangeMillis = TimeUnit.MINUTES.toMillis(10);

	DataSource currentDataSource;
	private URL currentDataSourceUrl;
	private BaseRobotRules robotRules;
	private final AtomicBoolean abort = new AtomicBoolean();
	private final LongAdder totalEdges = new LongAdder();
	private final LongAdder totalParents = new LongAdder();
	private final AtomicInteger maxDepth = new AtomicInteger(absoluteMaxDepth / 2);
	private final LongAdder depth = new LongAdder();
	private final LongAdder persistedProducts = new LongAdder();
	private final DoubleAdder downloadedMb = new DoubleAdder();
	final LongAdder removedProducts = new LongAdder();
	final LongAdder builtProducts = new LongAdder();

	final Set<String> insensitiveProductIds = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
	final Set<String> insensitiveAnalyzedLinks = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
	final Set<String> insensitiveVisitedUrls = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
	final List<P> products = Collections.synchronizedList(new ArrayList<>());

	private final ExecutorService gatheringService = Executors.newFixedThreadPool(maxPoolSize);
	private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());

	@Autowired private JdbcFacade jdbcFacade;
	@Autowired private DataSourceOperations dataSourceOperations;
	@Autowired private SlackMessenger slackMessenger;
	@Autowired private PageMetaBot pageMetaBot;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired protected ProductUtils productUtils;
	@Autowired protected AutomobileDao automobileDao;


	protected abstract Comparator<String> edgePriority();

	protected abstract Comparator<String> parentPriority();

	protected abstract String[] keywordsToAvoid();

	protected abstract void createProductIfFound(Document document);

	public abstract P buildProduct(Document document);

	/**
	 * Revisit all existing products fur the current data source. Running this first we are able to seed the common gathering
	 * collections to improve run efficiency.
	 * @return the milliseconds taken used to calculate final run stats.
	 */
	protected abstract long revisit();


	// TODO: CHANGE ALL DATA INTERACTION TO USE THE NEW PRODUCT DATA SERVICE

	public DataSource initiate(DataSource dataSource, long id) {
		nodeId = id;
		currentDataSource = dataSource;

		/* root url validation and redirect handling from initial connection */
		Document landingPage;
		if((landingPage = handleRootUrl()) == null) {
			return currentDataSource;
		}

		/* robots rules. false indicates we have decided not to proceed */
		if(!handleRobotRules()) {
			return currentDataSource;
		}

		/* revisit existing products */
		long revisitTimeMillis = revisit();

		if(checkAbort()) {
			return currentDataSource;
		}

		/* initiate the gathering */
		long gatheringTimeMillis = searchAndGather(landingPage);

		if(checkAbort()) {
			return currentDataSource;
		}

		if(builtProducts.longValue() == 0) {
			setFailureStatus(DataSourceStatusReason.NO_PRODUCTS, "Zero built product count at the end of the crawl");
			return currentDataSource;
		}

		// we did it, home stretch!
		dataSource.setStatus(DataSourceStatus.SUCCESS);
		dataSource.setStatusReason(null);
		dataSource.setDetails(null);

		// TODO: add 2 more columns to data_source... 1 for revisit time and 1 for gathering time then update this method
		dataSourceOperations.calculateAndUpdateDataSourceStats(currentDataSource, TimeUnit.MILLISECONDS.toSeconds(gatheringTimeMillis + revisitTimeMillis),
				Math.toIntExact(builtProducts.longValue()), insensitiveVisitedUrls.size(), insensitiveAnalyzedLinks.size(),
				new BigDecimal(Double.toString(downloadedMb.doubleValue())));

		long totalTimeMillis = gatheringTimeMillis + revisitTimeMillis;
		String totalTimeStr = "Total time:          [";
		if(TimeUnit.MILLISECONDS.toMinutes(totalTimeMillis) >= longCrawlTimeMinutes) {
			totalTimeStr += red(String.valueOf(TimeUtils.format(totalTimeMillis)));
		} else {
			totalTimeStr += green(String.valueOf(TimeUtils.format(totalTimeMillis)));
		}
		totalTimeStr += "]";
		String message = String.format("%s%s%n" +
						"%-75s  " + // analyzed
						"%-48s  " + // visited
						"%-64s  " + // products built/urls
						"%s%n" + // removed
						"%-75s  " + // revisit time
						"%-48s  " + // gathering time
						"%-55s" +   // total time
						"%s%n",  // crawl rate
				nodePre(),
				"Process completed for: [" + blueUnderlined(ScoutServices.getDomainName(currentDataSourceUrl) + "]:"),
				"                           URLs analyzed:  [" + cyan(String.valueOf(insensitiveAnalyzedLinks.size())) + "]",
				"Visited URLs:   [" + cyan(String.valueOf(insensitiveVisitedUrls.size())) + "]",
				"Built Products: [" + green(String.valueOf(builtProducts.longValue())) + "]",
				"Removed Products: [" + blue(String.valueOf(removedProducts)) + "]",
				"                           Revisit time:   [" + cyan(TimeUtils.format(revisitTimeMillis)) + "]",
				"Gathering time: [" + cyan(TimeUtils.format(gatheringTimeMillis)) + "]",
				totalTimeStr,
				"Crawl Rate:       [" + cyan(TimeUtils.formatSeconds(currentDataSource.getCrawlRate())) + "]");
		logger.info(message);
		return currentDataSource;
	}

	private Document handleRootUrl() {
		currentDataSourceUrl = ScoutServices.getUrlFromString(ScoutServices.parseByProtocolAndHost(currentDataSource.getUrl()), true);
		String error = null, urlLoc = null;
		Document landingPage = null;

		if(currentDataSourceUrl == null) {
			error = "URL failed basic parsing";
		}
		if(error == null) {
			// perform the initial connection to check validity and resolve any redirects
			AgentResponse agentResponse = ConnectionAgent.INSTANCE.download(currentDataSourceUrl.toExternalForm(), currentDataSource, nodeId);

			if(RequestedAction.ABORT_CRAWL.equals(agentResponse.getDecision().getAction())) {
				setFailureStatus(DataSourceStatusReason.BLOCKED, "Decision made to abort crawl, blocked status picked up!");
				currentDataSource.setDetails(agentResponse.getDecision().getMessage());
				return null;
			}
			if(StringUtils.containsIgnoreCase(agentResponse.getContent(), "Powered by Carsforsale.com")) {
				setFailureStatus(DataSourceStatusReason.BLOCKED, "Preemptive blocked status determined by carsforsale website");
				return null;
			}
			landingPage = agentResponse.getDocument();
			if(landingPage == null) {
				// all other failure cases for root url connection, status code will determine why
				error = String.format("Null document returned with status code [%d]", agentResponse.getDecision().getStatusCode());
			} else {
				urlLoc = landingPage.location();
			}
		}
		if(error == null) {
			// parse the url that resolved through the initial connection
			String parsedUrlLoc = ScoutServices.parseByProtocolAndHost(urlLoc);
			if(parsedUrlLoc == null) {
				error = String.format("Redirect URL failed basic parsing %s", urlLoc);
			} else {
				urlLoc = parsedUrlLoc;
			}
		}
		if(error == null) {
			// check if a redirect occurred and if so, handle accordingly
			if(!StringUtils.equalsIgnoreCase(currentDataSourceUrl.toExternalForm(), urlLoc)) {
				logger.debug(nodePre() + "Root URL was redirected. Handling data source accordingly. From [{}] To [{}]", currentDataSourceUrl.toExternalForm(), urlLoc);

				// check if the new url already exists as a data source. if it does, just pipe a slack message for now as the update process is a bit complicated...
				Integer existing = jdbcTemplate.queryForObject("select count(1) from data_source where url = ?", Integer.class, urlLoc);
				if(existing == null || existing == 0) {
					String finalUrlLoc = urlLoc;

					// TODO: generify
					int updated = jdbcFacade.retryingUpdate("update automobile set dealer_url = ? " +
							"where dealer_url = ? and source_url is null", ps -> {
						JdbcFacade.setParam(ps, finalUrlLoc, 1);
						JdbcFacade.setParam(ps, currentDataSource.getUrl(), 2);
					});
					logger.info(nodePre() + green("Datasource URL has been updated, [{}] row count updating from [{}] To [{}]"),
							updated, currentDataSourceUrl.toExternalForm(), urlLoc);

					currentDataSource.setUrl(urlLoc);
				} else {
					setFailureStatus(DataSourceStatusReason.DUPLICATE, String.format("Duplicate data source of %s", urlLoc));
					return null;
				}
			}
		}
		if(error != null) {
			setFailureStatus(DataSourceStatusReason.INVALID_ROOT_URL, error);
			return null;
		} else {
			return landingPage;
		}
	}

	private boolean handleRobotRules() {
		Pair<BaseRobotRules, String> ruleContentPair = ConnectionAgent.INSTANCE.downloadRobotRules(currentDataSource.getUrl(), currentDataSource);
		robotRules = ruleContentPair.getLeft();
		String content = ruleContentPair.getRight();
		boolean includedOurAgent = StringUtils.containsIgnoreCase(content, "FindUpon");

		if(includedOurAgent) {
			slackMessenger.sendMessageWithArgs("FindUpon explicit user agent found! Allowed: [%s] URL: [%s]",
					robotRules.isAllowNone() || robotRules.isDeferVisits() ? "no :weary:" : "yes! :aw_yeah:", currentDataSource.getUrl() + "robots.txt");
			slackMessenger.sendTextFile("robots.txt", content);
		}
		if(robotRules.isAllowNone()) {
			String error;
			if(includedOurAgent) {
				error = "Explicit user agent disallow";
			} else {
				error = String.format("Robots.txt allow none [%s]", robotRules.isAllowNone());
			}
			setFailureStatus(DataSourceStatusReason.ROBOTS_TXT, error);

			// TODO: generify
			List<Long> idsToDelete = automobileDao.findAllByDataSourceId(currentDataSource.getId()).stream()
					.map(Automobile::getId)
					.collect(Collectors.toList());
			logger.warn(nodePre() + red("Removing {} cars for dealer [{}] based on robots.txt :("), idsToDelete.size(), currentDataSource.getUrl());

			automobileDao.deleteAllById(idsToDelete);
			return false;

		} else if(robotRules.isDeferVisits()) {
			String error;
			if(includedOurAgent) {
				error = "Explicit user agent disallow";
			} else {
				error = String.format("Robots.txt defer visits [%s]", robotRules.isDeferVisits());
			}
			setFailureStatus(DataSourceStatusReason.ROBOTS_TXT, error);
			return false;
		}
		currentDataSource.setCrawlRate(robotRules.getCrawlDelay());
		return true;
	}

	/**
	 * @param landingPage passed in from the root url validation to save a connection.
	 * @return the time taken in milliseconds, used in the calling method to calculate final run stats.
	 */
	private long searchAndGather(Document landingPage) {
		Stopwatch stopwatch = Stopwatch.createStarted();

		logger.debug(nodePre() + "Generic gathering initiated on [{}]", blueUnderlined(currentDataSource.getUrl()));
		pageMetaBot.deployOne(landingPage, currentDataSource);

		Set<String> siteMapUrls = new HashSet<>(robotRules.getSitemaps());
		siteMapUrls.add(ScoutServices.parseByProtocolAndHost(currentDataSource.getUrl()) + "sitemap.xml");

		for(String siteMapUrl : siteMapUrls) {
			Document siteMap = ConnectionAgent.INSTANCE.xmlDownload(siteMapUrl, currentDataSource);
			if(siteMap != null) {
				getEdgesFromSitemap(siteMap).ifPresent(edges -> updateAdjacencyMap(edges, siteMap.location()));
			}
		}
		// dealerAddress = AddressOperations.getAddress(landingPage).orElse(null);

		// don't add edges of the root url if we already picked it up from the site map
		if(adjacencyMap.entrySet().stream().noneMatch(e ->
				StringUtils.equalsIgnoreCase(e.getKey(), currentDataSource.getUrl()) &&
						e.getValue().stream().noneMatch(s ->
								StringUtils.equalsIgnoreCase(s, currentDataSource.getUrl())))) {
			adjacentEdges(landingPage).ifPresent(edges -> updateAdjacencyMap(edges, currentDataSource.getUrl()));
		}

		while(true) {
			if(workingFutures() < maxPoolSize && !adjacencyMap.isEmpty()) {
				final Queue<String> edgesToVisit;
				final String nextUrl;
				synchronized(adjacencyMap) {
					nextUrl = adjacencyMap.entrySet().iterator().next().getKey();
					edgesToVisit = adjacencyMap.remove(nextUrl);
				}
				if(insensitiveVisitedUrls.add(nextUrl)) {
					futures.add(gatheringService.submit(() -> {
						int preDrillSize = insensitiveProductIds.size();
						while(!edgesToVisit.isEmpty()) {
							final String edge = edgesToVisit.poll();
							if(Thread.currentThread().isInterrupted()) {
								abort.set(true);
								return;
							}
							// if there is a good amount of work left to do and threads are available, send backup
							if(edgesToVisit.size() > 64 && workingFutures() < maxPoolSize) {
								futures.add(gatheringService.submit(() -> edgeGatherer().accept(edge)));
							} else {
								edgeGatherer().accept(edge);
							}
						}
						// if the depth approaching the max and we are still finding products, increase the max
						synchronized(depth) {
							if(depth.longValue() < absoluteMaxDepth) {
								depth.increment();
								if(depth.longValue() >= maxDepth.get() - maxDepthIncreaseRate
										&& insensitiveProductIds.size() - preDrillSize > 0
										&& maxDepth.get() < absoluteMaxDepth
										&& maxDepth.addAndGet(maxDepthIncreaseRate) > absoluteMaxDepth) {
									maxDepth.set(absoluteMaxDepth);
								}
							}
						}
					}));
				}
			}
			verifyValidCrawlState(stopwatch.elapsed(TimeUnit.MILLISECONDS));

			if(checkAbort()) {
				cancelAllWorkingFutures();
				logger.debug(nodePre() + "Decision made to abort crawl by gatherer thread");
				return stopwatch.elapsed(TimeUnit.MILLISECONDS);
			}
			if(adjacencyMap.isEmpty() && allFuturesComplete()) {
				break;
			} else {
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(1));
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.debug(red("[Node {}] - Thread has been interrupted. Abort abort!"), nodeId);
					abortFromInterrupt();
					return stopwatch.elapsed(TimeUnit.MILLISECONDS);
				}
			}
			if(++drillLog % 300 == 0) {
				drillLog = 1;
				int workingEdges = 0, workingParents = 0;
				synchronized(adjacencyMap) {
					for(Map.Entry<String, Queue<String>> entry : adjacencyMap.entrySet()) {
						workingParents++;
						workingEdges += entry.getValue().size();
					}
				}
				long millis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
				long second = (millis / 1000) % 60;
				long minute = (millis / (1000 * 60)) % 60;
				long hour = (millis / (1000 * 60 * 60)) % 24;
				String timeStr;
				String crawlRate = blue(String.valueOf((double)(currentDataSource.getCrawlRate() == 0 ? baseCrawlRate : currentDataSource.getCrawlRate()) / 1000));
				if(minute >= longCrawlTimeMinutes) {
					timeStr = "Time: [" + red(String.format("%02d", hour)) + ":" + red(String.format("%02d", minute)) + ":" + red(String.format("%02d", second)) + " (cr=" + crawlRate + ")]";
				} else {
					timeStr = "Time: [" + blue(String.format("%02d", hour)) + ":" + blue(String.format("%02d", minute)) + ":" + blue(String.format("%02d", second)) + " (cr=" + crawlRate + ")]";
				}
				String printOut = String.format("%-23s %-50s %-44s %-71s %-27s %-26s %-49s %-48s %s",
						nodePre(),
						"Site: [" + blueUnderlined(ScoutServices.getDomainName(currentDataSourceUrl)) + "]",
						"Cars (t/p): [" + blue(String.valueOf(builtProducts.longValue())) + "/" + cyan(String.valueOf(persistedProducts.longValue())) + "]",
						timeStr,
						"Visited: [" + blue(String.valueOf(insensitiveVisitedUrls.size())) + "]",
						"Threads: [" + blue(String.valueOf(futures.stream().filter(f -> !f.isDone()).count())) + "]",
						"Edges (q/t): [" + blue(String.valueOf(workingEdges)) + "/" + cyan(String.valueOf(totalEdges.longValue())) + "]",
						"Parents (q/t): [" + blue(String.valueOf(workingParents)) + "/" + cyan(String.valueOf(totalParents.longValue())) + "]",
						"Depth (c/m): [" + blue(String.valueOf(depth.longValue())) + "/" + cyan(String.valueOf(absoluteMaxDepth)) + "]");
				logger.info(printOut);
			}
		}
		logger.debug(nodePre() + "All futures submitted, shutting down the thread pool");
		shutdownService(gatheringService, this::abortFromInterrupt, "gathering");
		persistAndClear();
		return stopwatch.elapsed(TimeUnit.MILLISECONDS);
	}

	/**
	 * Check to see if are on the right path (missing ids in range, finding enough identifiers & products)
	 * If not, abort and set the ds accordingly
	 */
	private void verifyValidCrawlState(long currentCrawlTimeMillis) {
		if(!abort.get()) {
			String error = null;
			int currentProductIds = insensitiveProductIds.size();
			int currentBuiltAutomobiles = builtProducts.intValue();

			if(identifierAbortThreshold.shouldAbort(currentProductIds, currentCrawlTimeMillis, TimeUnit.MILLISECONDS)) {
				error = String.format("Product identifiers %d less than allowed for current time %s",
						currentProductIds, TimeUtils.format(currentCrawlTimeMillis));

			} else if(productAbortThreshold.shouldAbort(currentBuiltAutomobiles, currentCrawlTimeMillis, TimeUnit.MILLISECONDS)) {
				error = String.format("Build products %d less than allowed for current time %s",
						currentBuiltAutomobiles, TimeUtils.format(currentCrawlTimeMillis));
			}
			if(error != null) {
				abort.set(true);
				setFailureStatus(DataSourceStatusReason.NO_PRODUCTS, error);
			}
		}
	}

	private void setFailureStatus(DataSourceStatusReason reason, String errorMessage) {
		logger.warn(nodePre() + yellow(errorMessage) + " - DS ID: [{}]", currentDataSource.getId());
		currentDataSource.setStatus(DataSourceStatus.FAILURE);
		currentDataSource.setStatusReason(reason);
		currentDataSource.setDetails(errorMessage);
	}

	void shutdownService(ExecutorService service, Runnable interruptHandler, String location) {
		service.shutdown();
		try {
			if(!service.awaitTermination(MasterMessage.GENERIC_GATHER_AND_BUILD.getTimeout() / 2, TimeUnit.MILLISECONDS)) {
				logger.warn(nodePre() + "Timeout occurred awaiting [{}] service termination for dealer [{}]", location, currentDataSource.getUrl());
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.debug(nodePre() + red("Thread has been interrupted awaiting [{}] service termination for dealer [{}]"), location, currentDataSource.getUrl());
			interruptHandler.run();
		}
	}

	private void abortFromInterrupt() {
		abort.set(true);
		cancelAllWorkingFutures();
	}

	private long workingFutures() {
		synchronized(futures) {
			return futures.stream().filter(f -> !f.isDone()).count();
		}
	}

	private boolean allFuturesComplete() {
		synchronized(futures) {
			return futures.stream().allMatch(Future::isDone);
		}
	}

	private void cancelAllWorkingFutures() {
		synchronized(futures) {
			futures.stream()
					.filter(f -> !f.isCancelled() && !f.isDone())
					.forEach(f -> {
						if(!f.cancel(true)) {
							logger.warn(nodePre() + "Future [{}] failed to cancel", f.toString());
						}
					});
		}
	}

	private Consumer<String> edgeGatherer() {
		return edge -> {
			if(insensitiveVisitedUrls.add(edge)) {
				Document nextPage = download(edge);
				if(nextPage != null && wasNotRedirectedExternally(nextPage)) {
					if(!StringUtils.equalsIgnoreCase(edge, nextPage.location())) {
						insensitiveVisitedUrls.add(nextPage.location());
					}
					createProductIfFound(nextPage);

					if(nextPage.location().endsWith(".xml")) {
						getEdgesFromSitemap(Jsoup.parse(nextPage.html(), nextPage.location(), Parser.xmlParser()))
								.ifPresent(edges -> updateAdjacencyMap(edges, nextPage.location()));
					} else {
						adjacentEdges(nextPage).ifPresent(edges -> updateAdjacencyMap(edges, nextPage.location()));
					}
				}
			}
		};
	}

	private void updateAdjacencyMap(Queue<String> edges, String url) {
		synchronized(adjacencyMap) {
			final long currentEdgeTotal = totalEdges.longValue();
			final long currentDepth;
			synchronized(depth) {
				currentDepth = depth.longValue();
			}
			if(currentDepth < maxDepth.get()
					&& totalParents.longValue() + 1 < maxParentSize
					&& currentEdgeTotal < maxEdgeSize) {

				if(currentEdgeTotal + edges.size() > maxEdgeSize) {
					// trim the edge list to fit the max
					long edgeCapacity = maxEdgeSize - currentEdgeTotal;
					Queue<String> trimmedEdges = new PriorityQueue<>(edgePriority());

					for(int x = 0; x < edgeCapacity && edges.peek() != null; x++) {
						trimmedEdges.offer(edges.poll());
					}
					edges = trimmedEdges;
				}
				totalEdges.add(edges.size());
				totalParents.increment();
				adjacencyMap.putIfAbsent(url, edges);
			}
		}
	}

	void persistAndClear() {
		if(!products.isEmpty()) {
			synchronized(products) {
				persistedProducts.add(products.size());
				automobileDao.saveAll((Collection<Automobile>)products);
				products.clear();
			}
		}
	}

	Document download(String url) {
		/* wait depending on the crawl rate */
		if(checkAbort() || sleep()) {
			return null;
		}
		AgentResponse agentResponse = ConnectionAgent.INSTANCE.download(url, currentDataSource, nodeId);

		if(HttpStatusCode.isBackoffCode(agentResponse.getDecision().getStatusCode())) {
			backoffStatusCodeTimestamps.add(System.currentTimeMillis());
			if(baseCrawlRate < maxBaseCrawlRate) {
				baseCrawlRate += crawlRateChange;
			}
		} else {
			if(baseCrawlRate > minBaseCrawlRate) {
				baseCrawlRate -= crawlRateChange;
			}
		}
		if(!backoffStatusCodeTimestamps.isEmpty()) {
			long now = System.currentTimeMillis();
			int numBackoffCodesInTimeRange = 0;
			for(long backoffCodeTime : backoffStatusCodeTimestamps) {
				if(now - backoffCodeTime <= maxBackoffStatusCodesTimeRangeMillis) {
					numBackoffCodesInTimeRange++;
				}
			}
			if(numBackoffCodesInTimeRange >= maxBackoffStatusCodesInRange) {
				setFailureStatus(DataSourceStatusReason.BACKOFF, String.format("Decision made to abort crawl based on [%d] backoff codes in a [%d] minute range",
						numBackoffCodesInTimeRange, TimeUnit.MILLISECONDS.toMinutes(maxBackoffStatusCodesTimeRangeMillis)));
				// set the details again to the last html seen
				currentDataSource.setDetails("Last raw HTML: \n\n" + agentResponse.getContent());
				abort.set(true);
				return null;
			}
		}

		/* calculate and add to total downloaded */
		if(StringUtils.isNotEmpty(agentResponse.getContent())) {
			downloadedMb.add((double)agentResponse.getContent().getBytes().length / (1024 * 1024));
		}

		switch(agentResponse.getDecision().getAction()) {
			case ABORT_CRAWL:
				abort.set(true);
				setFailureStatus(DataSourceStatusReason.BLOCKED, "Decision made to abort crawl, blocked status picked up!");
				currentDataSource.setDetails(agentResponse.getDecision().getMessage());
				break;
			case PROCEED:
				return agentResponse.getDocument();
		}
		return null;
	}

	private boolean sleep() {
		long crawlRate = currentDataSource.getCrawlRate() == null ? 0 : currentDataSource.getCrawlRate();
		if(crawlRate < baseCrawlRate) {
			crawlRate = baseCrawlRate;
		}
		if(crawlRate > 0) {
			double deviation = (double)crawlRate / 20;
			long waitTime = (long)(ThreadLocalRandom.current().nextDouble(crawlRate - deviation, crawlRate + deviation));
			try {
				Thread.sleep(waitTime);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				abort.set(true);
				return true;
			}
		}
		return false;
	}

	boolean checkAbort() {
		return abort.get() || Thread.currentThread().isInterrupted();
	}

	private Optional<Queue<String>> getEdgesFromSitemap(Document siteMap) {
		if(siteMap != null) {
			Elements siteMapLinks = siteMap.select("loc");
			return siteMapLinks.stream()
					.map(Element::text)
					.filter(this::shouldVisit)
					.filter(edge -> !edge.equals(siteMap.location()))
					.map(s -> ScoutServices.encodeSpacing(s, true))
					.collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new PriorityQueue<>(edgePriority())),
							edges -> edges.isEmpty() ? Optional.empty() : Optional.of(edges)));
		}
		return Optional.empty();
	}

	private Optional<Queue<String>> adjacentEdges(Document document) {
		Elements anchors = document.select("a[href]");
		if(anchors.isEmpty()) {
			return Optional.empty();
		}
		Queue<String> nextUrls = new PriorityQueue<>(edgePriority());
		int stale = 0, fresh = 0, invalid = 0, found = anchors.size();

		for(Element anchor : anchors) {
			String link = anchor.attr("abs:href");
			if(StringUtils.isNotEmpty(link) && !StringUtils.equalsIgnoreCase(link, document.location())) {
				link = ScoutServices.encodeSpacing(link, true);
				if(!insensitiveAnalyzedLinks.add(link)) {
					stale++;
				} else {
					URL nextUrl = ScoutServices.formUrlFromString(link);
					boolean tooSimilar = false;
					if(nextUrl != null) {
						String query = nextUrl.getQuery();
						if(query != null && query.length() > 24) {
							for(String url : nextUrls) {
								int distance = LevenshteinDistance.getDefaultInstance().apply(link, url);
								if(distance < 3) {
									tooSimilar = true;
									break;
								}
							}
						}
					}
					fresh++;
					if(!tooSimilar && shouldVisit(link)) {
						nextUrls.offer(link);
					}
				}
			} else {
				invalid++;
			}
		}
		float freshPercentage = fresh * 100F / found;
		float stalePercentage = stale * 100F / found;
		float invalidPercentage = invalid * 100F / found;
		boolean worthContinuing = stalePercentage + invalidPercentage <= 95;

		if(logger.isTraceEnabled()) {
			String printOut;
			if(worthContinuing) {
				printOut = "[Link Analyzer] - Total: [{}]\tFresh: [" + green("{}%") + "]\tStale: [{}%]\tInvalid [{}%]";
			} else {
				printOut = "[Link Analyzer] - Total: [{}]\tFresh: [" + red("{}%") + "]\tStale: [{}%]\tInvalid [{}%]";
			}
			logger.trace(printOut, found, String.format("%.2f", freshPercentage), String.format("%.2f", stalePercentage), String.format("%.2f", invalidPercentage));
		}
		if(worthContinuing && !nextUrls.isEmpty()) {
			return Optional.of(nextUrls);
		} else {
			return Optional.empty();
		}
	}

	private boolean wasNotRedirectedExternally(Document document) {
		return StringUtils.containsIgnoreCase(document.location(), currentDataSourceUrl.getHost());
	}

	private boolean shouldVisit(String link) {
		if(StringUtils.isBlank(link)) {
			return false;
		}
		if(link.contains("#")) {
			return false;
		}
		if(insensitiveVisitedUrls.contains(link)) {
			return false;
		}
		for(String keyword : keywordsToAvoid()) {
			if(StringUtils.containsIgnoreCase(link, keyword)) {
				return false;
			}
		}
		// parse the link as a URL. we can do this at this point because we are using abs:href to select links
		URL linkUrl;
		try {
			linkUrl = new URL(link);
		} catch(MalformedURLException e) {
			return false;
		}
		// if the link is external, don't visit
		if(!StringUtils.equalsIgnoreCase(ScoutServices.getDomain(linkUrl), ScoutServices.getDomain(currentDataSourceUrl))) {
			return false;
		}
		// if the link contains a product identifier we have already found
		for(String identifier : new ArrayList<>(insensitiveProductIds)) {
			if(StringUtils.containsIgnoreCase(link, identifier)) {
				return false;
			}
		}
		// if the link's path contains an extension, make sure it's a web page extension
		String path = linkUrl.getPath();
		if(StringUtils.isNotEmpty(path) && path.contains(".")) {
			String ext = path.substring(path.lastIndexOf("."));
			if(ext.isEmpty()) {
				// url can't end with a .
				return false;
			}
			ext = ext.substring(1);
			if(ext.length() >= 3 && ext.length() <= 4) {
				if(!validWebPageExtensions().contains(ext)) {
					return false;
				}
			}
		}
		// if the path contains another website somewhere, no go
		if(path.contains("http") || path.contains("www.") || path.contains(".com") || path.contains(".net") || path.contains(".org")) {
			return false;
		}
		return robotRules.isAllowed(linkUrl.toString());
	}

	private static Set<String> validWebPageExtensions() {
		return new HashSet<>(Arrays.asList("asp", "aspx", "axd", "asx", "asmx", "ashx", "cfm", "yaws", "html", "htm", "xhtml", "jhtml",
				"jsp", "jspx", "php", "php4", "php3", "phtml", "rhtml", "shtml", "xml"));
	}

	protected String nodePre() {
		return "[" + purple("Node " + (nodeId < 10 ? "0" : "") + nodeId) + "] - ";
	}
}
