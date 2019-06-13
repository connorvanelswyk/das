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

package com.findupon.commons.searchparty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;


public final class ScoutServices {
	private static final Logger logger = LoggerFactory.getLogger(ScoutServices.class);

	private static final Set<String> genericIdentifiers = new HashSet<>(Arrays.asList(
			"uuid", "vuid",
			"productid", "product_id", "product-id",
			"listingid", "listing-id", "listing_id",
			"stock", "stockid", "stock-id", "stock_id",
			"stocknumber", "stock-number", "stock_number",
			"stocknum", "stock-num", "stock_num"));

	private static final Set<String> domainNamesToAvoid = new HashSet<>(Arrays.asList(
			"google", "ebay", "youtube", "facebook", "twitter", "amazon", "linkedin", "instagram", "apple", "blogspot", "wordpress", "tumblr", "typepad"));
	private static final Set<String> domainsToStayOn = new HashSet<>(Arrays.asList(
			"com", "net", "biz", "us", "org", "site", "auto", "forsale", "site", "online", "center", "cars", "us", "info", "deals"));


	public static String getUniqueIdentifierFromUrl(String url) {
		return getUniqueIdentifierFromUrl(url, null, x -> false);
	}

	public static String getUniqueIdentifierFromUrl(String url, String... domainSpecificIdentifiers) {
		List<String> identifiers = null;
		if(domainSpecificIdentifiers != null) {
			identifiers = Arrays.asList(domainSpecificIdentifiers);
		}
		return getUniqueIdentifierFromUrl(url, identifiers, x -> false);
	}

	public static String getUniqueIdentifierFromUrl(String url, Collection<String> domainSpecificIdentifiers,
	                                                Predicate<String> domainSpecificIdentityMatch) {
		URL urlObj;
		try {
			urlObj = new URL(url);
		} catch(MalformedURLException e) {
			logger.debug("Invalid URL encountered while attempting to find unique identifier [{}]", url, e);
			return null;
		}

		// if the url contains a query, check to see if the ID is there
		if(urlObj.getQuery() != null) {
			String identityParamValue = getIdentifierUrlParam(urlObj, domainSpecificIdentifiers);
			if(StringUtils.isNotEmpty(identityParamValue)) {
				return identityParamValue;
			}
		}

		// grab the path
		String path = StringUtils.trimToEmpty(urlObj.getPath());
		if(StringUtils.isEmpty(path)) {
			return null;
		}

		// decode any special characters
		try {
			path = URLDecoder.decode(path, "UTF-8");
		} catch(UnsupportedEncodingException e) {
			logger.warn("Bad encoding attempted", e);
		}

		// remove any trailing slashes so we can obtain the last section (/.*), avoiding unnecessary regex
		if(path.endsWith("/")) {
			if(path.length() > 1 && path.charAt(path.length() - 2) == '/') {
				path = path.replaceFirst("/*$", "");
			} else {
				path = path.substring(0, path.length() - 1);
			}
		}

		// verify we didn't empty the path and can continue
		if(!path.contains("/")) {
			return null;
		}

		// remove any file extension
		if(path.contains(".") && path.length() - path.lastIndexOf(".") <= 5) {
			path = path.substring(0, path.lastIndexOf("."));
		}

		// check each section for a valid ID, starting from the back since its usually last
		String[] sections = path.split("/");
		for(int x = sections.length - 1; x >= 0; x--) {

			// split the section by different delimiters to obtain keywords
			for(String keyword : sections[x].split("[- _]")) {

				// if its a number > 5 digits - great, we have an ID
				if(keyword.length() > 5 && NumberUtils.isDigits(keyword)) {
					logger.trace("[ScoutServices] - Found unique identifier as digits [{}]", keyword);
					return keyword;
				}

				// if its a domain-specific identifier (i.e. VIN) - great, we have an ID
				if(domainSpecificIdentityMatch.test(keyword)) {
					logger.trace("[ScoutServices] - Found unique identifier as VIN [{}]", keyword);
					return keyword;
				}

				// check if its a hash of some sort... this one is tricky
				if(isHash(keyword, false, true)) {
					logger.trace("[ScoutServices] - Found unique identifier as hash [{}]", keyword);
					return keyword;
				}
			}
		}
		logger.trace("No unique identifier found in path [{}]", path);
		return null;
	}

	public static boolean isLooseHash(String test) {
		return isHash(test, false, false, 2, 1, 1, 5);
	}

	public static boolean isHash(String test, boolean enforceSingleCase, boolean looseAlphanumericShuffling) {
		return isHash(test, enforceSingleCase, looseAlphanumericShuffling, 4, 3, 3, 6);
	}

	public static boolean isHash(String test, boolean enforceSingleCase, boolean looseAlphanumericShuffling,
	                             int shuffleSplitThreshold, int digitThreshold, int letterThreshold, int minLength) {
		if(test == null || test.length() <= minLength) {
			return false;
		}
		int digits = 0, letters = 0, capitals = 0, lowers = 0;
		for(char c : test.toCharArray()) {
			if(Character.isLetter(c)) {
				letters++;
				if(Character.isUpperCase(c)) {
					capitals++;
				} else if(Character.isLowerCase(c)) {
					lowers++;
				}
			} else if(Character.isDigit(c)) {
				digits++;
			} else {
				return false;
			}
		}
		return digits >= digitThreshold && letters >= letterThreshold
				&& !(looseAlphanumericShuffling && test.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)").length < shuffleSplitThreshold)
				&& (!enforceSingleCase || capitals == 0 && lowers > 0 || lowers == 0 && capitals > 0);
	}

	private static String getIdentifierUrlParam(URL url, Collection<String> domainSpecificIdentifiers) {
		for(Map.Entry<String, String> entry : getQueryParamValueMap(url).entrySet()) {
			String k = entry.getKey();
			String v = entry.getValue();
			if(StringUtils.isEmpty(v)) {
				continue;
			}
			Set<String> identifiers = new HashSet<>(genericIdentifiers);
			if(domainSpecificIdentifiers != null) {
				identifiers.addAll(domainSpecificIdentifiers);
			}
			for(String identifier : identifiers) {
				if(identifier.equalsIgnoreCase(k)) {
					if(v.length() > 5 && NumberUtils.isDigits(v)) {
						logger.trace("[ScoutServices] - Found Identity param value as digits! Param: [{}] Value: [{}]", k, v);
						return v;
					} else if(isHash(v, false, true)) {
						logger.trace("[ScoutServices] - Found Identity param value as hash! Param: [{}] Value: [{}]", k, v);
						return v;
					}
				}
			}
		}
		return null;
	}

	private static Map<String, String> getQueryParamValueMap(URL url) {
		Map<String, String> paramValueMap = new LinkedHashMap<>();
		if(url == null || url.getQuery() == null) {
			return paramValueMap;
		}
		for(String pair : url.getQuery().split("&")) {
			int x = pair.indexOf("=");
			if(x == -1) {
				continue;
			}
			String param = safeDecode(pair.substring(0, x));
			String value = safeDecode(pair.substring(x + 1));
			if(param != null) {
				paramValueMap.putIfAbsent(param, value);
			}
		}
		return paramValueMap;
	}

	private static String safeDecode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
		} catch(Exception e) {
			return null;
		}
	}

	public static String pureTextNormalizer(String str) {
		return pureTextNormalizer(str, false);
	}

	public static String pureTextNormalizer(String str, boolean makeLower) {
		str = StringUtils.trimToNull(str);
		if(str == null) {
			return null;
		}
		str = normalize(str, false);
		str = str.replaceAll("[^A-Za-z0-9]", " ");
		str = str.replaceAll(" +", " ");
		if(makeLower) {
			str = StringUtils.lowerCase(str, Locale.ENGLISH);
		}
		return str;
	}

	public static String normalize(String str) {
		return normalize(str, true);
	}

	public static String normalize(String str, boolean removeStar) {
		if(str == null) {
			return null;
		}
		str = StringUtils.stripAccents(str);
		str = str.replaceAll("[^\\x00-\\x7F]", "");
		if(removeStar) {
			str = str.replace("*", "");
		}
		return str.trim();
	}

	public static String formUrlFromString(String url, boolean parseByProtocolAndHost) {
		URL formedUrl = formUrlFromString(url);
		if(formedUrl != null) {
			return parseByProtocolAndHost ? parseByProtocolAndHost(formedUrl) : formedUrl.toExternalForm();
		} else {
			return null;
		}
	}

	public static URL formUrlFromString(String url) {
		if(basicInvalid(url)) {
			return null;
		}
		if(!StringUtils.startsWithIgnoreCase(url, "http://") && !StringUtils.startsWithIgnoreCase(url, "https://")) {
			// no protocol provided, assume http as the ConnectionAgent will resolve http > https redirects
			url = "http://" + url;
		}
		return ScoutServices.getUrlFromString(url, false);
	}

	public static URL getUrlFromString(String url) {
		return getUrlFromString(url, false);
	}

	public static URL getUrlFromString(String url, boolean addTrailingSlash) {
		if(basicInvalid(url)) {
			return null;
		}
		if(addTrailingSlash && !StringUtils.endsWith(url, "/")) {
			url += "/";
		}
		URL urlObj;
		try {
			urlObj = new URL(encodeSpacing(url, true));
		} catch(MalformedURLException e) {
			return null;
		}
		return invalidDomain(urlObj) ? null : urlObj;
	}

	public static URI getUriFromString(String url) {
		URL urlObj = getUrlFromString(url, false); // force parsing as a URL object as URI is not strict
		if(urlObj != null) {
			try {
				return urlObj.toURI();
			} catch(URISyntaxException e) {
				return null;
			}
		}
		return null;
	}

	public static String parseByProtocolAndHost(String url) {
		if(basicInvalid(url)) {
			return null;
		}
		URL urlObj;
		try {
			urlObj = new URL(encodeSpacing(url, true));
		} catch(MalformedURLException e) {
			return null;
		}
		return parseByProtocolAndHost(urlObj);
	}

	public static String parseByProtocolAndHost(URL url) {
		if(url == null) {
			return null;
		}
		if(basicInvalid(url.toExternalForm())) {
			return null;
		}
		if(invalidDomain(url)) {
			return null;
		}
		return url.getProtocol() + "://" + url.getHost() + "/";
	}

	public static String encodeSpacing(String url) {
		return encodeSpacing(url, false);
	}

	public static String encodeSpacing(String url, boolean removeNewLineAndTabs) {
		return encodeSpacing(url, removeNewLineAndTabs, false);
	}

	public static String encodeSpacing(String url, boolean removeNewLineAndTabs, boolean makeLowercase) {
		if(url.contains(" ")) {
			url = url.replace(" ", "%20");
		}
		if(removeNewLineAndTabs) {
			if(url.contains("\n")) {
				url = url.replace("\n", "");
			}
			if(url.contains("\t")) {
				url = url.replace("\t", "");
			}
		}
		if(makeLowercase) {
			url = url.toLowerCase();
		}
		return url;
	}

	public static String getDomain(String url) {
		URL urlObj = getUrlFromString(url, false);
		if(urlObj == null) {
			return null;
		}
		return getDomain(urlObj);
	}

	public static String getDomain(URL url) {
		String domain = url.getHost();
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}

	public static String getDomain(URI uri) {
		String domain = uri.getHost();
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}

	public static String getDomainName(String url) {
		URL urlObj = getUrlFromString(url, false);
		if(urlObj == null) {
			return null;
		}
		return getDomainName(urlObj);
	}

	public static String getDomainName(URL url) {
		String domainName = getDomain(url);
		if(domainName.contains(".")) {
			domainName = domainName.substring(0, domainName.lastIndexOf('.'));
		}
		return domainName;
	}

	private static boolean basicInvalid(String url) {
		if(StringUtils.isBlank(url)) {
			return true;
		}
		if(StringUtils.containsIgnoreCase(url, "bit.ly")) {
			return true;
		}
		if(StringUtils.containsIgnoreCase(url, "goo.gl")) {
			return true;
		}
		if(!url.contains(".")) {
			return true;
		}
		return false;
	}

	private static boolean invalidDomain(URL url) {
		if(url == null) {
			return true;
		}
		if(StringUtils.isBlank(url.getHost())) {
			return true;
		}
		return !DomainValidator.getInstance().isValid(url.getHost());
	}

	public static boolean clearToVisitDomain(String url) {
		if(StringUtils.isBlank(url)) {
			return false;
		}
		return domainNamesToAvoid.stream().noneMatch(d -> StringUtils.containsIgnoreCase(url, d));
	}

	public static boolean acceptedDomainExtension(String url) {
		URL urlObj = getUrlFromString(url, true);
		if(urlObj == null) {
			return false;
		}
		return acceptedDomainExtension(urlObj);
	}

	public static boolean acceptedDomainExtension(URL url) {
		if(url == null) {
			return false;
		}
		String urlStr = parseByProtocolAndHost(url);
		if(urlStr == null || !urlStr.contains(".")) {
			return false;
		}
		String domain = urlStr.substring(urlStr.lastIndexOf("."));
		return domainsToStayOn.stream().anyMatch(d -> StringUtils.containsIgnoreCase(domain, d));
	}

	public static String getQueryParamValue(String url, String param) {
		param = StringUtils.trimToNull(param);
		if(param == null) {
			return null;
		}
		return getQueryParamValueMap(getUrlFromString(url)).get(param);
	}

	public static String setQueryParamValue(String url, String param, String value) {
		param = StringUtils.trimToNull(param);
		value = StringUtils.trimToNull(value);
		if(param == null || value == null) {
			return null;
		}
		URI uri = ScoutServices.getUriFromString(url);
		if(uri == null) {
			return null;
		}
		if(param.startsWith("?") || param.startsWith("&")) {
			param = param.substring(1);
		}
		if(param.endsWith("=")) {
			param = param.substring(0, param.length() - 1);
		}
		if(StringUtils.containsIgnoreCase(uri.getQuery(), param + "=")) {
			return url.replaceFirst("\\b" + param + "=.*?(&|$)", param + "=" + value + "$1");
		}
		try {
			return new URIBuilder(uri).addParameter(param, value).build().toString();
		} catch(URISyntaxException e) {
			return null;
		}
	}
}
