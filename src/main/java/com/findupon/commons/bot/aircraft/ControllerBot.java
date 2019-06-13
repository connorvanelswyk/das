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

package com.findupon.commons.bot.aircraft;

import com.google.common.collect.Lists;
import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.building.PriceOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.commons.utilities.RegexConstants;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class ControllerBot extends AbstractAircraftBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collect(getDataSource(), "sitemaps/Detail-COM-Sitemap1.xml.gz"));
		for(List<String> urls : Lists.partition(new ArrayList<>(baseUrls), urlWriteThreshold)) {
			listingDataSourceUrlService.bulkInsert(getDataSource(), urls, false);
		}
		return new LinkedHashSet<>();
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		// if you return empty in retrieveBaseUrls after inserting the product urls, the runner will skip this method and go straight to building products
		logger.warn(logPre() + "Base urls should have came back empty");
	}

	/**
	 * Controller.com detail page parser:
	 *
	 * <ul>
	 * <li>product term, listing id, year, manufacturer, and model is in url</li>
	 * <li>price found by dollar symbol</li>
	 * <li>img found in tag containing alt value equalling YEAR MAKE MODEL at Controller.com</li>
	 * </ul>
	 *
	 * todo - java 8-ify
	 * @param url, e.g. https://www.controller.com/listings/aircraft/for-sale/25257909/2012-learjet-45xr
	 * @return {@link BuiltProduct}
	 */
	@Override
	public BuiltProduct buildProduct(String url) {
		String listingId = StringUtils.substringBetween(url, "for-sale/", "/");

		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		Aircraft aircraft = new Aircraft(url);
		aircraft.setListingId(listingId);

		String yearVal = StringUtils.substringBetween(url, listingId + "/", "-");
		if(yearVal != null) {
			aircraft.setYear(AttributeOperations.reduceToInteger(yearVal));
		}

		document.select("div.information-box.print-row-2col > div.row")
				.forEach(e -> setAttribute(aircraft, e.text()));

		if(aircraft.getMake() != null) {
			aircraft.setMainImageUrl(getMainImgUrl(document, yearVal + " " + aircraft.getMake()));
		}

		aircraft.setPrice(getPrice(document));

		Elements imgs = document.select("div.mc-item.mc-img.mc-selected > img");
		if(CollectionUtils.isNotEmpty(imgs)) {
			String mainImgUrl = imgs.size() > 1
					? imgs.get(1).attr("data-fullscreen")
					: imgs.get(0).attr("data-src");
			aircraft.setMainImageUrl(mainImgUrl);
		}

		return BuiltProduct.success(aircraft);
	}

	private String getMainImgUrl(Document document, String str) {
		Elements elements = document.select("img");
		if(CollectionUtils.isNotEmpty(elements)) {
			for(Element e : elements) {
				if(e.hasAttr("alt")) {
					String alt = e.attr("alt");
					if(StringUtils.containsIgnoreCase(str, alt)) {
						return e.attr("content");
					}
				}
			}
		}
		return null;
	}

	private BigDecimal getPrice(Document document) {
		String priceValue = null;
		Elements elements = document.select("span.price-value");
		if(elements != null && !elements.isEmpty()) {
			Element e = elements.first();
			priceValue = e.hasText() ? e.text() : null;
		}
		if(priceValue != null && priceValue.matches(RegexConstants.PRICE_MATCH)) {
			priceValue = PriceOperations.priceStringCleaner(priceValue);
			if(StringUtils.isNumeric(priceValue)) {
				return new BigDecimal(priceValue);
			}
		}
		return null;
	}
}