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
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class ISeeCarsBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		Document sitemapUsed = download("https://www.iseecars.com/sitemap");
		if(sitemapUsed == null) {
			logger.error(logPre() + "Used sitemap doc came back null");
			return baseUrls;
		}
		Set<String> cityLinks = sitemapUsed.getElementsByTag("a").stream()
				.filter(e -> e.hasAttr("href"))
				.map(e -> e.absUrl("href"))
				.filter(s -> StringUtils.containsIgnoreCase(s, "sitemap-s2"))
				.collect(Collectors.toSet());

		for(String cityLink : cityLinks) {
			Document cityDoc = download(cityLink);
			if(cityDoc == null) {
				logger.error(logPre() + "City doc came back null [{}]", cityLink);
				continue;
			}
			cityDoc.getElementsByTag("a").stream()
					.filter(e -> e.hasAttr("href"))
					.map(e -> e.absUrl("href"))
					.filter(s -> StringUtils.containsIgnoreCase(s, "sitemap-s6"))
					.forEach(baseUrls::add);
		}

		Document sitemapNew = download("https://www.iseecars.com/sitemap-new");
		if(sitemapNew == null) {
			logger.error(logPre() + "New sitemap doc came back null");
			return baseUrls;
		}
		sitemapNew.getElementsByTag("a").stream()
				.filter(e -> e.hasAttr("href"))
				.map(e -> e.absUrl("href"))
				.filter(s -> StringUtils.containsIgnoreCase(s, "new-cars-for-sale-in-"))
				.forEach(baseUrls::add);

		logger.info(logPre() + "Final base URL collection size [{}]", String.format("%,d", baseUrls.size()));
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		Set<String> additionalUrls = new HashSet<>();
		for(String baseUrl : baseUrls) {
			if(StringUtils.containsIgnoreCase(baseUrl, "sitemap-s6")) {
				if(sleep()) {
					return;
				}
				Document makeDoc = tryTwice(baseUrl);
				if(makeDoc == null) {
					logger.warn(logPre() + "Make doc came back null [{}]", baseUrl);
					continue;
				}
				makeDoc.select("div[class=span3]").stream()
						.flatMap(e -> e.getElementsByTag("a").stream())
						.filter(e -> e.hasAttr("href"))
						.map(e -> e.absUrl("href"))
						.forEach(additionalUrls::add);
			}
		}
		baseUrls.addAll(additionalUrls);
		indexOnlyGathering(nodeId, baseUrls, this::indexByPage);
	}

	private int indexByPage(String url) {
		int builtAutos = 0, pageNum = 1;

		Document document;
		do {
			logger.debug(logPre() + "Building from serp at page [{}]", pageNum);
			document = download(url + "?page=" + pageNum++);
			if(sleep()) {
				break;
			}
			if(document == null) {
				logger.warn(logPre() + ConsoleColors.yellow("Null document returned at page [{}], returning"), pageNum);
				logger.debug(logPre() + "Serp URL [{}]", url);
				return builtAutos;
			}
			for(Element serpElement : document.select("article[class*=article-search-result]")) {
				try {
					Automobile automobile;
					if(!serpElement.hasAttr("listing-url")) {
						logger.debug(logPre() + "No listing URL found in serp element");
						continue;
					}
					String automobileUrl = serpElement.absUrl("listing-url");
					if(UrlValidator.getInstance().isValid(automobileUrl)) {
						automobile = new Automobile(automobileUrl);
						automobile.setListingId(
								StringUtils.trimToNull(
										StringUtils.removeAll(
												StringUtils.substringAfter(automobileUrl, "#id="), "\\D+")));
					} else {
						logger.debug(logPre() + "Invalid detail page URL found in serp element [{}]", automobileUrl);
						continue;
					}
					Element detailLink = serpElement.selectFirst("span[class*=detailLink]");
					if(detailLink != null) {
						String detailText = JsoupUtils.defaultFilteringTextMapper.apply(detailLink);
						if(StringUtils.isNotEmpty(detailText)) {
							if(!automotiveGatherer.setMakeModelTrimYear(automobile, detailText)) {
								logger.debug(logPre() + "Could not parse MMY from text [{}] page [{}]", detailText, url);
								continue;
							}
						}
					}
					automobile.setMainImageUrl(serpElement.select("img[class*=s1_thumb]").stream()
							.filter(e -> e.hasAttr("src"))
							.findFirst()
							.map(e -> e.absUrl("src"))
							.filter(UrlValidator.getInstance()::isValid)
							.orElse(null));

					Element additionalInfoDiv = serpElement.selectFirst("div[class=additional-info-content]");
					if(additionalInfoDiv == null) {
						logger.warn(logPre() + "Missing additional info div required for parsing");
						continue;
					}
					additionalInfoDiv.select("div[class=additional-info-content-column]").forEach(e -> {
						Element keyElement = JsoupUtils.firstChild(e.getElementsByTag("b"));
						Element valueElement = JsoupUtils.firstChild(e.getElementsByTag("span"));
						if(keyElement != null && valueElement != null && keyElement.hasText() && valueElement.hasText()) {
							String key = keyElement.ownText();
							String value = valueElement.ownText();
							AutoParsingOperations.setAttribute(automobile, key, value);
						}
					});
					if(automobile.getListingId() == null && StringUtils.containsIgnoreCase(automobileUrl, "redirect")) {
						automobile.setListingId(automobile.getVin());
					}
					validateSetMetaAndAdd(automobile);
					builtAutos++;

				} catch(Exception e) {
					logger.error(logPre() + "Error building automobile from serp URL [{}]", url, e);
				}
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				logger.debug(logPre() + "Persisting [{}] automobiles to the db (over threshold)", products.size());
				persistAndClear();
			}
		} while(!StringUtils.containsIgnoreCase(document.html(), "There are fewer than")
				&& document.selectFirst("a[class*=nextBtn]") != null);

		return builtAutos;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		logger.error(logPre() + "Stop calling this method it's not implemented. This is an index_only data source.");
		return null;
	}
}
