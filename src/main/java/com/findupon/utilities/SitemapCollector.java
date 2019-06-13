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

package com.findupon.utilities;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class SitemapCollector {
	private static final Logger logger = LoggerFactory.getLogger(SitemapCollector.class);
	private static final Pattern lastModDatePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
	private static final DateFormat modFormat = new SimpleDateFormat("yyyy-MM-dd");


	public static List<String> collect(String sitemapUrl) {
		return collect(sitemapUrl, com.findupon.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.findupon.commons.netops.entity.AgentMode.ROTATE, s -> true, null);
	}

	public static List<String> collect(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath) {
		return collect(formSitemapUrl(dataSource, sitemapPath), dataSource.getProxyMode(), dataSource.getAgentMode(), s -> true, null);
	}

	public static List<String> collect(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath, Predicate<String> locValidator) {
		return collect(formSitemapUrl(dataSource, sitemapPath), dataSource.getProxyMode(), dataSource.getAgentMode(), locValidator, null);
	}

	public static List<String> collectNested(String sitemapUrl) {
		return collectNested(sitemapUrl, com.findupon.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.findupon.commons.netops.entity.AgentMode.ROTATE, s -> true, s -> true, null, null);
	}

	public static List<String> collectNested(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath) {
		return collectNested(dataSource, sitemapPath, s -> true, s -> true, null, null);
	}

	public static List<String> collectNested(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath,
                                             Predicate<String> baseLocValidator, Predicate<String> nestedLocValidator) {

		return collectNested(formSitemapUrl(dataSource, sitemapPath), dataSource.getProxyMode(), dataSource.getAgentMode(),
				baseLocValidator, nestedLocValidator, null, null);
	}

	public static List<String> collectNested(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath,
                                             Predicate<String> baseLocValidator, Predicate<String> nestedLocValidator,
                                             Date baseOldestMod, Date nestedOldestMod) {

		return collectNested(formSitemapUrl(dataSource, sitemapPath), dataSource.getProxyMode(), dataSource.getAgentMode(),
				baseLocValidator, nestedLocValidator, baseOldestMod, nestedOldestMod);
	}

	private static List<String> collectNested(String sitemapUrl, com.findupon.commons.netops.entity.ProxyMode proxyMode, com.findupon.commons.netops.entity.AgentMode agentMode,
                                              Predicate<String> baseLocValidator, Predicate<String> nestedLocValidator,
                                              Date baseOldestMod, Date nestedOldestMod) {

		return collect(sitemapUrl, proxyMode, agentMode, baseLocValidator, baseOldestMod).stream()
				.flatMap(s -> collect(s, proxyMode, agentMode, nestedLocValidator, nestedOldestMod).stream())
				.collect(Collectors.toList());
	}

	private static List<String> collect(String sitemapUrl, com.findupon.commons.netops.entity.ProxyMode proxyMode, com.findupon.commons.netops.entity.AgentMode agentMode, Predicate<String> locValidator, Date oldestMod) {
		if(!StringUtils.endsWith(sitemapUrl, ".xml") && !StringUtils.endsWith(sitemapUrl, ".gz")) {
			logger.warn("[SitemapCollector] - Sitemap URL should be in xml or gz format [{}]", sitemapUrl);
		}
		Document xml = com.findupon.commons.netops.ConnectionAgent.INSTANCE.xmlDownload(sitemapUrl, proxyMode, agentMode);
		if(xml == null) {
			xml = com.findupon.commons.netops.ConnectionAgent.INSTANCE.xmlDownload(sitemapUrl, proxyMode, agentMode);
		}
		if(xml == null) {
			logger.error("[SitemapCollector] - Document [{}] came back null", sitemapUrl);
			return new ArrayList<>();
		}
		Set<String> siteMapUrls = new LinkedHashSet<>();
		String selector = xml.selectFirst("sitemap") != null ? "sitemap" : "url";

		xml.select(selector).stream()
				.filter(e -> lastModEnforcer(oldestMod).test(e))
				.map(e -> com.findupon.commons.utilities.JsoupUtils.selectFirst(e, "loc"))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(Element::hasText)
				.map(Element::text)
				.map(s -> charDataMapper().apply(s))
				.map(s -> com.findupon.commons.searchparty.ScoutServices.encodeSpacing(s, true))
				.filter(locValidator)
				.forEach(siteMapUrls::add);

		logger.info("[SitemapCollector] - [{}] URLs added from [{}]", String.format("%,d", siteMapUrls.size()), sitemapUrl);
		return new ArrayList<>(siteMapUrls);
	}

	private static Predicate<Element> lastModEnforcer(Date oldestAllowed) {
		return e -> {
			if(oldestAllowed == null) {
				return true;
			}
			Element lm = e.selectFirst("lastmod");
			if(lm == null || !lm.hasText()) {
				logger.warn("No last mod date element or text found");
				return true;
			}
			String lms = lm.text();
			Matcher m = lastModDatePattern.matcher(lms);
			if(!m.find()) {
				logger.warn("No last mod date pattern found");
				return true;
			}
			try {
				Date date = modFormat.parse(m.group(0));
				if(date != null && date.before(oldestAllowed)) {
					return false;
				}
			} catch(ParseException e1) {
				logger.warn("Could not parse last mod date [{}]", lms);
			}
			return true;
		};
	}

	private static Function<String, String> charDataMapper() {
		return s -> {
			if(StringUtils.containsIgnoreCase(s, "<![CDATA[")) {
				return StringUtils.trim(StringUtils.substringBetween(s, "<![CDATA[", "]]"));
			}
			return s;
		};
	}

	private static String formSitemapUrl(com.findupon.commons.entity.datasource.DataSource dataSource, String sitemapPath) {
		String dataSourceUrl = dataSource.getUrl();
		if(StringUtils.containsIgnoreCase(sitemapPath, dataSourceUrl)) {
			sitemapPath = StringUtils.remove(sitemapPath, dataSourceUrl);
		}
		if(!dataSourceUrl.endsWith("/")) {
			dataSourceUrl += "/";
		}
		if(sitemapPath.startsWith("/")) {
			sitemapPath = sitemapPath.substring(1);
		}
		return dataSourceUrl + sitemapPath;
	}
}
