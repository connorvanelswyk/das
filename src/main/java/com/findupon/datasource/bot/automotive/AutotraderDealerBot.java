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

package com.findupon.datasource.bot.automotive;
//
//import com.findupon.commons.utilities.JsoupUtils;
//import com.findupon.datasource.bot.AbstractDealerRetrievalBot;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.math.NumberUtils;
//import org.jsoup.HttpStatusException;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.springframework.stereotype.Component;
//
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.TimeUnit;
//
//
//@Component
//@Deprecated // we obtain dealer URLs from the listing bot so this is no longer needed
//public class AutotraderDealerBot extends AbstractDealerRetrievalBot {
//	private final String BASE_URL = "https://www.autotrader.com/";
//	private final int CONNECT_WAIT_SECONDS = 2;
//	private Set<Integer> dealerIds = new HashSet<>();
//
//
//	@Override
//	public void obtainDatasourceUrls() {
//		String zip = "01001";
//		final int MAX_ZIP = 99950;
//		final int ZIP_DELTA = 500;
//
//		while(Integer.valueOf(zip) <= MAX_ZIP) {
//			int firstRecord = 0; // increments of 10, they don't allow result sizes > 10
//			Document document = downloadAndWait(getUrl(zip, firstRecord));
//
//			while(document != null && document.html().contains("Next \u00BB")) {
//				for(Element dealerAnchor : document.select("a[class=dealer-name]")) {
//					// get the dealer ID and add it to the set
//					String href = dealerAnchor.attr("href");
//					if(StringUtils.isNotEmpty(href)) {
//						String dealerIdStr = StringUtils.substringAfterLast(StringUtils.substringBeforeLast(href, "/"), "/");
//						if(StringUtils.isNotEmpty(dealerIdStr) && NumberUtils.isDigits(dealerIdStr)) {
//							Integer dealerId = Integer.valueOf(dealerIdStr);
//							if(!dealerIds.contains(dealerId)) {
//								dealerIds.add(dealerId);
//							} else {
//								logger.warn("Duplicate dealer found [{}]", href);
//								continue;
//							}
//						} else {
//							logger.warn("Could not parse dealer ID from href [{}]", href);
//						}
//						String potentialUrl = BASE_URL + (href.charAt(0) == '/' ? href.substring(1) : href);
//						findAndAddDealer(potentialUrl);
//					}
//				}
//				document = downloadAndWait(getUrl(zip, (firstRecord += 10)));
//			}
//			// increase the zip
//			int zipVal = Integer.valueOf(zip) + ZIP_DELTA;
//			zip = (zipVal < 10_000 ? "0" : "") + Integer.toString(zipVal);
//		}
//	}
//
//	@Override
//	protected String getSourceName() {
//		return "AutotraderDealer";
//	}
//
//	private void findAndAddDealer(String url) {
//		Document document = downloadAndWait(url);
//		if(document != null && StringUtils.containsIgnoreCase(document.html(), "Visit Our Website")) {
//			Element dealerSiteAnchor = JsoupUtils.firstChild(document.select("a[target=_siteLink][class=atcui-icon-link]"));
//			String href;
//			if(dealerSiteAnchor != null && StringUtils.isNotEmpty((href = dealerSiteAnchor.attr("href")))) {
//				addDealerUrl(href);
//			}
//		}
//	}
//
//	private Document downloadAndWait(String url) {
//		Document document;
//		try {
//			document = connector.download(url, true);
//			Thread.sleep(TimeUnit.SECONDS.toMillis(CONNECT_WAIT_SECONDS));
//		} catch(HttpStatusException e) {
//			logger.error("HTTP status [{}] returned by Autotrader for url [{}]!", e.getStatusCode(), url, e);
//			document = null;
//		} catch(InterruptedException e) {
//			Thread.currentThread().interrupt();
//			logger.error("Thread interrupt!", e);
//			document = null;
//		}
//		return document;
//	}
//
//	private String getUrl(String zip, int firstRecord) {
//		return "https://www.autotrader.com/car-dealers/" +
//				"Foo+BR-" + zip +
//				"?filterName=pagination" +
//				"&firstRecord=" + firstRecord +
//				"&searchRadius=200" +
//				"&sortBy=ownernameASC";
//	}
//}
