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
import com.findupon.commons.entity.building.Address;
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
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


public class YachtWorldBot extends ListingWatercraftBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "prod_sitemap_index.xml",
				s -> StringUtils.containsIgnoreCase(s, "boatdetail"), s -> true));

		List<String> temp = baseUrls.stream().map(s -> StringUtils.replace(s, "#", "%23")).collect(Collectors.toList());
		baseUrls.clear();
		baseUrls.addAll(temp);

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
		watercraft.setUrl(url);
		watercraft.setListingId(listingId);

		Elements dls = new Elements();
		for(Element detail : document.select("div[class*=boatdetails]")) {
			dls.addAll(detail.getElementsByAttributeValueContaining("class", "column").select("dl"));
		}

		dls.select("dt").forEach(dt -> {
			String key = StringUtils.lowerCase(dt.ownText().replace(":", ""));
			String value = StringUtils.lowerCase(dt.nextElementSibling().ownText());
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
					watercraft.setLength(AttributeOperations.reduceToInteger(value));
					break;
				case "engine/fuel type":
					String fuel = StringUtils.substringAfter(value, "/");
					if(StringUtils.isNotEmpty(fuel.trim())) {
						watercraft.setFuel(Fuel.of(fuel));
					}
					break;
				case "located in":
					if(value.contains(",")) {
						String cityStr = StringUtils.substringBefore(value, ",");
						Optional<Address> address = AddressOperations.getAddressFromCity(cityStr);
						address.ifPresent(a -> {
							watercraft.setAddress(a.getLine());
							watercraft.setLatitude(a.getLatitude());
							watercraft.setLongitude(a.getLongitude());
						});
					}
					break;
				case "hull material":
					watercraft.setHullType(HullType.of(value));
					break;
				case "current price":
					Integer price = AttributeOperations.reduceToInteger(value);
					if(price != null && price > 500 && price < 100_000_000) {
						watercraft.setPrice(BigDecimal.valueOf(price));
					}
					break;
			}
		});

		String js = StringUtils.substringBetween(document.toString(), "digitalDataBuilder.init()", "document.getElementById(\"searchbar\")");
		if(StringUtils.isNotEmpty(js)) {

			if(js.contains("manufacturer:")) {
				String manufacturer = StringUtils.substringBetween(js, "manufacturer: '", "',");
				if(StringUtils.isNotEmpty(manufacturer)) {
					watercraft.setManufacturer(manufacturer);
				}
			}

			if(js.contains("condition:")) {
				String condition = StringUtils.substringBetween(js, "condition: '", "',").toLowerCase();
				if(condition.startsWith("used")) {
					watercraft.setUsed(true);
				} else if(condition.startsWith("new")) {
					watercraft.setUsed(false);
				}
			}

			if(js.contains("model:")) {
				String model = StringUtils.substringBetween(js, "model: '", "',").toLowerCase();
				if(StringUtils.isNotEmpty(model)) {
					watercraft.setModel(model);
				}
			}

			if(js.contains("country:")) {
				String country = StringUtils.substringBetween(js, "country: '", "',").toLowerCase();
				if(StringUtils.isNotEmpty(country)) {
					watercraft.setCountry(country);
				}
			}

			if(js.contains("productClass: digitalDataBuilderYWMapper.convertClass(\"(")) {
				String type = StringUtils.substringBetween(js, "productClass: digitalDataBuilderYWMapper.convertClass(\"(", ")").toLowerCase();
				if(StringUtils.isNotEmpty(type)) {
					watercraft.setWatercraftType(WatercraftType.of(type));
				}
			}
		}

		JsoupUtils.selectFirst(document, "meta[property=og:image]")
				.filter(e -> e.hasAttr("content"))
				.map(e -> e.absUrl("content"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setMainImageUrl);

		JsoupUtils.selectFirst(document, "a[href*=tel:]")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactPhone);

		JsoupUtils.selectFirst(document, "div.header > h6")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactName);


		printWatercraftSpecs(watercraft);

		return BuiltProduct.success(watercraft);
	}
}