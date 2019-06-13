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

package com.findupon.datasource.bot.automotive;///**
// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
// * *                                                                                                                   *
// * PLAINVIEW R&D - CONFIDENTIAL                                                                                        *
// * ____________________________                                                                                        *
// * *                                                                                                                   *
// * Copyright (c) 2016 - 2017 Plainview Research & Development LLC.                                                     *
// * *                                                                                                                   *
// * All Rights Reserved.                                                                                                *
// * *                                                                                                                   *
// * NOTICE:  All information contained herein is, and remains the property of Plainview Research & Development LLC      *
// * and its suppliers, if any.  The intellectual and technical concepts contained herein are proprietary to             *
// * Plainview Research & Development LLC and its suppliers and may be covered by U.S. and Foreign Patents, patents      *
// * in process, and are protected by trade secret or copyright law. Dissemination of this information or                *
// * reproduction of this material is strictly forbidden unless prior written permission is obtained from Plainview      *
// * Research & Development LLC.                                                                                         *
// * *                                                                                                                   *
// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
// */
//
//package com.findupon.datasource.bot.automotive;
//
//import com.findupon.datasource.bot.AbstractDealerRetrievalBot;
//import org.apache.commons.lang3.StringUtils;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import org.springframework.stereotype.Component;
//
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import static com.findupon.commons.utilities.JsoupUtils.firstChild;
//
//
//@Component
//@Deprecated // don't think this site is ever updated, our first run is probably our last
//public class ListingsUSDealerBot extends AbstractDealerRetrievalBot {
//	private final String BASE_URL = "http://listingsus.com/";
//
//
//	@Override
//	public void obtainDatasourceUrls() {
//		Pattern firstAlpha = Pattern.compile("\\p{Alpha}");
//		Document document = connector.downloadQuietly(BASE_URL + "auto_guide/dealers/");
//		if(document == null) {
//			logger.error("[ListingsUSDealerBot] - Error retrieving base URL [{}]", BASE_URL);
//			return;
//		}
//		Element subAreaSection = firstChild(document.select("section[id=subareas]"));
//		if(subAreaSection != null) {
//			for(Element anchor : subAreaSection.getElementsByTag("a")) {
//				String href = anchor.attr("href");
//				if(StringUtils.isNotBlank(href)) {
//					Matcher matcher = firstAlpha.matcher(href);
//					if(matcher.find() && href.length() > matcher.start()) {
//						obtainByPage(BASE_URL + href.substring(matcher.start()));
//					}
//				}
//			}
//		}
//	}
//
//	@Override
//	protected String getSourceName() {
//		return "ListingsUS";
//	}
//
//	private void obtainByPage(String url) {
//		Document document;
//		String nextUrl = url;
//		do {
//			document = connector.downloadQuietly(nextUrl);
//			if(document == null) {
//				return;
//			}
//			// find the header that contains "Inside <state>" and parse links after that
//			// if it doesn't exist, just grab all the links
//			Elements listSubheads = document.select("h5[class=listsubhead]");
//			if(!listSubheads.isEmpty()) {
//				for(Element h5 : listSubheads) {
//					if(h5.text().contains("Inside")) {
//						String htmlAfter = document.html().substring(document.html().indexOf(h5.html()));
//						addAllPotentialLinks(Jsoup.parse(htmlAfter));
//					}
//				}
//			} else {
//				addAllPotentialLinks(document);
//			}
//			String nextHref;
//			Element nextElement = firstChild(document.select("a:contains(Next)"));
//			if(nextElement != null && StringUtils.isNotBlank(nextHref = nextElement.attr("href"))) {
//				nextUrl = url + nextHref;
//			} else {
//				return;
//			}
//		} while(document.html().contains("Next"));
//	}
//
//	private void addAllPotentialLinks(Document document) {
//		document.select("a[class=linktitle]").stream().map(a -> a.attr("href")).forEach(this::addDealerUrl);
//	}
//}
