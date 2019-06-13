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

import com.findupon.commons.entity.product.BuiltProduct;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Set;


public class OffLeaseOnlyBot extends ListingAutomobileBot {

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		baseUrls.forEach(this::gatherCarUrls);
		logger.info(logPre() + "Car URLs size [{}]", productUrls.size());
		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
	}

	private void gatherCarUrls(String baseUrl) {
		Document document;
		String currentUrl = baseUrl;
		Element breadcrumbDiv;

		do {
			logger.debug(logPre() + "Gathering all car urls from serp at [{}]", currentUrl);
			document = download(currentUrl);
			sleep();

			if(document == null) {
				logger.warn(logPre() + "Null document returned from serp URL [{}]", currentUrl);
				return;
			}

			Elements carContainers = document.select("div[class*=vehicle-listing]");

			for(Element carContainer : carContainers) {
				Element linkElement = carContainer.selectFirst("div[class*=vehicle-title-wrap] > h6 > a");
				if(linkElement == null || !linkElement.hasAttr("href")) {
					logger.warn(logPre() + "Link and title anchor came back null or missing text");
					continue;
				}

				String carUrl = linkElement.absUrl("href");

				//Now that we have the link for specific vehicle, grab the vehicle page and parse automobile
				if(UrlValidator.getInstance().isValid(carUrl)) {
					productUrls.add(carUrl);
				} else {
					logger.warn(logPre() + "Invalid detail page URL [{}]", carUrl);
				}
			}

			breadcrumbDiv = document.selectFirst("div[class=breadcrumb-page-list]");
			if(breadcrumbDiv == null) {
				return;
			}

			currentUrl = breadcrumbDiv.getElementsByTag("a").stream()
					.filter(e -> e.hasAttr("href"))
					.filter(e -> StringUtils.contains(e.ownText(), ">"))
					.findFirst()
					.map(e -> e.absUrl("href"))
					.orElse(null);

		} while(StringUtils.isNotEmpty(currentUrl));
	}

	@Override
	public Set<String> retrieveBaseUrls() {
		String rootUrl = getDataSource().getUrl() + "used-cars.htm";
		Document document = download(rootUrl);

		document.select("select[name=make] > option").stream()
				.map(e -> e.attr("value"))
				.filter(StringUtils::isNotEmpty)
				.filter(s -> !StringUtils.containsIgnoreCase(s, "harley"))
				.map(s -> getDataSource().getUrl() + "used-" + s + ".htm")
				.forEach(baseUrls::add);

		return baseUrls;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		return standardBuilder(url);
	}
}
