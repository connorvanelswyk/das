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

import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.dao.ProductDao;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.Drivetrain;
import com.findupon.commons.entity.product.attribute.ExteriorColor;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.utilities.JsonUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Locale;
import java.util.Set;


public class AutotraderBot extends ListingAutomobileBot {

	private final String noMatchingResultsMessage = "No results found";


	@Override
	public Set<String> retrieveBaseUrls() {
		SitemapCollector.collectNested(getDataSource(), "sitemap_srp_index_all.xml").stream()
				.map(s -> s + "?numRecords=100&firstRecord=0")
				.forEach(baseUrls::add);
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		for(String baseUrl : baseUrls) {
			retrieveByPage(baseUrl);
		}
	}

	private void retrieveByPage(String baseUrl) {
		Document firstPage = tryTwice(baseUrl);
		if(firstPage == null) {
			return;
		}
		Document currentPage;
		int pageNumber = 0;
		if(StringUtils.containsIgnoreCase(firstPage.html(), noMatchingResultsMessage)) {
			logger.debug(logPre() + "No matching results message found at first page URL [{}]", firstPage.location());
			return;
		}
		boolean keepSerpin = true;
		do {
			pageNumber++;
			if(firstPage != null) {
				currentPage = firstPage;
				firstPage = null;
			} else {
				String pageUrl = baseUrl.replaceAll("&firstRecord=\\d+", "&firstRecord=" + (pageNumber - 1) * 100);
				currentPage = tryTwice(pageUrl);
			}
			if(sleep()) {
				break;
			}
			if(currentPage == null) {
				logger.warn(logPre() + "Null document returned at page [{}], returning. Serp URL [{}]", pageNumber, baseUrl);
				return;
			}
			logger.debug(logPre() + "Building from serp at [{}]", currentPage.location());
			if(StringUtils.containsIgnoreCase(currentPage.html(), noMatchingResultsMessage)) {
				logger.debug(logPre() + "No matching results message found at serp URL [{}]", currentPage.location());
				return;
			}
			String json = StringUtils.substringBetween(currentPage.html(), "mountRoot(\"", "\",{\"myatc\":");
			json = StringUtils.replace(json, "\\", "");
			String listings = StringUtils.substringBetween(json, "\"listings\":", ",\"priceRanges\":");

			if(listings != null) {
				JSONArray ja;
				try {
					ja = new JSONArray(listings);
				} catch(Exception e) {
					logger.warn(logPre() + "Could not parse listing json");
					continue;
				}
				for(int x = 0; x < ja.length(); x++) {
					JSONObject jo = ja.getJSONObject(x);
					Automobile automobile = new Automobile();

					automobile.setExteriorColor(ExteriorColor.of(JsonUtils.optString(jo, "colorExteriorSimple")));
					String price = JsonUtils.optString(jo, "salePrice");
					if(price == null) {
						price = JsonUtils.optString(jo, "derivedPrice");
					}
					if(price == null) {
						price = JsonUtils.optString(jo, "firstPrice");
					}
					automobile.setPrice(AutoParsingOperations.parsePrice(price));
					automobile.setDrivetrain(Drivetrain.of(JsonUtils.optString(jo, "driveType")));
					String imageUrl = JsonUtils.optString(jo, "imageURL");
					imageUrl = StringUtils.replace(imageUrl, "\\/", "/");
					if(UrlValidator.getInstance().isValid(imageUrl)) {
						automobile.setMainImageUrl(imageUrl);
					}
					automobile.setListingId(JsonUtils.optString(jo, "listingId"));
					if(StringUtils.isEmpty(automobile.getListingId())) {
						continue;
					}
					automobile.setUrl(getDataSource().getUrl() + "cars-for-sale/vehicledetails.xhtml?listingId=" + automobile.getListingId());
					automobile.setMileage(AutoParsingOperations.parseMileage(JsonUtils.optString(jo, "maxMileage")));

					String trim = StringUtils.defaultString(JsonUtils.optString(jo, "trim"));
					String title = StringUtils.trimToNull(JsonUtils.optString(jo, "title") + " " + trim);

					if(!automotiveGatherer.setMakeModelTrimYear(automobile, title)) {
						continue;
					}
					automobile.setMpgHighway(JsonUtils.optInteger(jo, "mpgHighway"));
					automobile.setMpgCity(JsonUtils.optInteger(jo, "mpgCity"));

					automobile.setDealerName(JsonUtils.optString(jo, "ownerName"));
					String vin = JsonUtils.optString(jo, "vin");
					if(AutoParsingOperations.vinRecognizer().test(vin)) {
						automobile.setVin(vin.toUpperCase(Locale.US));
					} else {
						continue;
					}
					validateSetMetaAndAdd(automobile);
				}
			} else {
				logger.debug(logPre() + "Missing listing json object");
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				logger.debug(logPre() + "Persisting [{}] automobiles to the db (over threshold)", products.size());
				persistAndClear();
			}
			Element nextSpan = currentPage.selectFirst("span[aria-label=\"Next\"]");
			if(nextSpan == null) {
				keepSerpin = false;
			} else {
				Element li = nextSpan.parent().parent();
				if("li".equals(li.tagName())) {
					if(li.hasClass("disabled")) {
						keepSerpin = false;
					}
				}
			}
		} while(pageNumber <= 10 && keepSerpin);
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		logger.error(logPre() + "Stop calling this method it's not implemented. This is an index_only data source.");
		return null;
	}
}
