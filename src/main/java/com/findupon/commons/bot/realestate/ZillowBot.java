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

package com.findupon.commons.bot.realestate;

import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.building.PriceOperations;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.ProductTerm;
import com.findupon.commons.entity.product.attribute.RealEstateType;
import com.findupon.commons.entity.product.realestate.RealEstate;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class ZillowBot extends ListingRealEstateBot {

	private final String noMatchingResultsMessage = "No matching results";

	@Override
	public Set<String> retrieveBaseUrls() {
		return baseUrlsByZip(zip -> "homes/" + zip + "_rb/1_p/");
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		for(String baseUrl : baseUrls) {
			buildByPage(baseUrl, Collections.singletonList(noMatchingResultsMessage), this::buildRealEstates,
					(document, pageNum) -> pageNum >= 20);
		}
	}

	private List<RealEstate> buildRealEstates(Document document) {
		List<RealEstate> realEstates = new ArrayList<>();

		for(Element article : document.select("ul[class=photo-cards] > li > article")) {
			RealEstate realEstate = new RealEstate();

			realEstate.setListingId(article.attr("data-zpid"));
			if(realEstate.getListingId() == null) {
				continue;
			}
			realEstate.setUrl(getDataSource().getUrl() + "homedetails/" + realEstate.getListingId() + "_zpid/");
			realEstate.setProductTerm(ProductTerm.of(article.attr("data-sgapt")));

			String stateStr = StringUtils.trimToNull(article.select("span[itemprop=addressRegion]").text());
			String cityStr = StringUtils.trimToNull(article.select("span[itemprop=addressLocality]").text());
			String zip = StringUtils.trimToNull(article.select("span[itemprop=postalCode]").text());
			String street = StringUtils.trimToNull(article.select("span[itemprop=streetAddress]").text());

			if(stateStr == null || cityStr == null) {
				logger.trace(logPre() + "No city/ state info");
				continue;
			}
			String addressLine = street + " " + cityStr + ", " + stateStr + " " + zip;
			addressLine = StringUtils.replaceAll(addressLine, " +", " ");
			if(StringUtils.containsIgnoreCase(addressLine, "undisclosed")) {
				logger.trace(logPre() + "Undisclosed address, not persisting");
				continue;
			}
			realEstate.setState(State.valueOfAbbreviation(stateStr));
			realEstate.setCity(cityStr);
			AddressOperations.getAddress(addressLine).ifPresent(address -> address.setRealEstateAddress(realEstate));

			realEstate.setAgentName(StringUtils.trimToNull(article.select("span[class=zsg-photo-card-broker-name]").text()));
			String type = article.select("span[class=zsg-photo-card-status]").text();
			realEstate.setRealEstateType(RealEstateType.of(type));
			if(realEstate.getRealEstateType() == null) {
				for(String split : type.split(" ")) {
					RealEstateType realEstateType = RealEstateType.of(split);
					if(realEstateType != null) {
						realEstate.setRealEstateType(realEstateType);
						break;
					}
				}
			}
			String priceStr = PriceOperations.priceStringCleaner(article.select("span[class=zsg-photo-card-price]").text());
			if(NumberUtils.isDigits(priceStr)) {
				realEstate.setPrice(new BigDecimal(priceStr));
			}
			String imgUrl = JsoupUtils.getImageSource(article, "img");
			if(StringUtils.containsIgnoreCase(imgUrl, "zillowstatic")) {
				realEstate.setMainImageUrl(imgUrl);
			}

			String json = article.select("div[class=minibubble template hide]").html();
			if(StringUtils.contains(json, "<!--") && StringUtils.contains(json, "-->")) {
				json = StringUtils.substringBetween(json, "<!--", "-->");

				String bed = getShittyJsonVal(json, "bedrooms");
				if(bed == null) {
					bed = getShittyJsonVal(json, "bed");
				}
				Double beds = AttributeOperations.reduceToDouble(bed);
				if(beds != null && beds > 0 && beds < 25) {
					realEstate.setBeds(beds);
				}
				String bath = getShittyJsonVal(json, "bathrooms");
				if(bath == null) {
					bath = getShittyJsonVal(json, "bath");
				}
				Double baths = AttributeOperations.reduceToDouble(bath);
				if(baths != null && baths > 0 && baths < 16) {
					realEstate.setBaths(baths);
				}
				String sqftStr = getShittyJsonVal(json, "livingArea");
				if(sqftStr == null) {
					sqftStr = getShittyJsonVal(json, "sqft");
				}
				Integer sqft = AttributeOperations.reduceToInteger(sqftStr);
				if(sqft != null && sqft < 100) {
					sqft = null;
				}
				realEstate.setSquareFeet(sqft);
				if(realEstate.getMainImageUrl() == null) {
					imgUrl = getShittyJsonVal(json, "imageLink");
					if(UrlValidator.getInstance().isValid(imgUrl)) {
						realEstate.setMainImageUrl(imgUrl);
					}
				}
				Integer yearBuilt = AttributeOperations.reduceToInteger(getShittyJsonVal(json, "yearBuilt"));
				if(yearBuilt != null && (yearBuilt < 1800 || yearBuilt > 2020)) {
					yearBuilt = null;
				}
				realEstate.setYearBuilt(yearBuilt);
				Integer lotSqft = AttributeOperations.reduceToInteger(getShittyJsonVal(json, "lotSize"));
				if(lotSqft != null && lotSqft < 100) {
					lotSqft = null;
				}
				realEstate.setLotSquareFeet(lotSqft);
				if(realEstate.getRealEstateType() == null) {
					realEstate.setRealEstateType(RealEstateType.of(getShittyJsonVal(json, "homeType")));
				}
				if(realEstate.getProductTerm() == null) {
					realEstate.setProductTerm(ProductTerm.of(getShittyJsonVal(json, "homeStatus")));
				}
				realEstate.setAgentPhone(getShittyJsonVal(json, "contactPhone"));
				if(realEstate.getPrice() == null) {
					priceStr = getShittyJsonVal(json, "title");
					if(StringUtils.contains(priceStr, "$")) {
						if(priceStr.contains("/mo")) {
							realEstate.setProductTerm(ProductTerm.RENT);
						}
						priceStr = priceStr.replaceAll("[^\\d]", "");
						if(NumberUtils.isDigits(priceStr)) {
							realEstate.setPrice(new BigDecimal(priceStr));
						}
					}
				}
			}
			if(StringUtils.length(realEstate.getAgentName()) > 128) {
				realEstate.setAgentName(realEstate.getAgentName().substring(0, 128));
			}
			realEstates.add(realEstate);
		}
		return realEstates;
	}

	private static String getShittyJsonVal(String shittyJson, String key) {
		if(shittyJson == null || key == null) {
			return null;
		}
		shittyJson = shittyJson.toLowerCase();
		key = key.toLowerCase();
		String keyLoc = "\"" + key + "\":";
		String val = null;
		if(shittyJson.contains(keyLoc)) {
			val = StringUtils.trimToEmpty(StringUtils.substringAfter(shittyJson, keyLoc));
			if(val.startsWith("\"")) {
				val = StringUtils.substringBetween(val, "\"", "\"");
			} else {
				if(val.contains("\"")) {
					val = StringUtils.substringBefore(val, "\"");
				} else if(val.contains("}")) {
					val = StringUtils.substringBefore(val, "}");
				}
			}
			if("null".equals(val)) {
				val = null;
			}
		}
		return StringUtils.trimToNull(StringUtils.remove(val, ","));
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		logger.warn(logPre() + "Build product not implemented for this data source");
		return null;
	}
}
