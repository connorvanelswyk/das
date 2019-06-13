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

package com.findupon.commons.bot.automotive;

import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.BoolUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.findupon.commons.utilities.JsoupUtils.firstChild;


public class JamesEditionBot extends ListingAutomobileBot {
	private final Set<String> resultPageUrls = new HashSet<>();


	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		String baseUrl = baseUrls.get(0);
		logger.info(logPre() + "Started run on [{}]", baseUrl);

		setResultPageUrls(baseUrl);
		resultPageUrls.forEach(this::retrieveByPage);
		logger.info(logPre() + "Car URLs size [{}]", productUrls.size());

		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
	}

	@Override
	public Set<String> retrieveBaseUrls() {
		Document document = download(getDataSource().getUrl() + "brands");
		sleep();
		if(document == null) {
			return new LinkedHashSet<>();
		}
		Set<String> validMakes = attributeMatcher.getFullAttributeMap().entrySet().stream()
				.map(e -> e.getValue().getAttribute())
				.collect(Collectors.toSet());

		document.select("li.brand").stream()
				.filter(e -> !e.children().isEmpty())
				.map(e -> e.child(0).attr("abs:href"))
				.filter(s -> Objects.equals(StringUtils.substringBetween(s.substring(s.indexOf("jamesedition")), "/", "/"), "cars"))
				.filter(s -> validMakes.stream()
						.anyMatch(m -> {
									String x = s.substring(s.lastIndexOf("/") + "/".length());
									return !"ac".equalsIgnoreCase(x) && ("vw".equalsIgnoreCase(x) ||
											StringUtils.equalsIgnoreCase(m, x.replace("_", " ")) ||
											StringUtils.equalsIgnoreCase(m, x.replace("_", "-")));
								}
						))
				.peek(s -> logger.debug(logPre() + "Base URL added [{}]", s))
				.forEach(baseUrls::add);

		logger.info(logPre() + "Base URL collection size [{}]", baseUrls.size());
		return baseUrls;
	}

	private void retrieveByPage(String url) {
		Document document = download(url);
		sleep();
		if(document == null) {
			logger.debug(logPre() + "Document came back null trying to retrieve product URLs by page");
			return;
		}
		document.select("div.listing").stream()
				.filter(e -> !BoolUtils.isDead(e))
				.filter(e -> StringUtils.containsIgnoreCase(e.html(), "flag-icon-us"))
				.filter(e -> !e.children().isEmpty())
				.map(e -> e.child(0).attr("abs:href"))
				.filter(StringUtils::isNotEmpty)
				.forEach(productUrls::add);
	}

	private void setResultPageUrls(String url) {
		Document document = download(url);
		sleep();
		if(document == null) {
			return;
		}
		Element resultsDiv = document.getElementById("results");

		if(resultsDiv == null || resultsDiv.children().isEmpty() || !resultsDiv.child(0).hasText()) {
			logger.debug(logPre() + "No result pages for brand URL [{}]", url);
		} else {

			resultPageUrls.add(url); // implicitly ?page=1

			String resultsText = resultsDiv.child(0).text();
			resultsText = StringUtils.substringBefore(resultsText, " ");

			int results = Integer.valueOf(resultsText);
			int pages = results / 24;

			IntStream.rangeClosed(2, pages).forEach(i -> resultPageUrls.add(url + "?page=" + i));
		}
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		String listingId = ScoutServices.getUniqueIdentifierFromUrl(url);
		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		// quick redirect check to see if the car has been removed
		if(!StringUtils.containsIgnoreCase(document.html(), "Send Inquiry")) {
			return BuiltProduct.removed(listingId);
		}

		Automobile automobile = automotiveGatherer.buildProduct(document);
		if(automobile == null) {
			logger.debug(logPre() + "Automobile came back null from generic builder [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		/* Manual Attributes */
		try {
			// Dealer and address if not picked up generically
			Element seller = firstChild(document.select("div[class=seller]"));
			if(seller != null) {
				if(automobile.getAddress() == null || automobile.getZip() == null) {
					Element address = firstChild(seller.getElementsByTag("address"));
					if(address != null) {
						String addressText = address.text();
						if(addressText.contains("Phone:")) {
							addressText = addressText.replaceAll("Phone:.*", "").trim();
						}
						AddressOperations.getAddress(addressText).ifPresent(a -> a.setAutomobileAddress(automobile));
					}
				}
				Element sellerNameElement = firstChild(seller.getElementsByTag("h3"));
				if(sellerNameElement != null) {
					automobile.setDealerName(sellerNameElement.text());
				}
				Element linkElement = firstChild(seller.select("a[href]"));
				if(linkElement != null) {
					String link = linkElement.attr("abs:href");
					if(ScoutServices.getUrlFromString(url, false) != null) {
						Document dealerDocument = download(link);
						sleep();
						if(dealerDocument != null) {
							Element dealerSiteElement = firstChild(dealerDocument.select("a[data-track=office_website_clicked]"));
							if(dealerSiteElement != null && dealerSiteElement.hasAttr("href")) {
								String dealerUrl = dealerSiteElement.attr("href");
								if(StringUtils.isNotBlank(dealerUrl)) {
									automobile.setDealerUrl(ScoutServices.formUrlFromString(dealerUrl, true));
								}
							}
						}
					}
				}
			}
			// Images
			Elements pictures = document.select("#pictures");
			if(BoolUtils.isDead(pictures)) {
				logger.debug(logPre() + "No images for [{}]", url);
			} else {
				for(String className : Arrays.asList("primary", "main", "additional", "hidden")) {
					for(Element div : pictures.select("div." + className)) {
						Element a = firstChild(div.select("a"));
						if(a != null && StringUtils.isNotEmpty(a.attr("href"))) {
							automobile.setMainImageUrl(a.attr("href"));
							break;
						}
					}
					if(automobile.getMainImageUrl() != null) {
						break;
					}
				}
			}
		} catch(Exception e) {
			logger.warn(logPre() + "Error building manual attributes for URL [{}]", url, e);
		}
		return BuiltProduct.success(automobile);
	}
}
