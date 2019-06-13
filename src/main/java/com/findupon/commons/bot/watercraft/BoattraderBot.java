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

import com.google.common.collect.Lists;
import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.HullType;
import com.findupon.commons.entity.product.attribute.WatercraftType;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class BoattraderBot extends ListingWatercraftBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "sitemaps/www-xmlsitemap.xml",
				s -> StringUtils.containsIgnoreCase(s, "listing"), s -> true));

		for(List<String> urls : Lists.partition(new ArrayList<>(baseUrls), urlWriteThreshold)) {
			listingDataSourceUrlService.bulkInsert(getDataSource(), urls, false);
		}
		return new LinkedHashSet<>();
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		logger.warn(logPre() + "who is calling this. base urls should have came back empty");
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
		watercraft.setListingId(listingId);

		for(Element tr : document.select("tbody > tr")) {
			Element th = tr.selectFirst("th");
			Element td = tr.selectFirst("td");

			if(th == null || td == null || !th.hasText() || !tr.hasText()) {
				continue;
			}

			String key = StringUtils.lowerCase(th.ownText());
			String value = td.ownText();

			switch(key) {
				case "class":
					watercraft.setWatercraftType(WatercraftType.of(value));
					break;
				case "year":
					if(StringUtils.isNumeric(value)) {
						Integer year = Integer.valueOf(value);
						if(year <= 2020 && year >= 1850) {
							watercraft.setYear(year);
						}
					}
					break;
				case "make":
					watercraft.setManufacturer(value);
					break;
				case "length":
					watercraft.setLength(AttributeOperations.reduceToInteger(value));
				case "hull material":
					watercraft.setHullType(HullType.of(value));
					break;
				case "fuel type":
					if(StringUtils.containsIgnoreCase(value, "lpg")) {
						watercraft.setFuel(Fuel.GASOLINE);
					} else {
						watercraft.setFuel(Fuel.of(value));
					}
					break;
				case "location":
					if(value.contains(",")) {
						String cityStr = StringUtils.substringBefore(value, ",");
						State stateStr = State.valueOfAbbreviation(StringUtils.substringAfter(value, ","));
						AddressOperations.getAddressFromCityState(cityStr, stateStr).ifPresent(a -> a.setWatercraftAddress(watercraft));
					} else {
						logger.debug(logPre() + "No location found from [{}]", value);
					}
					break;
			}
		}

		JsoupUtils.selectFirst(document, "#seller-name")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactName);

		JsoupUtils.selectFirst(document, "div.contact")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactPhone);

		JsoupUtils.selectFirst(document, ".street-address")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactAddress);

		JsoupUtils.selectFirst(document, ".postal-code")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactZipcode);

		JsoupUtils.selectFirst(document, "span.bd-model")
				.map(Element::ownText)
				.ifPresent(watercraft::setModel);

		JsoupUtils.selectFirst(document, ".bd-price.contact-toggle")
				.map(Element::ownText)
				.filter(s -> s.contains("$"))
				.map(AutoParsingOperations::parsePrice)
				.ifPresent(watercraft::setPrice);

		JsoupUtils.selectFirst(document, "span[class=locality]")
				.map(Element::ownText)
				.map(StringUtils::trimToNull)
				.ifPresent(locality -> watercraft.setContactCity(StringUtils.substringBefore(locality, ",")));

		JsoupUtils.selectFirst(document, "abbr[class=region]")
				.map(JsoupUtils.defaultFilteringTextMapper)
				.map(State::valueOfAbbreviation)
				.ifPresent(watercraft::setContactState);

		JsoupUtils.selectFirst(document, "title")
				.map(JsoupUtils.defaultFilteringTextMapper)
				.map(StringUtils::lowerCase)
				.ifPresent(s -> {
					if(s.startsWith("used")) {
						watercraft.setUsed(true);
					} else if(s.startsWith("new")) {
						watercraft.setUsed(false);
					}
				});

		JsoupUtils.selectFirst(document, "#go-to-website")
				.map(e -> e.absUrl("href"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setContactUrl);

		JsoupUtils.selectFirst(document, "meta[name=og:image]")
				.filter(e -> e.hasAttr("content"))
				.map(e -> e.absUrl("content"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setMainImageUrl);

		printWatercraftSpecs(watercraft);

		return BuiltProduct.success(watercraft);
	}
}
