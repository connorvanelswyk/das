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

package com.findupon.commons.building;

import com.findupon.commons.entity.building.TagWeight;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.Functions;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.AttributeMatch;
import com.findupon.utilities.ContainingLoneAttributeMatcher;
import com.findupon.utilities.ContainsCollectionOwnText;
import com.findupon.utilities.PermutableAttribute;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public final class AttributeOperations {
	private static final Logger logger = LoggerFactory.getLogger(AttributeOperations.class);


	public static Map<Integer, PermutableAttribute> matchFromText(Map<Integer, PermutableAttribute> attributeMap, String text) {
		Map<Integer, DoubleAdder> attributeWeightMap = new LinkedHashMap<>();
		attributeMap.forEach((k, v) -> v.getPermutations()
				.forEach(p -> getLargestValidValueInsideString(text, s -> StringUtils.equalsIgnoreCase(p, s))
						.ifPresent(a -> attributeWeightMap.computeIfAbsent(k, x -> new DoubleAdder())
								.add(TagWeight.DEFAULT_WEIGHT))));
		return attributeWeightMap.entrySet().stream().collect(Functions.allMax(
				Comparator.comparingDouble(x -> x.getValue().doubleValue()),
				Collectors.toMap(Map.Entry::getKey, x -> attributeMap.get(x.getKey())))
		);
	}

	public static Map<Integer, AttributeMatch> matchTransformer(Map<Integer, PermutableAttribute> attributeMap, Element element, URL url) {
		return matchTransformer(attributeMap, Collections.singletonList(element), url);
	}

	public static Map<Integer, AttributeMatch> loneAttributeMatcher(Map<Integer, PermutableAttribute> attributeMap, Element element) {
		Map<Integer, AttributeMatch> matches = new HashMap<>();
		for(Map.Entry<Integer, PermutableAttribute> entry : attributeMap.entrySet()) {
			matches.put(entry.getKey(), new AttributeMatch(entry.getValue().getAttribute(), entry.getValue().getPermutations()));
		}
		Collector.collect(new ContainingLoneAttributeMatcher(matches), element);
		return matches;
	}

	public static Map<Integer, AttributeMatch> matchTransformer(Map<Integer, PermutableAttribute> rawAttributes, Collection<Element> elements, URL url) {
		Map<Integer, AttributeMatch> matches = new LinkedHashMap<>();
		elements.forEach(element -> loneAttributeMatcher(rawAttributes, element).entrySet().stream()
				.filter(e -> !e.getValue().getMatchingElements().isEmpty())
				.forEach(e ->
						matches.merge(e.getKey(), e.getValue(), (m0, m1) -> {
							m0.getMatchingElements().addAll(m1.getMatchingElements());
							return m0;
						})));
		Map<Integer, AttributeMatch> reduction = maxWeightReduce(matches, url);
		if(reduction.size() <= 1 || rawAttributes.entrySet().stream().allMatch(e -> e.getValue().getChildren().isEmpty())) {
			return reduction;
		}
		logger.trace("[AttributeOperations] - Determining tied matches based on children. Tied attributes [{}]",
				reduction.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")));
		Map<Integer, AttributeMatch> childMatches = new LinkedHashMap<>();
		reduction.forEach((rk, rv) ->
				matchTransformer(rawAttributes.get(rk).getChildren(), elements, url).forEach((tk, tv) ->
						childMatches.merge(rk, tv, (m0, m1) -> {
							m0.getMatchingElements().addAll(m1.getMatchingElements());
							return m0;
						})));
		if(!childMatches.isEmpty()) {
			reduction.entrySet().removeIf(e -> !childMatches.keySet().contains(e.getKey()));
			logger.debug("[AttributeOperations] - Recursive child reduction success. Attributes [{}] Children matched [{}]",
					reduction.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")),
					childMatches.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")));
		}
		return reduction;
	}

	private static Map<Integer, AttributeMatch> maxWeightReduce(Map<Integer, AttributeMatch> matches, URL url) {
		Map<Integer, DoubleAdder> attributeWeightMap = new LinkedHashMap<>();
		matches.forEach((k, v) -> {
			v.getMatchingElements().stream()
					.filter(JsoupUtils.defaultTextQualityGate)
					.forEach(e -> attributeWeightMap.computeIfAbsent(k,
							x -> new DoubleAdder()).add(TagWeight.getWeight(e)));
			if(url != null) {
				String path = url.getPath().replace("%20", " ").replace("+", " ").replace("_", " ").replace("*", " ");
				for(String attribute : v.getAllowedMatches()) {
					int x = 0;
					while(containsLoneAttribute(path, attribute) && x++ < 3) {
						attributeWeightMap.computeIfAbsent(k, w -> new DoubleAdder()).add(TagWeight.HEAVY_WEIGHT);
						attribute = StringUtils.replaceOnceIgnoreCase(path, attribute, "");
					}
				}
			}
		});
		if(logger.isTraceEnabled()) {
			Map<AttributeMatch, Double> pureWeightMap = sortByValue(attributeWeightMap.entrySet().stream()
					.collect(Collectors.toMap(x -> matches.get(x.getKey()), x -> x.getValue().doubleValue())));
			logWeightStatistics(pureWeightMap);
		}
		return attributeWeightMap.entrySet().stream().collect(Functions.allMax(
				Comparator.comparingDouble(x -> x.getValue().doubleValue()),
				Collectors.toMap(Map.Entry::getKey, x -> matches.get(x.getKey())))
		);
	}

	/**
	 * If an attribute contains a space or dash, map 3 new keys (empty, space, & dash) to the original value
	 * i.e. the value F-150 will be mapped to the keys F150, F 150, & F-150
	 *
	 * key == a space dash permutation of, value == original attribute
	 */
	public static List<String> buildSpaceDashSimilarity(List<String> attributes) {
		List<String> similarities = new ArrayList<>();
		for(String attribute : attributes) {
			similarities.addAll(buildSpaceDashSimilarity(attribute));
		}
		return similarities;
	}

	/**
	 * If an attribute contains a space or dash, map 3 new keys (empty, space, & dash) to the original value
	 * i.e. the value F-150 will be mapped to the keys F150, F 150, & F-150
	 *
	 * key == a space dash permutation of, value == original attribute
	 */
	public static List<String> buildSpaceDashSimilarity(String attribute) {
		final char SPACE = ' ', DASH = '-', EMPTY = CharUtils.NUL;
		if(StringUtils.isBlank(attribute)) {
			return new ArrayList<>();
		}
		if(StringUtils.containsAny(attribute, SPACE) || StringUtils.containsAny(attribute, DASH)) {
			return allPermutationReplacement(attribute, SPACE, DASH, EMPTY);
		} else {
			return new ArrayList<>(Collections.singletonList(attribute));
		}
	}

	/**
	 * @return A list of all permutations based on the value and given replacements.
	 * @implNote Use {@link CharUtils#NUL} to replace empty.
	 */
	private static List<String> allPermutationReplacement(String value, char... replacements) {
		List<String> permutations = new ArrayList<>();
		allPermutationReplacement(permutations, 0, value.trim().toCharArray(), replacements);
		return permutations;
	}

	private static void allPermutationReplacement(List<String> permutations, int depth, char[] value, char... replacements) {
		if(depth < value.length) {
			boolean match = false;
			for(char x : replacements) {
				if(!match && value[depth] == x) {
					for(char y : replacements) {
						value[depth] = y;
						allPermutationReplacement(permutations, depth + 1, value, replacements);
						match = true;
					}
				}
			}
			if(!match) {
				allPermutationReplacement(permutations, depth + 1, value, replacements);
			}
		} else {
			permutations.add(new String(value).replace(String.valueOf(CharUtils.NUL), ""));
		}
	}

	/**
	 * alone == not touching a letter or digit, inclusive to string start or end
	 */
	public static boolean containsLoneAttribute(String text, String attr) {
		if(StringUtils.isEmpty(text) || StringUtils.isEmpty(attr) || !StringUtils.containsIgnoreCase(text, attr)) {
			return false;
		}
		text = text.toUpperCase(Locale.ENGLISH);
		attr = attr.toUpperCase(Locale.ENGLISH);
		int attrStart = text.indexOf(attr);
		int attrFinish = text.indexOf(attr) + attr.length();
		char before = attrStart == 0 ? '~' : text.substring(attrStart - 1, attrStart).charAt(0);
		char after = attrFinish == text.length() ? '~' : text.substring(attrFinish, attrFinish + 1).charAt(0);
		return !Character.isLetter(before) && !Character.isLetter(after) && !Character.isDigit(before) && !Character.isDigit(after);
	}

	static <T> T mostFrequentValue(List<T> values, Function<List<T>, T> collisionHandler) {
		Map<T, AtomicInteger> freqMap = new HashMap<>();
		List<T> freqValues = new ArrayList<>();
		int maxFrequency = 0;
		values.forEach(v -> freqMap.computeIfAbsent(v, x -> new AtomicInteger()).incrementAndGet());
		for(Map.Entry<T, AtomicInteger> e : freqMap.entrySet()) {
			int v;
			if((v = e.getValue().get()) > maxFrequency) {
				maxFrequency = v;
			}
		}
		for(Map.Entry<T, AtomicInteger> e : freqMap.entrySet()) {
			if(e.getValue().get() == maxFrequency) {
				freqValues.add(e.getKey());
			}
		}
		if(freqValues.size() == 1) {
			return freqValues.get(0);
		} else {
			return collisionHandler.apply(freqValues);
		}
	}

	static <T> T mostFrequentValue(List<T> values) {
		if(values.isEmpty()) {
			return null;
		}
		if(values.size() == 1) {
			return values.get(0);
		}
		Map<T, AtomicInteger> freqMap = new HashMap<>();
		for(T v : values) {
			if(v != null) {
				freqMap.computeIfAbsent(v, x -> new AtomicInteger()).incrementAndGet();
			}
		}
		return freqMap.entrySet().stream()
				.max(Comparator.comparingInt(x -> x.getValue().get()))
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	public static String getGenericAttributeValue(Document document, Predicate<String> matchFinder, Predicate<String> textValidator,
	                                              Set<String> keywordsToLookFor, Set<String> keywordsToAvoid, boolean enforceValueAfterKey) {

		document = JsoupUtils.defaultStripTags(document);
		Elements matchingElements = Collector.collect(new ContainsCollectionOwnText(keywordsToLookFor), document);

		if(matchingElements.isEmpty()) {
			logger.trace("No values found for matching attributes [{}]",
					keywordsToLookFor.stream().limit(3).collect(Collectors.joining(", ")));
			return null;
		}

		// TODO: handle multiple values being in the same text (i.e. interior and exterior for color)
		String matchedValue = null;
		for(Element e : matchingElements) {
			if(keywordsToAvoid.stream().noneMatch(k -> StringUtils.containsIgnoreCase(e.outerHtml(), k))) {
				Optional<String> matchedValueOptional = getMatchingValueAfterKeyword(
						e.text(), keywordsToLookFor, matchFinder, textValidator, true, enforceValueAfterKey);
				if(matchedValueOptional.isPresent()) {
					matchedValue = matchedValueOptional.get();
					logger.trace("Found matching value [{}] in key text [{}]", matchedValue, e.text());
					break;
				}
			}
		}
		if(matchedValue != null) {
			return matchedValue;
		}

		List<Element> keyValueElements = matchingElements.stream()
				.filter(JsoupUtils.defaultTextQualityGate)
				.map(Element::parent) // get the parent so we can get all the children
				.flatMap(p -> p.children().stream()) // then get all the children for that parent, one of which should contain the actual value
				.filter(JsoupUtils.defaultTextQualityGate)
				.filter(e -> keywordsToAvoid.stream()
						.noneMatch(k -> StringUtils.containsIgnoreCase(e.outerHtml(), k)))
				.distinct()
				.collect(Collectors.toList());

		for(int x = 0; x < keyValueElements.size(); x++) {
			String keyText = keyValueElements.get(x).text();

			// if the key element does not contain the keyword we are looking for, abort
			if(keywordsToLookFor.stream().noneMatch(s -> StringUtils.containsIgnoreCase(keyText, s))) {
				continue;
			}
			// check if the key contains the value as well, i.e. "Mileage: XX,XXX"
			logger.trace("Checking if key text [{}] contains value match...", keyText);
			Optional<String> keyOptional = getMatchingValueAfterKeyword(
					keyText, keywordsToLookFor, matchFinder, textValidator, true, enforceValueAfterKey);
			if(keyOptional.isPresent()) {
				matchedValue = keyOptional.get();
				logger.trace("Found matching value [{}] in key text [{}]", matchedValue, keyText);
				break;
			}
			// get the adjacent child (next in the iteration) and check for a match
			if(x + 1 < keyValueElements.size()) {
				String valueText = keyValueElements.get(x + 1).text();
				if(StringUtils.isNotEmpty(valueText)) {
					logger.trace("Checking value text [{}] for match...", valueText);
					Optional<String> valueOptional = getMatchingValueAfterKeyword(
							valueText, keywordsToLookFor, matchFinder, textValidator, false, enforceValueAfterKey);
					if(valueOptional.isPresent()) {
						matchedValue = valueOptional.get();
						break;
					}
				}
			}
		}
		if(matchedValue == null) {
			logger.trace("No values found for matching attributes [{}]",
					keywordsToLookFor.stream().limit(3).collect(Collectors.joining(", ")));
		} else {

			logger.trace("Value found [{}] for matching attributes [{}]",
					ConsoleColors.green(matchedValue), keywordsToLookFor.stream().limit(3).collect(Collectors.joining(", ")));
		}
		return matchedValue;
	}

	public static Optional<String> getMatchingValueAfterKeyword(String text, Set<String> keywordsToLookFor,
	                                                            Predicate<String> matchFinder, Predicate<String> textValidator,
	                                                            boolean traverseSpaces, boolean enforceValueAfterKey) {
		if(!textValidator.test(text)) {
			return Optional.empty();
		}
		int keywordIndex = getFirstKeywordIndex(text, keywordsToLookFor);
		if(traverseSpaces) {
			for(String possibleValue : text.split(" ")) {
				if(StringUtils.isNotBlank(possibleValue) && matchFinder.test(possibleValue)
						&& (!enforceValueAfterKey || text.toUpperCase().indexOf(possibleValue.toUpperCase()) > keywordIndex)) {
					return Optional.of(possibleValue);
				}
			}
		} else if(StringUtils.isNotEmpty(text) && matchFinder.test(text)) {
			return Optional.of(text);
		}
		return Optional.empty();
	}

	private static int getFirstKeywordIndex(String text, Set<String> keywordsToLookFor) {
		String textToAnalyze = text.toUpperCase();
		return keywordsToLookFor.stream()
				.map(String::toUpperCase)
				.filter(textToAnalyze::contains)
				.findFirst()
				.map(textToAnalyze::indexOf)
				.orElse(-1);
	}

	public static Optional<String> getLargestValidValueInsideString(String containingValue, Predicate<String> matcher) {
		return getLargestValidValueInsideString(containingValue, " ", matcher);
	}

	public static Optional<String> getLargestValidValueInsideString(String containingValue, String spliterator, Predicate<String> matcher) {
		String[] split = containingValue.split(spliterator);
		int pass = 1;
		for(int x = split.length; x > 0; x--) {
			for(int y = 0; y < pass; y++) {
				StringBuilder builder = new StringBuilder();
				for(int z = 0; z < x; z++) {
					builder.append(split[z + y]).append(" ");
				}
				String potential = builder.toString().trim();
				if(matcher.test(potential)) {
					return Optional.of(potential);
				}
			}
			pass++;
		}
		return Optional.empty();
	}

	public static <K, V extends Comparable<?>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(e1, e2) -> e1,
						LinkedHashMap::new
				));
	}

	private static void logWeightStatistics(Map<AttributeMatch, Double> attributeWeightMap) {
		if(!logger.isDebugEnabled()) {
			return;
		}
		String format = "%1$-32s%2$-8s%n";
		System.out.format("%n" + format, "Attribute Analyzed", "Rank");
		System.out.println("─────────────────────────────── ───────────");

		if(!attributeWeightMap.isEmpty()) {
			double[] total = {0};
			double[] max = {0};
			attributeWeightMap.forEach((k, v) -> {
				total[0] += v;
				if(v > max[0]) {
					max[0] = v;
				}
			});
			if(total[0] > 0) {
				attributeWeightMap.entrySet().stream().limit(10).forEach(e -> {
					double v = e.getValue();
					boolean isMax = max[0] == v;
					String vStr = String.format("%.2f%%", (v / total[0]) * 100);
					System.out.format(format, e.getKey().getAttribute(), (isMax ? ConsoleColors.green(vStr) : vStr));
				});
			} else {
				System.out.println("Attribute occurrences did not pass quality gates");
			}
		} else {
			System.out.println("No attribute occurrences found");
		}
		System.out.println();
	}

	public static Integer reduceToInteger(String value) {
		Double d = reduceToDouble(value);
		if(d != null) {
			return d.intValue();
		}
		return null;
	}

	public static Double reduceToDouble(String value) {
		if(value == null || value.equals("")) {
			return null;
		}
		Double d;
		try {
			d = Double.parseDouble(value.replaceAll("[^\\d.]", ""));
		} catch(NumberFormatException e) {
			return null;
		}
		return d;
	}

	public static boolean isAlphaNumericSpaceDash(String value) {
		if(StringUtils.isEmpty(value)) {
			return false;
		}
		for(int x = 0; x < value.length(); x++) {
			char c = value.charAt(x);
			if(!Character.isLetter(c) && !Character.isDigit(c) && c != ' ' && c != '-') {
				return false;
			}
		}
		return true;
	}
}
