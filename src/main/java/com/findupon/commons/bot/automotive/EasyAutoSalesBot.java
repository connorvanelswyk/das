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

import com.google.common.collect.Lists;
import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class EasyAutoSalesBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "sitemap.xml"));

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
		URL urlObj = ScoutServices.getUrlFromString(url, false);
		String listingId;
		if(urlObj != null && StringUtils.isNotEmpty(urlObj.getPath())) {
			listingId = StringUtils.substring(urlObj.getPath(), 0, 254);
		} else {
			listingId = StringUtils.substring(url, 0, 254);
		}
		Document document = download(url);
		if(document == null) {
			return BuiltProduct.removed(listingId);
		}
		document.select("div[class=\"ui four doubling cards\"]").remove(); // related listings
		Automobile automobile = automotiveGatherer.buildAutomobile(document, JsoupUtils.defaultRemoveUnneeded(document.clone()), urlObj, listingId);
		if(automobile == null) {
			return BuiltProduct.removed(listingId);
		}
		Element subHeader = document.selectFirst("a[class=\"sub header\"]");
		if(subHeader != null && subHeader.hasText()) {
			String subText = subHeader.ownText();
			if(StringUtils.contains(subText, "-")) {
				String dealerName = StringUtils.trimToNull(StringUtils.substringBefore(subText, "-"));
				automobile.setDealerName(dealerName);
				String cityState = StringUtils.trimToNull(StringUtils.substringAfter(subText, "-"));
				AddressOperations.getAddressFromCityStateStr(cityState)
						.ifPresent(a -> a.setAutomobileAddress(automobile));
			}
		}
		Element priceElement = document.selectFirst("div[class=\"ui big red label\"]");
		if(priceElement != null && priceElement.hasText()) {
			automobile.setPrice(AutoParsingOperations.parsePrice(priceElement.ownText()));
		}
		return BuiltProduct.success(automobile);
	}
}
