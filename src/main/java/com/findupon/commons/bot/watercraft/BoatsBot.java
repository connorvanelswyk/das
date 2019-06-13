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
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.HullType;
import com.findupon.commons.entity.product.attribute.WatercraftType;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class BoatsBot extends ListingWatercraftBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		String url = "http://www.boats.com/sitemap-search-models.xml";
		Document document = download(url);
		if(document == null) {
			logger.error(logPre() + "Could not connect to root sitemap [{}]", url);
			return baseUrls;
		}
		Arrays.stream(document.text().split(" "))
				.map(StringUtils::trimToNull)
				.filter(UrlValidator.getInstance()::isValid)
				.forEach(baseUrls::add);
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		standardProductUrlGatherer(baseUrls, nodeId, 64, "page",
				document -> document.select("a").stream()
						.filter(e -> e.hasAttr("data-reporting-click-product-id") && e.hasAttr("href"))
						.map(e -> e.absUrl("href"))
						.collect(Collectors.toList()),
				document -> document.selectFirst("a[class=next]") != null);
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

		for(Element tr : document.select("tbody > tr")) {
			Element th = tr.selectFirst("th");
			Element td = tr.selectFirst("td");

			if(th == null || td == null || !th.hasText() || !tr.hasText()) {
				continue;
			}

			String key = StringUtils.lowerCase(th.ownText());
			String value = StringUtils.lowerCase(td.ownText());

			switch(key) {
				case "type":
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
				case "condition":
					if(value.startsWith("used")) {
						watercraft.setUsed(true);
					} else if(value.startsWith("new")) {
						watercraft.setUsed(false);
					}
					break;
				case "make":
					watercraft.setManufacturer(value);
					break;
				case "model":
					watercraft.setModel(value);
					break;
				case "length":
					watercraft.setLength(AttributeOperations.reduceToInteger(value));
					break;
				case "hull material":
					watercraft.setHullType(HullType.of(value));
					break;
				case "fuel type":
					if(StringUtils.lowerCase(value).contains("lpg")) {
						watercraft.setFuel(Fuel.GASOLINE);
					} else {
						watercraft.setFuel(Fuel.of(value));
					}
					break;
				case "location":
					if(value.contains(",")) {
						String cityStr = StringUtils.substringBefore(value, ",");
						Optional<Address> address = AddressOperations.getAddressFromCity(cityStr);
						address.ifPresent(a -> {
							watercraft.setAddress(a.getLine());
							watercraft.setLatitude(a.getLatitude());
							watercraft.setLongitude(a.getLongitude());
						});
					} else {
						logger.debug(logPre() + "No location found for watercraft [{}]", watercraft.getUrl());
					}
					break;
			}
		}

		JsoupUtils.selectFirst(document, "h3")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactName);

		JsoupUtils.selectFirst(document, "span.long")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactCountry);

		JsoupUtils.selectFirst(document, "div.street")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactAddress);

		JsoupUtils.selectFirst(document, "span.number")
				.map(Element::ownText)
				.ifPresent(watercraft::setContactPhone);

		JsoupUtils.selectFirst(document, "span[class=abbrev]")
				.map(JsoupUtils.defaultFilteringTextMapper)
				.map(State::valueOfAbbreviation)
				.ifPresent(watercraft::setContactState);

		JsoupUtils.selectFirst(document, "#go-to-website")
				.map(e -> e.absUrl("href"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setContactUrl);

		JsoupUtils.selectFirst(document, "meta[property=og:image]")
				.filter(e -> e.hasAttr("content"))
				.map(e -> e.absUrl("content"))
				.filter(UrlValidator.getInstance()::isValid)
				.ifPresent(watercraft::setMainImageUrl);

		JsoupUtils.selectFirst(document, "span[class=price]")
				.map(Element::ownText)
				.filter(s -> s.contains("$"))
				.map(ListingWatercraftBot::parseAndValidatePrice)
				.ifPresent(watercraft::setPrice);

		String address = JsoupUtils.selectFirst(document, "div[class=city]").map(Element::ownText).orElse(null);
		AddressOperations.getAddress(address).ifPresent(a -> {
			watercraft.setContactCity(a.getCity());
			watercraft.setContactState(a.getState());
			watercraft.setContactAddress(a.getLine());
			watercraft.setContactZipcode(a.getZip());
		});

//		printWatercraftSpecs(watercraft);

		return BuiltProduct.success(watercraft);
	}
}
