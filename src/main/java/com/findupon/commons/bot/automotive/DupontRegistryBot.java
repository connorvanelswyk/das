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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.findupon.commons.utilities.JsoupUtils.firstChild;
import static com.findupon.commons.utilities.JsoupUtils.firstChildOwnText;


public class DupontRegistryBot extends ListingAutomobileBot {

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		String baseUrl = baseUrls.get(0);
		this.nodeId = nodeId;
		logger.info(logPre() + "Started run on [{}]", baseUrl);
		setDetailPageUrls(baseUrl);
		logger.info(logPre() + "Car URLs size [{}]", productUrls.size());
		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
	}

	@Override
	public Set<String> retrieveBaseUrls() {
		// for this bot the baseUrls are actually the result page urls

		String url = getDataSource().getUrl() + "autos/";
		Document document = download(url);
		sleep();
		if(document == null) {
			return new LinkedHashSet<>();
		}
		Element makeSelect = document.getElementById("mainContentPlaceholder_topMakeSelect");
		Set<String> validMakes = attributeMatcher.getFullAttributeMap().entrySet().stream()
				.map(e -> e.getValue().getAttribute())
				.collect(Collectors.toSet());

		makeSelect.getElementsByTag("option").stream()
				.map(Element::text)
				.filter(s -> validMakes.stream()
						.anyMatch(m -> StringUtils.containsIgnoreCase(s, m)))
				.map(s -> StringUtils.replace(s, " ", "--").toLowerCase())
				.map(s -> url + "results/" + s + "/all/all/refine?distance=0&pagenum=1&perpage=100&sort=price_desc&inv=false")
				.peek(s -> logger.debug(logPre() + "Base URL added [{}]", s))
				.forEach(baseUrls::add);

		logger.info("[DupontRegistryBot] - Base URL collection size [{}]", baseUrls.size());
		return baseUrls;
	}

	private void setDetailPageUrls(String url) {
		Document document;
		do {
			document = download(url);
			sleep();
			if(document == null) {
				logger.debug(logPre() + "Document came back null getting detail page URLs [{}]", url);
				return;
			}
			document.select("span.car_title").stream()
					.map(e -> firstChild(e.getElementsByTag("a")))
					.filter(e -> e.hasAttr("href"))
					.map(e -> e.attr("abs:href"))
					.filter(StringUtils::isNotEmpty)
					.forEach(productUrls::add);

			String currentPageIndex = StringUtils.substringBetween(url, "&pagenum=", "&perpage=");
			String futurePageIndex = String.valueOf(Integer.valueOf(currentPageIndex) + 1);
			url = StringUtils.replaceOnce(url, "&pagenum=" + currentPageIndex, "&pagenum=" + futurePageIndex);

		} while(firstChild(document.select("a[class=next]")) != null);
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
		if(StringUtils.containsIgnoreCase(document.html(), "cars within") || StringUtils.containsIgnoreCase(document.html(), "no longer available")) {
			return BuiltProduct.removed(listingId);
		}

		Automobile automobile = automotiveGatherer.buildProduct(document);
		if(automobile == null) {
			logger.debug(logPre() + "Automobile came back null from generic builder [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		/* Manual Attributes */
		try {
			// Dealer
			Element dealerName = firstChild(document.select("h4[id=nm]"));
			if(dealerName != null && StringUtils.isNotBlank(dealerName.text())) {
				automobile.setDealerName(dealerName.text().trim());
			}
			Element dealerAnchor = firstChild(document.select("a[id=mainContentPlaceholder_SimilarListings_DealerContactInfo_DealerSiteLink]"));
			if(dealerAnchor != null) {
				String dealerUrl = dealerAnchor.attr("href");
				if(StringUtils.isNotEmpty(dealerUrl) && dealerUrl.contains("&url=")) {
					if((dealerUrl = ScoutServices.formUrlFromString(dealerUrl.substring(dealerUrl.indexOf("&url=") + "&url=".length()), true)) != null) {
						automobile.setDealerUrl(dealerUrl);
					}
				}
			}
			// Address if not picked up generically
			if(automobile.getAddress() == null || automobile.getZip() == null) {
				String addressLine = firstChildOwnText(document.selectFirst("span#mainContentPlaceholder_sectionVehicleLocation"));
				if(StringUtils.isNotEmpty(addressLine)) {
					AddressOperations.getAddress(addressLine).ifPresent(a -> a.setAutomobileAddress(automobile));
				}
			}
			// Images
			Element mainSlide = firstChild(document.select("div.slide"));
			if(mainSlide != null) {
				Element image = firstChild(mainSlide.getElementsByTag("img"));
				if(image != null && StringUtils.isNotEmpty(image.attr("src"))) {
					String src = "http://www.dupontregistry.com" + image.attr("src");
					src = StringUtils.replacePattern(src, "height=\\d*", "height=100%");
					src = StringUtils.replacePattern(src, "width=\\d*", "width=100%");
					automobile.setMainImageUrl(src);
				}
			}
		} catch(Exception e) {
			logger.warn(logPre() + "Error building manual attributes for URL [{}]", url, e);
		}
		return BuiltProduct.success(automobile);
	}
}
