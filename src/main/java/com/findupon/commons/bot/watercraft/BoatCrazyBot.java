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
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.HullType;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;


public class BoatCrazyBot extends ListingWatercraftBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "sitemaps/listings"));
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
		String listingId = StringUtils.substringAfterLast(url, "-id");
		if(!StringUtils.isNumeric(listingId)) {
			logger.warn(logPre() + "Could not determine listing ID for URL [{}]", url);
			return BuiltProduct.removed(url);
		}

		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Watercraft watercraft = new Watercraft(url);
		watercraft.setUrl(url);
		watercraft.setListingId(listingId);

		for(Element detail : document.select("ul.item-details.list-inline > li")) {
			String key = detail.getElementsByAttributeValueContaining("class", "title").text().toLowerCase();
			String value = detail.ownText().toLowerCase();
			switch(key) {
				case "length":
					if(value.contains("'")) {
						watercraft.setLength(AttributeOperations.reduceToInteger(StringUtils.substringBefore(value, "'")));
					}
					break;
				case "condition":
					if(value.startsWith("used")) {
						watercraft.setUsed(true);
					} else if(value.startsWith("new")) {
						watercraft.setUsed(false);
					}
					break;
				case "location":
					if(value.contains(",")) {
						String cityStr = StringUtils.substringBefore(value, ",");
						Optional<Address> address = AddressOperations.getAddressFromCity(cityStr);
						address.ifPresent(a -> {
							watercraft.setLatitude(a.getLatitude());
							watercraft.setLongitude(a.getLongitude());
							watercraft.setAddress(a.getLine());
						});
					}
					break;
				case "fuel":
					watercraft.setFuel(Fuel.of(value));
					break;
				case "hull type":
					watercraft.setHullType(HullType.of(value));
					break;
			}
		}

		JsoupUtils.selectFirst(document, "meta[name=twitter:image]")
				.filter(e -> e.hasAttr("content"))
				.map(e -> e.absUrl("content"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setMainImageUrl);

		JsoupUtils.selectFirst(document, "div.price.col-lg-4")
				.map(Element::ownText)
				.filter(s -> s.contains("$"))
				.map(ListingWatercraftBot::parseAndValidatePrice)
				.ifPresent(watercraft::setPrice);

		JsoupUtils.selectFirst(document, "div.seller-info > p")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactName);

		String urlAttr = document.select("a.pinterest").attr("href");
		if(StringUtils.isNotEmpty(urlAttr) && urlAttr.contains("&description=") && urlAttr.contains("+on+BoatCrazy")) {
			String urlManufacturerAndModel = StringUtils.substringBetween(urlAttr, "&description=", "+on+BoatCrazy");
			String manufacturer = StringUtils.substringBetween(urlManufacturerAndModel, "+", "+");
			String model = StringUtils.substringAfterLast(urlManufacturerAndModel, "+");
			if(StringUtils.isNotEmpty(manufacturer) && StringUtils.isNotEmpty(model)) {
				watercraft.setManufacturer(manufacturer);
				watercraft.setModel(model);
			}
		}

		String yearTitle = document.select("h1.col-lg-8").text();
		if(NumberUtils.isDigits(yearTitle.substring(0, 4))) {
			int year = Integer.parseInt(yearTitle.substring(0, 4));
			if(year <= 2020 && year >= 1850) {
				watercraft.setYear(year);
			}
		}

		printWatercraftSpecs(watercraft);

		return BuiltProduct.success(watercraft);
	}
}
