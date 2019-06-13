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

package com.findupon.commons.utilities;

import com.findupon.commons.utilities.Functions;
import com.findupon.commons.building.PriceOperations;
import com.findupon.commons.entity.building.TagWeight;
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;


public final class JsoupUtils {
	private static final Logger logger = LoggerFactory.getLogger(JsoupUtils.class);
	private static final int defaultMaxTextLength = 128;

	/**
	 * Basic element-text validation.
	 */
	public static final Predicate<Element> defaultTextQualityGate = e -> StringUtils.isNotEmpty(qualityText(e, false));

	/**
	 * Basic element-text validation with an included predicate.
	 * The {@code Predicate<String>} is used to never call {@link Element#text()} more than once.
	 */
	public static final BiPredicate<Element, Predicate<String>> textQualityGate = (e, p) -> {
		String text = qualityText(e, false);
		return StringUtils.isNotEmpty(text) && p.test(text);
	};

	/**
	 * Map an element to its (quality) text as efficiently as possible.
	 */
	public static final Function<Element, String> defaultFilteringTextMapper = JsoupUtils::qualityText;

	/**
	 * Map an element to its (quality) text as efficiently as possible.
	 * The {@code Predicate<String>} is used to never call {@link Element#text()} more than once.
	 */
	public static final BiFunction<Element, Predicate<String>, String> filteringTextMapper = (e, p) -> {
		String text = qualityText(e);
		if(StringUtils.isNotEmpty(text) && p.test(text)) {
			return text;
		}
		return StringUtils.EMPTY;
	};

	/**
	 * Map an element to its (quality) text as efficiently as possible.
	 * The first {@code Predicate<String>} is used to never call {@link Element#text()} more than once.
	 * In doing so, the text quality gates (the cheapest operations) can be applied before the {@code Predicate<Element>}
	 * with the previous text result saved for return.
	 */
	public static final Functions.TriFunction<Element, Predicate<String>, Predicate<Element>, String> dualFilteringTextMapper = (e, sp, ep) -> {
		String text = qualityText(e);
		if(StringUtils.isNotEmpty(text) && sp.test(text) && ep.test(e)) {
			return text;
		}
		return StringUtils.EMPTY;
	};

	private static String qualityText(Element element) {
		return qualityText(element, true);
	}

	/**
	 * Used for basic text qualifications or in pair with element-text mapping.
	 * {@link Element#hasText()} is called before {@link Element#text()} as it is a cheaper operation that shorts out.
	 * @return the text that has passed the basic quality gates, or empty if it has failed.
	 */
	private static String qualityText(Element element, boolean normalize) {
		if(element == null) {
			return StringUtils.EMPTY;
		}
		if(!element.hasText()) {
			return StringUtils.EMPTY;
		}
		if(TagWeight.largeTagsToAvoid.contains(element.tagName())) {
			return StringUtils.EMPTY;
		}
		String text = element.text();
		if(text.length() >= defaultMaxTextLength) {
			return StringUtils.EMPTY;
		}
		return normalize ? ScoutServices.normalize(text) : text;
	}

	public static Stream<String> streamText(Document document) {
		return streamText(document, " ", true);
	}

	public static Stream<String> streamText(Document document, String spliterator, boolean normalize) {
		if(normalize) {
			return Arrays.stream(document.text().split(spliterator)).map(ScoutServices::normalize);
		} else {
			return Arrays.stream(document.text().split(spliterator));
		}
	}

	public static Document defaultStripTags(Document document) {
		return stripTags(document, TagWeight.auxiliaryTags);
	}

	/**
	 * This method does not change the reference the original document, as it parses a new one from the raw HTML.
	 * This is because the underlying stripTags function uses pure String replacement.
	 * @return A new {@link Document} with the specified {@param tags} removed.
	 */
	public static Document stripTags(Document document, String... tags) {
		return Jsoup.parse(stripTags(document.html(), tags), document.location());
	}

	/**
	 * @return The HTML with the specified {@param tags} removed.
	 */
	public static String stripTags(String html, String... tags) {
		html = ScoutServices.normalize(html);
		for(String tag : tags) {
			html = html.replaceAll("<" + tag + ".*?>", " ").replace("</" + tag + ">", " ").replaceAll(" +", " ");
		}
		return html;
	}

	public static String firstChildOwnText(Element element) {
		if(element == null || !element.hasText()) {
			return null;
		}
		return element.ownText();
	}

	public static Element firstChild(Elements elements) {
		if(elements == null || elements.isEmpty()) {
			return null;
		}
		return elements.get(0);
	}

	public static Document defaultRemoveUnneeded(Document document) {
		removeByTags(document, TagWeight.lowValueTags);
		defaultRemoveByAttributeValueContaining(document, TagWeight.adscititiousAttributeKeys);
		return document;
	}

	public static void removeByTags(Document document, String[] tagsToRemove) {
		for(String tag : tagsToRemove) {
			document.getElementsByTag(tag).remove();
		}
	}

	public static void defaultRemoveByAttributeValueContaining(Document document, String[] attrValuesToRemove) {
		removeByAttributeValueContaining(document, attrValuesToRemove, TagWeight.classIdAttributeKeys);
	}

	public static void removeByAttributeValueContaining(Document document, String[] attrValuesToRemove, String[] attrKeys) {
		for(String value : attrValuesToRemove) {
			for(String attributeKey : attrKeys) {
				document.getElementsByAttributeValueContaining(attributeKey, value).remove();
			}
		}
	}

	public static List<Element> defaultGetElementsByAttributeValueContaining(Document document, String[] keywordsToMatch) {
		return getElementsByAttributeValueContaining(document, TagWeight.integralAttributeKeys, keywordsToMatch);
	}

	// TODO: make another jsoup method to perform this with a collection
	public static List<Element> getElementsByAttributeValueContaining(Document document, String[] attributeKeys, String[] keywordsToMatch) {
		List<Element> matchingElements = new ArrayList<>();
		for(String keyword : keywordsToMatch) {
			for(String attributeKey : attributeKeys) {
				matchingElements.addAll(document.getElementsByAttributeValueContaining(attributeKey, keyword));
			}
		}
		return matchingElements;
	}

	public static Optional<Element> selectFirst(Element element, String cssQuery) {
		if(element == null || cssQuery == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(element.selectFirst(cssQuery));
	}

	public static String getImageSource(Element element, String cssQuery) {
		return getImageSource(element, cssQuery, "src");
	}

	public static String getImageSource(Element element, String cssQuery, String srcAttrKey) {
		Element img = element.selectFirst(cssQuery);
		if(img != null && img.hasAttr(srcAttrKey)) {
			String src = img.absUrl(srcAttrKey);
			if(UrlValidator.getInstance().isValid(src)) {
				return src;
			}
		}
		return null;
	}

	public static BigDecimal priceMapper(Element element, String cssQuery, Predicate<BigDecimal> validator) {
		BigDecimal price = null;
		Optional<Element> opt = selectFirst(element, cssQuery);
		if(opt.isPresent()) {
			String text = PriceOperations.priceStringCleaner(opt.get().ownText());
			if(StringUtils.isNumeric(text)) {
				try {
					price = new BigDecimal(text);
				} catch(Exception e) {
					logger.trace("Error parsing price [{}]", text, e);
				}
			}
		}
		if(price != null && !validator.test(price)) {
			price = null;
		}
		return price;
	}

	public static BigDecimal priceMapper(Element element, String cssQuery) {
		return priceMapper(element, cssQuery, p -> true);
	}
}
