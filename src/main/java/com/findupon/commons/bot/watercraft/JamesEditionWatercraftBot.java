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

package com.findupon.commons.bot.watercraft;

import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.WatercraftType;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.BoolUtils;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.IntStream;

import static com.findupon.commons.utilities.JsoupUtils.firstChild;


public class JamesEditionWatercraftBot extends ListingWatercraftBot {

	private final Set<String> resultPageUrls = new HashSet<>();

	@Override
	public Set<String> retrieveBaseUrls() {
		Document document = download(getDataSource().getUrl() + "brands");
		sleep();
		if(document == null) {
			return new LinkedHashSet<>();
		}

		document.select("li.brand").stream()
				.filter(e -> !e.children().isEmpty())
				.map(e -> e.child(0).attr("abs:href"))
				.filter(s -> Objects.equals(StringUtils.substringBetween(s.substring(s.indexOf("jamesedition")), "/", "/"), "yachts"))
				.peek(s -> logger.debug(logPre() + "Base URL added [{}]", s))
				.forEach(baseUrls::add);

		logger.info(logPre() + "Base URL collection size [{}]", baseUrls.size());
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		String baseUrl = baseUrls.get(0);
		logger.info(logPre() + "Started run on [{}]", baseUrl);

		setResultPageUrls(baseUrl);
		resultPageUrls.forEach(this::retrieveByPage);
		logger.info(logPre() + "Watercraft URLs size [{}]", productUrls.size());

		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
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

			resultPageUrls.add(url);

			String resultsText = resultsDiv.child(0).text();
			resultsText = StringUtils.substringBefore(resultsText, " ");

			int results = Integer.valueOf(resultsText);
			int pages = results / 24;

			IntStream.rangeClosed(2, pages).forEach(i -> resultPageUrls.add(url + "?page=" + i));
		}
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
				.filter(e -> !e.children().isEmpty())
				.map(e -> e.child(0).attr("abs:href"))
				.filter(StringUtils::isNotEmpty)
				.forEach(productUrls::add);
	}

	@Override
	public BuiltProduct buildProduct(String url) {

		String listingId = ScoutServices.getUniqueIdentifierFromUrl(url);

		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Watercraft watercraft = new Watercraft(url);
		watercraft.setUrl(url);
		watercraft.setListingId(listingId);

		for(Element ul : document.select("ul.details-list > li")) {
			Element n = ul.selectFirst("span.name");
			Element v = ul.selectFirst("span.value");

			if(n == null || v == null || !n.hasText() || !ul.hasText()) {
				continue;
			}

			String key = StringUtils.lowerCase(n.ownText().replace(":", ""));
			String value = StringUtils.lowerCase(v.ownText());

			switch(key) {
				case "year":
					if(StringUtils.isNumeric(value)) {
						Integer year = Integer.valueOf(value);
						if(year <= 2020 && year >= 1850) {
							watercraft.setYear(year);
						}
					}
					break;
				case "length":
					if(value.contains("ft")) {
						String feet = StringUtils.substringBefore(value, "ft");
						if(NumberUtils.isDigits(feet.trim())) {
							watercraft.setLength(AttributeOperations.reduceToInteger(feet));
						}
					}
					break;
				case "location":
					watercraft.setCountry(value);
					break;
				case "boat type":
					if(value.contains("motor")) {
						watercraft.setWatercraftType(WatercraftType.POWER);
					} else if(value.contains("sail")) {
						watercraft.setWatercraftType(WatercraftType.SAIL);
					}
			}
		}

		try {
			Element seller = firstChild(document.select("div.seller"));
			if(seller != null) {
				if(watercraft.getAddress() == null || watercraft.getZip() == null) {
					Element address = firstChild(seller.getElementsByTag("address"));
					if(address != null) {
						String addressText = address.text();
						if(addressText.contains("Phone:")) {
							addressText = addressText.replaceAll("Phone:.*", "").trim();
						}
						AddressOperations.getAddress(addressText).ifPresent(a -> {
							watercraft.setContactAddress(a.getLine());
							watercraft.setContactZipcode(a.getZip());
							watercraft.setContactState(a.getState());
							watercraft.setContactCity(a.getCity());
						});
					}
				}
				Element sellerNameElement = firstChild(seller.getElementsByTag("h3"));
				if(sellerNameElement != null) {
					watercraft.setContactName(sellerNameElement.text());
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
									watercraft.setContactUrl(ScoutServices.formUrlFromString(dealerUrl, true));
								}
							}
						}
					}
				}
			}

			if(document.select("ol > li").size() == 4) {
				Elements li = document.select("ol > li");
				watercraft.setManufacturer(li.get(2).text());
				watercraft.setModel(li.get(3).text());
			} else if(document.select("ol > li").size() == 3) {
				Elements li = document.select("ol > li");
				watercraft.setManufacturer(li.get(2).text());
			}

			String price = document.select("div.currency-converter > div.converted-currency > div.USD").text();
			if(StringUtils.contains(price, "$")) {
				price = price.replaceAll("[^\\d.]", "");
				watercraft.setPrice(ListingWatercraftBot.parseAndValidatePrice(price));
			}

			JsoupUtils.selectFirst(document, "a.phone")
					.map(Element::ownText)
					.ifPresent(watercraft::setContactPhone);

			JsoupUtils.selectFirst(document, "#pictures > div.primary > a")
					.filter(e -> e.hasAttr("href"))
					.map(e -> e.absUrl("href"))
					.filter(UrlValidator.getInstance()::isValid)
					.ifPresent(watercraft::setMainImageUrl);

		} catch(Exception e) {
			logger.warn(logPre() + "Error building manual attributes for URL [{}]", url, e);
		}

		return BuiltProduct.success(watercraft);
	}
}
