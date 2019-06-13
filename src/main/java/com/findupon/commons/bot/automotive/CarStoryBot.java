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
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.ExteriorColor;
import com.findupon.commons.entity.product.attribute.InteriorColor;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Map;
import java.util.Set;


public class CarStoryBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "sitemap_index.xml.gz"));
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		indexOnlyGathering(nodeId, baseUrls, this::buildFromSerpJson);
	}

	@SuppressWarnings("unchecked")
	private int buildFromSerpJson(String url) {
		Document document = download(url);
		int builtAutomobiles = 0;
		if(document == null) {
			logger.warn(logPre() + "Document came back null from the builder [{}]", url);
			return builtAutomobiles;
		}

		String jindex1 = "\"searchResults\":";
		String jindex2 = ",\"jumpstart\":";
		String html = document.html();
		if(html.contains(jindex1) && html.contains(jindex2)) {
			String json = "{" + html.substring(html.indexOf(jindex1), html.indexOf(jindex2)) + "}";
			JSONObject jo = new JSONObject(json);
			JSONArray ja = ((JSONObject)jo.get("searchResults")).getJSONArray("results");
			for(int x = 0; x < ja.length(); x++) {
				try {
					Map<String, Object> detailMap = ((JSONObject)ja.get(x)).toMap();
					Automobile automobile = new Automobile();

					String vin = getString(detailMap, "vin");
					if(StringUtils.isNotEmpty(vin) && AutoParsingOperations.vinRecognizer().test(vin)) {
						vin = vin.toUpperCase();
						automobile.setListingId(vin);
						automobile.setVin(vin);
					} else {
						logger.trace(logPre() + "Missing/ invalid vin [{}]", url);
						continue;
					}
					String title = getString(detailMap, "make") + " " + getString(detailMap, "model")
							+ " " + getString(detailMap, "trim") + " " + getString(detailMap, "year");
					if(!automotiveGatherer.setMakeModelTrimYear(automobile, title)) {
						logger.debug(logPre() + "Missing/ invalid car title, can't parse make/model/year [{}] at [{}]", title, url);
						continue;
					}
					String priceStr = getString(detailMap, "price");
					if(StringUtils.isNotEmpty(priceStr)) {
						automobile.setPrice(AutoParsingOperations.parsePrice(priceStr));
					} else {
						logger.trace(logPre() + "Missing price [{}]", url);
					}
					String mileageStr = getString(detailMap, "mileage");
					if(StringUtils.isNotEmpty(mileageStr)) {
						mileageStr = mileageStr.replace(",", "");
						if(NumberUtils.isDigits(mileageStr)) {
							int mileage = Integer.parseInt(mileageStr);
							if(mileage >= 0 && mileage <= 500_000) {
								automobile.setMileage(mileage);
							}
						}
					}
					String city = getString(detailMap, "city");
					State state = State.valueOfAbbreviation(getString(detailMap, "state"));
					AddressOperations.getAddressFromCityState(city, state).ifPresent(a -> a.setAutomobileAddress(automobile));

					String href = getString(detailMap, "via");
					if(UrlValidator.getInstance().isValid(href)) {
						if(!StringUtils.containsIgnoreCase(href, "details.vast")) {
							Element listingHref = document.getElementsByAttributeValueContaining("href", vin).first();
							if(listingHref == null) {
								logger.warn("No listing href found for non-vast details link. URL [{}]", url);
								continue;
							}
							String onPageHref = listingHref.absUrl("href");
							if(UrlValidator.getInstance().isValid(onPageHref)) {
								automobile.setUrl(onPageHref);
							} else {
								logger.warn(logPre() + "Invalid on page href found for vin [{}] href [{}] url", vin, href, url);
								continue;
							}
						} else {
							automobile.setUrl(href);
						}
					} else {
						logger.warn(logPre() + "No listing href found for vin [{}] url [{}]", vin, url);
						continue;
					}
					validateSetMetaAndAdd(automobile);
					builtAutomobiles++;
				} catch(Exception e) {
					logger.warn(logPre() + "Error parsing detail json [{}]", url, e);
				}
			}
		} else {
			logger.warn("No json index found at [{}]", url);
		}
		return builtAutomobiles;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		return standardBuilder(url, ((document, automobile) -> {
			Element locationElement = document.selectFirst("span[class=u-ellipsis]");
			if(locationElement != null && locationElement.hasText()) {
				AddressOperations.getAddressFromCityStateStr(locationElement.ownText())
						.ifPresent(a -> a.setAutomobileAddress(automobile));
			}
			String colors = document.getElementsContainingOwnText("EXTERIOR/INTERIOR").next("p").text();
			if(StringUtils.contains(colors, "/")) {
				automobile.setExteriorColor(ExteriorColor.of(StringUtils.substringBefore(colors, "/")));
				automobile.setInteriorColor(InteriorColor.of(StringUtils.substringAfter(colors, "/")));
			}
		}));
	}

	private String getString(Map<String, Object> detailMap, String key) {
		Object value = detailMap.get(key);
		if((value instanceof String || value instanceof Number)) {
			return value.toString();
		}
		return StringUtils.EMPTY;
	}
}
