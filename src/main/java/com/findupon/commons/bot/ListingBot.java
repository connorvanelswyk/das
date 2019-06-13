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

package com.findupon.commons.bot;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.findupon.commons.building.ProductUtils;
import com.findupon.commons.dao.ProductDao;
import com.findupon.commons.entity.datasource.AssetType;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.entity.datasource.DataSourceType;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.ProductList;
import com.findupon.commons.netops.ConnectionAgent;
import com.findupon.commons.netops.entity.AgentResponse;
import com.findupon.commons.repository.datasource.DataSourceRepo;
import com.findupon.commons.repository.datasource.ListingDataSourceUrlService;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.TimeUtils;
import com.findupon.utilities.PropertyLoader;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Component
@NotThreadSafe
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class ListingBot<P extends Product & Serializable> {
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected long nodeId = 0;
	protected final int urlWriteThreshold = 16384;
	private final int minDownloadTimeMillis = 16;
	private final int invalidWaitTimeMillis = 8192;

	private final String currentClassName = getClass().getSimpleName();
	private String currentDownloadingUrl;
	private DataSource currentDataSource = null;

	protected final List<P> products = new ProductList<>();
	protected final List<String> productUrls = new ArrayList<>();
	protected final Set<String> baseUrls = new LinkedHashSet<>();

	@Autowired protected ProductUtils productUtils;
	@Autowired protected ListingDataSourceUrlService listingDataSourceUrlService;
	@Autowired private DataSourceRepo dataSourceRepo;


	public abstract Set<String> retrieveBaseUrls();

	public abstract void gatherProductUrls(List<String> baseUrls, long nodeId);

	public abstract BuiltProduct buildProduct(String url); // maybe have a "noop" product type that just invokes the standard builder?

	// TODO: abstract index only method


	// do not change access from public. needed for reflection.
	@SuppressWarnings("unchecked")
	public void buildAndPersist(List<String> urls, long nodeId) {
		this.nodeId = nodeId;
		logger.info(logPre() + "Building and persisting [{}] URLs", urls.size());
		Stopwatch stopwatch = Stopwatch.createStarted();

		for(String url : urls) {
			if(sleep()) {
				return;
			}
			logger.debug(logPre() + "Building product [{}]", url);

			BuiltProduct product = buildProduct(url);
			if(product == null) {
				logger.error(logPre() + "Null built product not allowed! [{}]", url);
				continue;
			}
			if(product.isMarkedForRemoval()) {
				if(product.getListingId() == null) {
					logger.error(logPre() + "Marked for removal product with no listing ID not allowed! [{}]", url);
					continue;
				}
				logger.debug(logPre() + "Product came back marked for removal the builder [{}]", url);

				List<P> allExisting = productUtils.findExistingProducts(getDataSource(), product.getListingId());
				Optional<P> existing = Optional.empty();
				if(!allExisting.isEmpty()) {
					existing = Optional.of(allExisting.get(0));
					if(allExisting.size() > 1) {
						List<Long> idsToRemove = IntStream.range(1, allExisting.size())
								.mapToObj(i -> allExisting.get(i).getId())
								.collect(Collectors.toList());

						logger.warn(logPre() + "More than one product found by same listing ID [{}]. Removing [{}] dupes.", product.getListingId(), idsToRemove.size());
						productUtils.removeProductsById(product.getProduct().getClass(), idsToRemove);
					}
				}
				existing.ifPresent(p -> productUtils.removeProductAndRefreshAggregates(p));
			} else {
				validateSetMetaAndAdd((P)product.getProduct());
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				persistAndClear();
			}
		}
		persistAndClear();
		logger.info(logPre() + "Build & persist for [{}] URLs completed in [{}]", urls.size(), TimeUtils.format(stopwatch));
	}

	protected Document download(String url) {
		currentDownloadingUrl = url;
		long startTimeMillis = System.currentTimeMillis();
		AgentResponse agentResponse = ConnectionAgent.INSTANCE.download(url, getDataSource(), nodeId);
		long downloadTimeMillis = System.currentTimeMillis() - startTimeMillis;

		switch(agentResponse.getDecision().getAction()) {
			case ABORT_CRAWL:
				Thread.currentThread().interrupt();
				logger.error(logPre() + "Decision made to abort crawl! {}", agentResponse.getDecision().getMessage());
				break;
			case PROCEED:
				if(downloadTimeMillis <= minDownloadTimeMillis) {
					logger.warn(logPre() + ConsoleColors.yellow("Download time taken [{}] ms less than [{}] ms allowed. Waiting [{}] ms before proceeding. URL [{}]"),
							downloadTimeMillis, minDownloadTimeMillis, invalidWaitTimeMillis, url);
					logger.debug(logPre() + "Raw HTML: \n\n{}\n\n", agentResponse.getDocument() != null ? agentResponse.getDocument().html() : "null document");
					try {
						Thread.sleep(invalidWaitTimeMillis);
					} catch(InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
		}
		return agentResponse.getDocument();
	}

	protected Document tryTwice(String url) {
		if(Thread.currentThread().isInterrupted()) {
			return null;
		}
		Document document = download(url);
		if(document == null) {
			if(sleep()) {
				return null;
			}
			document = download(url);
		}
		return document;
	}

	protected boolean sleep() {
		Long crawlRate = getDataSource().getCrawlRate();
		if(crawlRate == null) {
			crawlRate = 1L;
		}
		if(crawlRate > 0) {
			double deviation = (double)crawlRate / 10;
			long waitTime = (long)(ThreadLocalRandom.current().nextDouble(crawlRate, crawlRate + deviation));
			try {
				Thread.sleep(waitTime);
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.debug(logPre() + "Thread has been interrupted. Abort abort!");
				return true;
			}
		}
		return false;
	}

	protected void validateSetMetaAndAdd(P product) {
		if(product == null) {
			return;
		}
		product.setSourceUrl(getDataSource().getUrl());
		product.setDataSourceId(getDataSource().getId());
		if(productUtils.basicInvalidator(product)) {
			return;
		}
		if(StringUtils.isBlank(product.getListingId())) {
			logger.debug(logPre() + "Missing listing ID for listing product, not persisting [{}]", product.getUrl());
			return;
		}
		if(products.stream().anyMatch(p -> StringUtils.equalsIgnoreCase(product.getListingId(), p.getListingId()))) {
			logger.debug(logPre() + "Product listing ID already added [{}]", product.getListingId());
			return;
		}
		productUtils.mergeExistingProductAndRefreshAggregates(product);
		products.add(product);
	}

	protected void persistAndClear() {
		if(!products.isEmpty() && !Thread.currentThread().isInterrupted()) {
			productUtils.saveAll(products);
			products.clear();
		}
	}

	protected DataSource getDataSource() {
		if(currentDataSource == null) {
			currentDataSource = dataSourceRepo.findByBotClass(getClass().getCanonicalName());
		}
		if(currentDataSource == null) {
			if(!PropertyLoader.getBoolean("production") && currentDownloadingUrl != null) {
				automatedDatasourceInsert();
			} else {
				throw new UnsupportedOperationException("Could not find listing datasource in the database. The data_source either " +
						"does not exist or the bot_class is incorrect. It should equal " + getClass().getCanonicalName() + "\n");
			}
		}
		return currentDataSource;
	}

	protected String logPre() {
		return "[" + ConsoleColors.purple("Node " + (nodeId < 10 ? "0" : "") + nodeId) + "] - [" + currentClassName + "] - ";
	}

	/**
	 * @param baseUrl                  The url to start indexing from.
	 * @param pageQueryParam           The next page url query param, i.e. "page" to produce "?page=x" or "&page=x"
	 * @param portletCssSelector       The css to select all product portlets.
	 * @param noMatchingResultsMessage Many listing bots will still display related results if your search query is too specific,
	 *                                 this will be your site-specific message to short the builder.
	 * @param recursiveUrlReducer      Are there too many results per page? If so, recurse and reduce. Limited to once.
	 *                                 Example: a site will show 5000 results but their serp only traverses to 1000. Return a
	 *                                 new set of urls that have narrowed down the search so each one returns less than 1000.
	 * @param portletBuilder           Builds an product from the portlet selected.
	 * @param hasNextPage              Test the element for a next page condition and enforce a max page value.
	 * @return The number of built products.
	 */
	protected int abstractSerpBuilder(String baseUrl, String pageQueryParam, String portletCssSelector,
	                                  String noMatchingResultsMessage, final int maxPageNumber,
	                                  BiFunction<Document, String, Set<String>> recursiveUrlReducer,
	                                  Function<Element, P> portletBuilder,
	                                  Predicate<Document> hasNextPage) {

		AtomicInteger builtProduct = new AtomicInteger();
		AtomicInteger pageNumber = new AtomicInteger();
		Document firstPage = download(baseUrl);
		Document currentPage;

		if(firstPage == null) {
			logger.warn(logPre() + "Null document returned at first page, returning. Serp URL [{}]", baseUrl);
			return builtProduct.get();
		}
		if(StringUtils.containsIgnoreCase(firstPage.html(), noMatchingResultsMessage)) {
			logger.debug(logPre() + "No matching results message found at first page URL [{}]", firstPage.location());
			return builtProduct.get();
		}
		Set<String> additionalUrls = recursiveUrlReducer.apply(firstPage, baseUrl);
		if(!additionalUrls.isEmpty()) {
			LongAdder recursiveBuilt = new LongAdder();
			additionalUrls.forEach(u ->
					recursiveBuilt.add(abstractSerpBuilder(baseUrl, pageQueryParam, portletCssSelector, noMatchingResultsMessage,
							maxPageNumber, (r, s) -> new HashSet<>(), portletBuilder, hasNextPage)));
			logger.debug(logPre() + "Recursive built [{}] from base url [{}]", recursiveBuilt, baseUrl);
			return recursiveBuilt.intValue();
		}
		do {
			pageNumber.incrementAndGet();
			if(firstPage != null) {
				currentPage = firstPage;
				firstPage = null;
			} else {
				String pageUrl = ScoutServices.setQueryParamValue(baseUrl, pageQueryParam, pageNumber.toString());
				currentPage = download(pageUrl);
			}
			if(sleep()) {
				break;
			}
			if(currentPage == null) {
				logger.warn(logPre() + "Null document returned at page [{}], returning. Serp URL [{}]", pageNumber.get(), baseUrl);
				return builtProduct.get();
			}
			logger.debug(logPre() + "Building from serp at [{}]", currentPage.location());
			if(StringUtils.containsIgnoreCase(currentPage.html(), noMatchingResultsMessage)) {
				logger.debug(logPre() + "No matching results message found at serp URL [{}]", currentPage.location());
				return builtProduct.get();
			}
			for(Element serpElement : currentPage.select(portletCssSelector)) {
				try {
					P product = portletBuilder.apply(serpElement);
					if(product != null) {
						validateSetMetaAndAdd(product);
						builtProduct.incrementAndGet();
					}
				} catch(Exception e) {
					logger.error(logPre() + "Error building product from serp URL [{}]", baseUrl, e);
				}
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				logger.debug(logPre() + "Persisting [{}] {}s to the db (over threshold)", currentClassName, products.size());
				persistAndClear();
			}
		} while(hasNextPage.test(currentPage) && pageNumber.get() <= maxPageNumber);
		return builtProduct.get();
	}

	protected void standardProductUrlGatherer(List<String> baseUrls, long nodeId, final int maxPageNumber, String pageQueryParam,
	                                          Function<Element, List<String>> urlRetriever,
	                                          Predicate<Element> hasNextPage) {
		this.nodeId = nodeId;
		for(String baseUrl : baseUrls) {
			AtomicInteger pageNumber = new AtomicInteger();
			Document document;
			do {
				pageNumber.incrementAndGet();
				baseUrl = ScoutServices.setQueryParamValue(baseUrl, pageQueryParam, pageNumber.toString());
				document = download(baseUrl);
				if(sleep()) {
					return;
				}
				if(document == null) {
					logger.warn(logPre() + "Null document retrieving product URLs at [{]]", baseUrl);
					break;
				}
				List<String> urls = urlRetriever.apply(document);
				if(urls == null) {
					urls = new ArrayList<>();
				}
				urls.stream().distinct().forEach(productUrls::add);

			} while(hasNextPage.test(document) && pageNumber.get() <= maxPageNumber);
		}
		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
	}

	/**
	 * Used for testing
	 */
	public void deployAll() {
		if(PropertyLoader.getBoolean("production")) {
			logger.error(logPre() + ConsoleColors.red("Deploy all should not be run on a production profile"));
			return;
		}
		logger.info(logPre() + "Full deploy all run started");
		retrieveBaseUrls();
		logger.info(logPre() + "Base URL collection size [{}]", baseUrls.size());
		int indexDelSize = ObjectUtils.defaultIfNull(getDataSource().getIndexDelegationSize(), 8);
		Lists.partition(new ArrayList<>(baseUrls), indexDelSize).forEach(b -> gatherProductUrls(b, 0));
		if(!getDataSource().getIndexOnly()) {
			Lists.partition(productUrls, 128).forEach(b -> buildAndPersist(b, 0));
		}
		logger.info(logPre() + "Full run completed");
	}

	private void automatedDatasourceInsert() {
		if(PropertyLoader.getBoolean("production")) {
			return;
		}
		try(Scanner scanner = new Scanner(System.in)) {
			System.err.println("\n\nListing data source not found in the database");
			System.err.println("It was either removed from the weekly dev sync, has the wrong bot_class, or never inserted.");
			System.err.print("\nWould you like to automatically create a new one? (y/n): ");
			String input = scanner.next().trim().toLowerCase();
			if("y".equals(input) || "yes".equals(input)) {
				System.err.print("\nIs this datasource index_only? (y/n/idk): ");
				input = scanner.next().toLowerCase().trim();
				boolean indexOnly = "y".equals(input) || "yes".equals(input);
				AtomicInteger i = new AtomicInteger();
				Map<Integer, AssetType> selectionMap = Arrays.stream(AssetType.values()).collect(Collectors.toMap(k -> i.getAndIncrement(), v -> v));
				System.err.printf("\nEnter the asset type: %n%n%s%n%n",
						selectionMap.entrySet().stream().map(e -> e.getKey() + " - " + e.getValue().name()).collect(Collectors.joining("\n")));
				input = scanner.next().trim();
				if(!StringUtils.isNumeric(input)) {
					System.err.println("\nIncorrect asset type selection");
					System.exit(0);
				}
				AssetType assetType = selectionMap.get(Integer.parseInt(input));
				if(assetType == null) {
					System.err.println("\nIncorrect asset type selection");
					System.exit(0);
				}
				String rootUrl = ScoutServices.parseByProtocolAndHost(currentDownloadingUrl);
				System.err.printf("%nConnecting to root URL [%s] to resolve any redirects...%n", rootUrl);
				Document temp = ConnectionAgent.INSTANCE.stealthDownload(rootUrl).getDocument();
				if(temp == null) {
					System.err.println("Could not connect to root URL");
					System.err.println("Please try again or manually insert your data source using /sql/scripts/DataSourceInsert.sql");
					System.exit(0);
				}
				rootUrl = ScoutServices.parseByProtocolAndHost(temp.location());
				DataSource dataSource = DataSource.createNew(rootUrl, assetType, DataSourceType.LISTING);
				dataSource.setIndexOnly(indexOnly);
				dataSource.setBotClass(getClass().getCanonicalName());
				currentDataSource = dataSourceRepo.save(dataSource);
				System.err.println("Datasource creation success! Continuing what you were trying to run.\n");
			} else {
				System.err.println("\nPlease manually insert your data source using /sql/scripts/DataSourceInsert.sql");
				System.exit(0);
			}
		}
	}
}
