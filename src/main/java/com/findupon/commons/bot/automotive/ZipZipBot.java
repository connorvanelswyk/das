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
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


public class ZipZipBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, -30);

		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "system/sitemap.xml.gz",
				s -> true, s -> StringUtils.containsIgnoreCase(s, "/vehicles/"), null, calendar.getTime()));

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
		if(!StringUtils.containsIgnoreCase(url, "vehicles/")) {
			return BuiltProduct.removed(null);
		}
		URL urlObj = ScoutServices.formUrlFromString(url);
		String listingId = StringUtils.substringAfter(url, "vehicles/");
		Document document = download(url);
		if(document == null || urlObj == null) {
			return BuiltProduct.removed(listingId);
		}
		if(StringUtils.containsIgnoreCase(document.html(), "This vehicle is no longer available")) {
			return BuiltProduct.removed(listingId);
		}
		AtomicReference<String> vinRef = new AtomicReference<>();
		document.getElementsByAttributeValueContaining("href", "vehicle_history").stream()
				.map(e -> e.attr("href"))
				.filter(StringUtils::isNotEmpty)
				.flatMap(s -> Arrays.stream(s.split("/")))
				.filter(AutoParsingOperations.vinRecognizer())
				.findFirst()
				.ifPresent(v -> vinRef.set(v.toUpperCase()));
		String vin = vinRef.get();
		if(vin == null) {
			logger.debug(logPre() + "No vin found [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Automobile automobile = automotiveGatherer.buildAutomobile(document, JsoupUtils.defaultRemoveUnneeded(document.clone()), urlObj, listingId, vin);
		if(automobile == null) {
			return BuiltProduct.removed(listingId);
		}
		return BuiltProduct.success(automobile);
	}
}
