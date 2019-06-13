//import com.gargoylesoftware.htmlunit.WebClient;
//import com.gargoylesoftware.htmlunit.html.DomElement;
//import com.gargoylesoftware.htmlunit.html.HtmlPage;
//import com.google.common.base.Stopwatch;
//import com.findupon.commons.building.AutoParsingOperations;
//import com.findupon.commons.utilities.NumUtils;
//import com.findupon.commons.utilities.VinDecoder;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.text.similarity.JaroWinklerDistance;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import org.springframework.beans.factory.config.ConfigurableBeanFactory;
//import org.springframework.context.annotation.Scope;
//import org.springframework.stereotype.Component;
//
//import javax.persistence.Column;
//import java.math.BigDecimal;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//
//@Component
//@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
//public class CarGurusBot extends ListingAutomobileBot {
//
//	private static final List<String> columnNamesToIgnore = Arrays.asList("id", "creation_date", "created_by",
//			"modified_date", "modified_by", "url", "dealer_url", "dealer_name", "source_url", "source_name");
//
//
//	@Override
//	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
//		this.nodeId = nodeId;
//		retrieveAllProductUrls(baseUrl);
//		logger.info("[CarGurusBot] - Last batch car URLs size [{}]", productUrls.size());
//		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls);
//	}
//
//	@Override
//	public void buildAndPersist(List<String> urls) {
//		productUrls.clear();
//		productUrls.addAll(urls);
//		buildAndPersistEntities();
//	}
//
//	@Override
//	public Automobile buildProduct(String url) {
//
//		String pageSource = "";
//
//		Document document = Jsoup.parse(pageSource);
//
//		String listingId = StringUtils.substringAfter(url, "#listing=");
//
//		Element input = document.getElementById("inventoryListingId");
//		if(input == null) {
//			return null;
//		} else if(Objects.equals(input.val(), listingId)) {
//			return buildProduct(document, url);
//		} else {
//			return buildProduct(url);
//		}
//	}
//
//	private Automobile buildProduct(Document document, String url) {
//
//		Automobile automobile = new Automobile(url);
//		automobile.setListingId(StringUtils.substringAfter(url, "#listing="));
//
////		try {
////			automobile.setDescription(document.getElementById("#description").toString());
////		} catch(Exception e) {
////			logger.error("[CarGurusBot] - While setting description for [{}]: ", url, e);
////		}
//
//		try {
//			document.select("tr")
//					.stream()
//					.filter(tr -> tr.children().size() == 2)
//					.filter(tr -> tr.child(1).select("select").isEmpty())
//					.filter(tr -> tr.child(0).text().length() < 99)
//					.filter(tr -> tr.child(1).text().length() < 99)
//					.forEach(tr ->
//
//							Stream.of(automobile.getClass().getDeclaredFields())
//									.filter(f -> f.getAnnotation(Column.class) != null)
//									.filter(f -> !columnNamesToIgnore.contains(f.getAnnotation(Column.class).name()))
//									.forEach(f -> {
//
//										try {
//
//											f.setAccessible(true);
//
//											if(f.get(automobile) == null) {
//
//												String c = f.getAnnotation(Column.class).name();
//												String x = StringUtils.replace(c, "_", " ");
//												String k = StringUtils.removeEnd(tr.child(0).text(), ":").toLowerCase();
//												String v = tr.child(1).text();
//
//												if(Objects.equals(k, "gas mileage")) {
//													tr.child(1).childNodes().forEach(childNode -> {
//														String[] vals = StringUtils.split(childNode.toString());
//														if(vals != null && vals.length > 0) {
//															String mpg = vals[0];
//															if(NumUtils.isDigits(mpg)) {
//																String type = vals[2];
//																if(StringUtils.equalsIgnoreCase(type, "City")) {
//																	automobile.setMpgCity(Integer.valueOf(mpg));
//																} else {
//																	automobile.setMpgHighway(Integer.valueOf(mpg));
//																}
//															}
//														}
//													});
//												} else if(new JaroWinklerDistance().apply(x, k) < .75 && !k.contains(x)) {
//													// ain't nobody got time for that
//												} else if(f.getType().equals(Integer.class)) {
//													v = StringUtils.remove(v, ",");
//													v = StringUtils.remove(v, "miles").trim();
//													if(NumUtils.isDigits(v)) {
//														f.set(automobile, Integer.valueOf(v));
//													}
//												} else if(f.getType().equals(BigDecimal.class)) {
//													BigDecimal price = AutoParsingOperations.parsePrice(v);
//													if(price != null) {
//														f.set(automobile, price);
//													}
//												} else {
//													f.set(automobile, v);
//												}
//											}
//										} catch(IllegalAccessException e) {
//											logger.error("[CarGurusBot] - While setting attributes for [{}] ", url, e);
//										} finally {
//											f.setAccessible(false);
//										}
//									})
//					);
//		} catch(Exception e) {
//			logger.error("While setting attributes for [{}]: ", url, e.getMessage());
//		}
//
//		try {
//			Elements imageElements = document.select("div.cg-listing-thumbnailPic");
//			if(imageElements == null || imageElements.isEmpty()) {
//				logger.debug("[CarGurusBot] - No images for [{}]", url);
//			} else {
//				Element imageElement = imageElements.get(0);
//				String onclick = StringUtils.substringAfter(imageElement.toString(), "onclick=");
//				String imgUrl = StringUtils.substringBetween(onclick, "https://", ".jpeg');");
//				automobile.setMainImageUrl("https://" + imgUrl + ".jpeg");
////				automobile.setAutomobileImages(new ArrayList<>());
////				imageElements.forEach(e -> {
////					String onclick = StringUtils.substringAfter(e.toString(), "onclick=");
////					String imgUrl = StringUtils.substringBetween(onclick, "https://", ".jpeg');");
////					AutomobileImage automobileImage = new AutomobileImage();
////					automobileImage.setUrl("https://" + imgUrl + ".jpeg");
////					automobileImage.setAutomobile(automobile);
////					automobile.getAutomobileImages().add(automobileImage);
////				});
//			}
//		} catch(Exception e) {
//			logger.error("While setting images for [{}]: ", url, e.getMessage());
//		}
//
//		try {
//
////		The dealership name can still be retrieved by selecting the 'itemReviewed' in the html.
////		Example:
////		Go to the following URL: https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?sourceContext=carGurusHomePage_false_0&entitySelectingHelper.selectedEntity=&zip=32708#listing=191024201
////		Inspect the html and search for dealerInfo-header -> No results are found.
////		Search for itemReviewed -> Results are returned and they are all displaying the dealership name.
//
//			Elements dealerInfoElements = document.select("div.dealerInfo-header");
//			if(dealerInfoElements == null || dealerInfoElements.isEmpty()) {
//				logger.debug("[CarGurusBot] - Dealer info is not available");
//			} else {
//				Element titleElement = dealerInfoElements.get(0);
//				if(titleElement.children().isEmpty()) {
//					logger.debug("[CarGurusBot] - Dealer name is not available");
//				} else {
//					automobile.setDealerName(titleElement.child(0).text());
//				}
//				if(dealerInfoElements.size() < 2) {
//					logger.debug("[CarGurusBot] - Dealer url is not available");
//				} else {
//					automobile.setDealerUrl(dealerInfoElements.get(1).text());
//				}
//			}
//		} catch(Exception e) {
//			logger.error("[CarGurusBot] - While setting dealer info for [{}]: ", url, e.getMessage());
//		}
//
//		try {
//			Element yearElement = document.getElementById("_quoteLogForm_formOpenedData_year");
//			if(yearElement != null && yearElement.hasText() && NumUtils.isDigits(yearElement.text())) {
//				automobile.setYear(Integer.valueOf(yearElement.attr("value")));
//			}
//			Element makeElement = document.getElementById("_quoteLogForm_formOpenedData_make");
//			if(makeElement != null && StringUtils.isNotBlank(makeElement.attr("value"))) {
//				automobile.setMake(makeElement.attr("value"));
//			}
//			Element modelElement = document.getElementById("_quoteLogForm_formOpenedData_model");
//			if(modelElement != null && StringUtils.isNotBlank(modelElement.attr("value"))) {
//				automobile.setModel(modelElement.attr("value"));
//			}
//			Element trimElement = document.getElementById("_quoteLogForm_formOpenedData_trim");
//			if(trimElement != null && StringUtils.isNotBlank(trimElement.attr("value"))) {
//				automobile.setTrim(trimElement.attr("value"));
//			}
//		/*
//			JIC, if we missed data and this page is using a json object
//		 */
//			if(automobile.getYear() == null) {
//				automobile.setYear(VinDecoder.getYear(document, automobile.getVin()));
//			}
//			if(StringUtils.isBlank(automobile.getMake())) {
//				automobile.setMake(StringUtils.substringBetween(document.toString(), "['Make'] = \"", "\";"));
//			}
//			if(StringUtils.isBlank(automobile.getModel())) {
//				automobile.setModel(StringUtils.substringBetween(document.toString(), "['Model'] = \"", "\";"));
//			}
//			if(StringUtils.isBlank(automobile.getBodyStyle())) {
//				automobile.setBodyStyle(StringUtils.substringBetween(document.toString(), "['BodyStyle']=\"", "\";"));
//			}
//		} catch(Exception e) {
//			logger.error("[CarGurusBot] - While setting critical info for [{}]: ", url, e.getMessage());
//		}
//
//		try {
//			document.select(".span3.block-icon-offset").forEach(addressElement -> {
//				addressElement.childNodes().forEach(addressElementNode -> {
//					if(!addressElementNode.nodeName().equals("a") && automobile.getAddress() == null) {
//						AutoParsingOperations.setAutomobileAddress(automobile, addressElement.text());
//					}
//				});
//			});
//		} catch(Exception e) {
//			logger.error("[CarGurusBot] - While setting address info for [{}]: ", url, e.getMessage());
//		}
//
//		return automobile;
//	}
//
//	@Override
//	protected void retrieveProductUrlsByPage(Document document) {
//
//		logger.info("Retrieving product urls by page");
//
//		Elements listingDivs = document.select("div[id^=listing_]");
//		if(listingDivs == null || listingDivs.isEmpty()) {
//			logger.error("[CarGurusBot] - Could not SERP listing results", new RuntimeException());
//		} else {
//			Integer totalResultSize = getResultSize(document);
//			if(totalResultSize == null) {
//				logger.error("[CarGurusBot] - Could not retrieve total results size", new RuntimeException());
//			} else {
//
//				int pageSize = totalResultSize / 15;
//				if(pageSize % 15 != 0) {
//					pageSize++;
//				}
//
//				int page = 1;
//				while(page <= pageSize) {
//
//					if(page > 1) {
//						String tempUrl = StringUtils.substringBeforeLast(document.baseUri(), "#resultsPage=");
//						document = connector.download(tempUrl + "#resultsPage=" + page);
//					}
//
//					String url = StringUtils.substringBeforeLast(document.baseUri(), "#resultsPage=");
//					productUrls.addAll(document.select("div[id^=listing_]")
//							.stream()
//							.map(e -> url + "#listing=" + StringUtils.remove(e.id(), "listing_"))
//							.collect(Collectors.toSet()));
//
//					if(!productUrls.isEmpty()) {
//						Automobile automobile = buildProduct(productUrls.iterator().next());
//						logger.info("car\n{}", automobile);
//					}
//
//					page++;
//				}
//			}
//		}
//	}
//
//	@Override
//	public LinkedHashSet<String> retrieveBaseUrls() {
//
//		Document document = connector.download("https://www.cargurus.com/Cars/sitemap.html");
//
//		Element div = firstChild(document.select(".col-md-9"));
//
//		if(div != null) {
//
//			div.children().forEach(child -> {
//
//				if(!child.tagName().equals("h2")) {
//
//					Elements anchorTags = child.select("a");
//
//					if(anchorTags != null) {
//
//						anchorTags.forEach(a -> {
//
//							String href = a.attr("href");
//							String[] arr = StringUtils.splitByWholeSeparator(href, "-Overview-");
//
//							if(arr != null && arr.length > 1 && StringUtils.isNotBlank(arr[1])) {
//
//								String baseUrl
//										= "https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action"
//										+ "?sourceContext=carGurusHomePage_false_0"
//										+ "&newSearchFromOverviewPage=true"
//										+ "&inventorySearchWidgetType=AUTO"
//										+ "&entitySelectingHelper.selectedEntity=" + arr[1]
//										+ "&zip=32814"
//										+ "&distance=50000"
//										+ "&searchChanged=true"
//										+ "&modelChanged=false"
//										+ "&filtersModified=true"
//										+ "&sortType=PRICE"
//										+ "&sortDirection=DESC";
//
//								baseUrls.add(baseUrl);
//							}
//						});
//					}
//				}
//			});
//		}
//
//		return baseUrls;
//	}
//
//	// todo make more efficient
//	private void retrieveAllProductUrls(String baseUrl) {
//
//		String model = getModelFromBaseUrl(baseUrl);
//
//		Stopwatch stopwatch = Stopwatch.createStarted();
//		logger.info("[CarGurusBot] - Retrieving all detail page URLs for model [{}]", model);
//
////		for(int year = Calendar.getInstance().get(Calendar.YEAR) + 2; year > 1946; year--) {
//		for(int year = Calendar.getInstance().get(Calendar.YEAR) - 14; year > 1946; year--) {
//
//			Stopwatch yearTimer = Stopwatch.createStarted();
//			boolean rangeNextPass = true;
//			int range = 20_000;
//			int minPrice = 0;
//			int maxPrice;
//
//			while((maxPrice = minPrice + range) < 10_000_000) {
//
//				logger.info("base url {}", baseUrl);
//
//
//				Integer totalResultsSize = getResultSize(baseUrl);
//				if(totalResultsSize == null) {
////					Elements noListingsDivs = document.select("div#noListingResultsMessage");
////					if(noListingsDivs == null) {
////						logger.error("[CarGurusBot] - Could not retrieve total results size", new RuntimeException());
////					}
//					totalResultsSize = 0;
//				}
//
//				logger.debug("[CarGurusBot] - Result size [{}] for Year [{}] Make [{}] Model [{}] Price range [{} - {}] ",
//						totalResultsSize, year, model, minPrice, maxPrice);
//
//				if(totalResultsSize > 2000) {
//					range /= ((totalResultsSize + 2000) / 2000);
//					rangeNextPass = false;
//					continue;
//
//				} else if(totalResultsSize <= 0) {
//					minPrice += range;
//					range *= 2;
//					continue;
//
//				} else if(totalResultsSize > 500) {
//					rangeNextPass = false;
//				}
//
//				minPrice += range;
//				if(rangeNextPass) {
//					range *= Math.ceil(Math.PI / Math.log10(totalResultsSize * 100) + 1);
//				}
//				rangeNextPass = true;
//				Document document = connector.download(baseUrl);
//				retrieveProductUrlsByPage(document);
//			}
//			logger.debug("[CarGurusBot] - Indexing complete for year [{}] model [{}] in [{}] seconds",
//					year, model, yearTimer.elapsed(TimeUnit.SECONDS));
//		}
//		logger.info("[CarGurusBot] - Indexing complete for [{}] in [{}] seconds\n", model, stopwatch.elapsed(TimeUnit.SECONDS));
//	}
//
//	private static String createSerpUrl(String modelCode, int year, int minPrice, int maxPrice) {
//		return
//				"https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?sourceContext=" +
//						"&newSearchFromOverviewPage=true" +
//						"&inventorySearchWidgetType=ADVANCED" +
//						"&zip=32814" +
//						"&distance=50000" +
//						"&advancedSearchAutoEntities%5B0%5D.selectedEntity=" + modelCode +
//						"&advancedSearchAutoEntities%5B1%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B2%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B3%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B4%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B5%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B6%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B7%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B8%5D.selectedEntity=" +
//						"&advancedSearchAutoEntities%5B9%5D.selectedEntity=" +
//						"&startYear=" + year +
//						"&endYear=" + year +
//						"&__multiselect_bodyTypeGroupIds=" +
//						"&__multiselect_fuelTypes=" +
//						"&minPrice=" + minPrice +
//						"&maxPrice=" + maxPrice +
//						"&minMileage=" +
//						"&maxMileage=" +
//						"&transmission=ANY" +
//						"&__multiselect_installedOptionIds=" +
//						"&modelChanged=undefined" +
//						"&filtersModified=true" +
//						"&sortType=undefined" +
//						"&sortDirection=undefined";
//	}
//
//	private static String getModelFromBaseUrl(String baseUrl) {
//		return StringUtils.substringBetween(baseUrl, "&entitySelectingHelper.selectedEntity=", "&");
//	}
//
//	private Integer getResultSize(String url) {
//		try(WebClient webClient = connector.getWebClient()) {
//			HtmlPage htmlPage = webClient.getPage(url);
//			System.out.println("Get ready to wait 30 seconds...");
//			webClient.waitForBackgroundJavaScript(10 * 1000); /* will wait JavaScript to execute up to 30s */
//			webClient.getCurrentWindow().getJobManager().shutdown();
//			htmlPage = (HtmlPage)webClient.getCurrentWindow().getEnclosedPage();
//			System.out.println(htmlPage.asXml());
//
//			DomElement htmlElement = htmlPage.getElementById("displayedListingsCount");
//
//			if(htmlElement == null) {
//				logger.info("Displayed listing count element is null!");
//				return null;
//			} else if(htmlElement.getChildElementCount() < 2
//					|| !NumUtils.isDigits(htmlElement.getChildNodes().get(1).asText())) {
//				return null;
//			} else {
//				return Integer.valueOf(htmlElement.getChildNodes().get(1).asText());
//			}
//		} catch(Exception e) {
//			e.printStackTrace();
//			return null;
//		}
//	}
//
//
//	private HtmlPage getHtmlPage(WebClient webClient, String url) {
//		try {
//			HtmlPage page = webClient.getPage(url);
//			if(hasCars(page)) {
//				return page;
//			} else {
//				return getHtmlPage(webClient, page);
//			}
//		} catch(Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
//
//	private HtmlPage getHtmlPage(WebClient webClient, HtmlPage page) {
//		if(hasCars(page)) {
//			return page;
//		} else {
//			webClient.waitForBackgroundJavaScript(1000);
//			HtmlPage enclosedPage = (HtmlPage)webClient.getCurrentWindow().getEnclosedPage();
//			if(hasCars(enclosedPage)) {
//				return enclosedPage;
//			} else {
//				return getHtmlPage(webClient, page);
//			}
//		}
//	}
//
//	private boolean hasCars(HtmlPage htmlPage) {
//		Elements carDivs = Jsoup.parse(htmlPage.asXml()).select("div[id^=listing_]");
//		boolean hasCars = carDivs != null && !carDivs.isEmpty();
//		if(hasCars) {
//			System.out.println("car divs: " + carDivs);
//		} else {
//			System.out.println("no cars.");
//		}
//		return hasCars;
//	}
//
//	private void waitOutLoading(WebClient webClient, HtmlPage page) {
//
//	}
//
//	private Integer getResultSize(Document document) {
//		Element element = document.getElementById("displayedListingsCount");
//		if(element == null || element.children().size() < 2 || !NumUtils.isDigits(element.child(1).text())) {
//			return null;
//		} else {
//			return Integer.valueOf(element.child(1).text());
//		}
//	}
//}