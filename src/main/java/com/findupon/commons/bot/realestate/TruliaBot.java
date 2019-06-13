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
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.ProductTerm;
import com.findupon.commons.entity.product.attribute.RealEstateType;
import com.findupon.commons.entity.product.realestate.RealEstate;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.quickgeo.Place;

import java.math.BigDecimal;
import java.util.*;


public class TruliaBot extends ListingRealEstateBot {

	private final List<String> noMatchingResultsMessages = Arrays.asList("there are no available", "search does not match any homes");


	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrlsByZip(zip -> "for_sale/" + zip + "_zip/1_p/");
		baseUrlsByZip(zip -> "for_rent/" + zip + "_zip/1_p/");
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		for(String baseUrl : baseUrls) {
			buildByPage(baseUrl, noMatchingResultsMessages, d -> buildRealEstates(d, baseUrl),
					(document, integer) -> StringUtils.containsIgnoreCase(document.html(), "next page"));
		}
	}

	private List<RealEstate> buildRealEstates(Document document, String baseUrl) {
		List<RealEstate> realEstates = new ArrayList<>();

		String json = StringUtils.trim(StringUtils.substringBetween(document.html(), "var appState = ", "var googleMapURL"));
		if(StringUtils.endsWith(json, ";")) {
			json = json.substring(0, json.length() - 1);
		}

		for(Element card : document.select("div[class*=cardContainer]")) {
			RealEstate realEstate = new RealEstate();

			Element anchor = card.selectFirst("a[class=tileLink]");
			if(anchor == null || !anchor.hasAttr("href")) {
				continue;
			}
			String url = anchor.absUrl("href");
			if(UrlValidator.getInstance().isValid(url)) {
				realEstate.setUrl(url);
			} else {
				logger.debug(logPre() + "URL for real estate is not valid [{}]", url);
				continue;
			}
			String listingId = StringUtils.substringAfterLast(url, "--");
			if(!StringUtils.isNumeric(listingId)) {
				logger.debug(logPre() + "Invalid listing ID real estate [{}] URL [{}]", listingId, url);
				continue;
			}
			realEstate.setListingId(listingId);

			if(StringUtils.containsIgnoreCase(baseUrl, "for_sale")) {
				realEstate.setProductTerm(ProductTerm.OWN);
			} else if(StringUtils.containsIgnoreCase(baseUrl, "for_rent")) {
				realEstate.setProductTerm(ProductTerm.RENT);
			}

			String stateStr = StringUtils.trimToNull(card.select("span[itemprop=addressRegion]").text());
			String cityStr = StringUtils.trimToNull(card.select("span[itemprop=addressLocality]").text());
			String zip = StringUtils.trimToNull(card.select("span[itemprop=postalCode]").text());
			String street = StringUtils.trimToNull(card.select("span[itemprop=streetAddress]").text());

			if(stateStr == null || cityStr == null || street == null) {
				logger.trace(logPre() + "No city, state, or street info found");
				continue;
			}
			String addressLine = street + " " + cityStr + ", " + stateStr + " " + zip;
			addressLine = StringUtils.replaceAll(addressLine, " +", " ");

			realEstate.setState(State.valueOfAbbreviation(stateStr));
			realEstate.setCity(cityStr);
			AddressOperations.getAddress(addressLine).ifPresent(address -> address.setRealEstateAddress(realEstate));

			String priceStr = PriceOperations.priceStringCleaner(card.select("span[class*=cardPrice]").text());
			if(NumberUtils.isDigits(priceStr)) {
				realEstate.setPrice(new BigDecimal(priceStr));
			}
			realEstate.setMainImageUrl(JsoupUtils.getImageSource(card, "img[class*=cardPhoto]"));
			if(realEstate.getMainImageUrl() == null) {
				// lazy loaded, get from the json
				String imgUrl = StringUtils.substringBetween(json, "\"maloneId\":\"" + realEstate.getListingId() + "\",", "\",\"photoUrlForHdDpiDisplay");
				imgUrl = StringUtils.substringAfter(imgUrl, "photoUrl\":\"");
				imgUrl = StringUtils.replace(imgUrl, "\\/", "/");
				if(imgUrl != null) {
					imgUrl = "https://thumbs.trulia-cdn.com" + imgUrl;
					if(UrlValidator.getInstance().isValid(imgUrl)) {
						realEstate.setMainImageUrl(imgUrl);
					}
				}
			}

			realEstate.setBeds(AttributeOperations.reduceToDouble(card.select("li[data-auto-test=beds]").text()));
			realEstate.setBaths(AttributeOperations.reduceToDouble(card.select("li[data-auto-test=baths]").text()));
			realEstate.setSquareFeet(AttributeOperations.reduceToInteger(card.select("li[data-auto-test=sqft]").text()));

			// TODO: figure out real estate type

			realEstates.add(realEstate);
		}
		return realEstates;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		String listingId = ScoutServices.getUniqueIdentifierFromUrl(url);
		if(listingId == null && StringUtils.contains(url, "--")) {
			String id = StringUtils.substringAfter(url, "--");
			if(NumberUtils.isDigits(id)) {
				listingId = id;
			}
		}
		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		RealEstate realEstate = new RealEstate(url);
		realEstate.setListingId(listingId);

		String street = JsoupUtils.selectFirst(document, "div[data-role=address]").map(Element::ownText).orElse(null);
		String cityStateZip = JsoupUtils.selectFirst(document, "span[data-role=cityState]").map(Element::ownText).orElse(null);

		if(street == null || cityStateZip == null) {
			logger.warn(logPre() + "No address found [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		Optional<Address> addressOpt = AddressOperations.getAddress(street + " " + cityStateZip);
		if(addressOpt.isPresent()) {
			Address address = addressOpt.get();
			realEstate.setAddress(address.getLine());
			realEstate.setZip(address.getZip());
			realEstate.setLatitude(address.getLatitude());
			realEstate.setLongitude(address.getLongitude());

			Optional<Place> placeOpt = AddressOperations.getNearestPlaceFromZip(address.getZip());
			if(placeOpt.isPresent()) {
				Place p = placeOpt.get();
				realEstate.setState(State.valueOfAbbreviation(p.getAdminCode1()));
				realEstate.setCity(p.getPlaceName());
			} else {
				logger.warn(logPre() + "Could not determine state [{}]", url);
				return BuiltProduct.removed(listingId);
			}
		} else {
			logger.warn(logPre() + "No address found at [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Element imageDiv = document.selectFirst("div[id=mediaItem]");
		if(imageDiv != null) {
			if(imageDiv.hasAttr("style")) {
				String style = imageDiv.attr("style");
				if(StringUtils.containsIgnoreCase(style, "background-image:url(")) {
					String imageUrl = StringUtils.remove(style, "background-image:url(");
					imageUrl = StringUtils.removeEnd(imageUrl, ")");
					imageUrl = "https:" + imageUrl;
					if(UrlValidator.getInstance().isValid(imageUrl)) {
						realEstate.setMainImageUrl(imageUrl);
					}
				}
			} else {
				realEstate.setMainImageUrl(JsoupUtils.getImageSource(imageDiv, "img"));
			}
		}
		if(realEstate.getMainImageUrl() == null) {
			logger.warn(logPre() + "Could not determine image url for real estate [{}]", url);
		}
		Elements statusElements = document.select("span[class=typeCaps]");
		for(int i = statusElements.size() - 1; i >= 0; i--) {
			String text = statusElements.get(i).ownText();
			if(StringUtils.containsIgnoreCase(text, "auction")) {
				logger.debug(logPre() + "Auction listing found, not parsing [{}]", url);
				return BuiltProduct.removed(listingId);
			}
			ProductTerm status = ProductTerm.of(text);
			if(status != null) {
				realEstate.setProductTerm(status);
				break;
			}
		}
		if(realEstate.getProductTerm() == null) {
			logger.error(logPre() + "Could not determine product status at [{}]", url);
			return BuiltProduct.removed(listingId);
		}

		typeSetter:
		for(Element typeBullet : document.select("ul[class*=listInlineBulleted] > li[class*=miniHidden]")) {
			if(typeBullet.hasText()) {
				String text = typeBullet.ownText();
				for(RealEstateType type : RealEstateType.values()) {
					for(String match : type.getAllowedMatches()) {
						if(AttributeOperations.containsLoneAttribute(text, match)) {
							realEstate.setRealEstateType(type);
							break typeSetter;
						}
					}
				}
			}
		}

		Element overview = document.selectFirst("div[data-auto-test-id=home-details-overview]");
		if(overview != null) {
			for(Element element : overview.select("li")) {
				if(element.hasText()) {
					String s = element.ownText();
					if(StringUtils.containsIgnoreCase(s, "days on")) {
						continue;
					}
					if(StringUtils.containsIgnoreCase(s, "views")) {
						continue;
					}
					if(StringUtils.containsIgnoreCase(s, "bed")) {
						realEstate.setBeds(AttributeOperations.reduceToDouble(s));
					} else if(StringUtils.containsIgnoreCase(s, "bath")) {
						realEstate.setBaths(AttributeOperations.reduceToDouble(s));
					} else if(StringUtils.containsIgnoreCase(s, "sqft")) {
						if(!s.contains("$")) {
							if(StringUtils.containsIgnoreCase(s, "lot size")) {
								realEstate.setLotSquareFeet(AttributeOperations.reduceToInteger(s));
							} else {
								realEstate.setSquareFeet(AttributeOperations.reduceToInteger(s));
							}
						}
					} else if(StringUtils.containsIgnoreCase(s, "acres")) {
						s = s.replaceAll("[^\\d.]", "");
						Integer acres = AttributeOperations.reduceToInteger(s);
						if(acres != null) {
							acres *= 43560;
						}
						realEstate.setLotSquareFeet(acres);
					} else if(StringUtils.containsIgnoreCase(s, "built in")) {
						Integer yearBuilt = AttributeOperations.reduceToInteger(s);
						if(yearBuilt != null && (yearBuilt < 1800 || yearBuilt > 2020)) {
							yearBuilt = null;
						}
						realEstate.setYearBuilt(yearBuilt);
					}
				}
			}
		}

		if(StringUtils.containsIgnoreCase(document.html(), "Contact For Estimate")) {
			realEstate.setPrice(null);
		} else {
			setPrice(realEstate, JsoupUtils.priceMapper(document, "span[data-role=price]"));
		}
		return BuiltProduct.success(realEstate);
	}
}
