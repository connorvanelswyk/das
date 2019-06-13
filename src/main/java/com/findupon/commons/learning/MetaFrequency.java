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

package com.findupon.commons.learning;

import com.findupon.commons.bot.PageMetaBot;
import com.findupon.commons.entity.datasource.AssetType;
import com.findupon.commons.entity.datasource.PageMeta;
import com.findupon.commons.entity.learning.PossibleAssetType;
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
public class MetaFrequency implements AssetDeterminer {
	private static final Logger logger = LoggerFactory.getLogger(MetaFrequency.class);

	private static final Set<String> wordsToAvoid = new HashSet<>(Arrays.asList("and", "the", "for", "you", "your", "are",
			"our", "can", "has", "that", "any", "all", "get", "here", "where", "site", "website", "home", "page", "what",
			"not", "llc", "inc", "more", "click"));
	private final Map<String, Double> metaFrequency;
	private final double frequencyThreshold;


	@Autowired
	public MetaFrequency(JdbcTemplate jdbcTemplate) {
		List<String> metaKeywords = new ArrayList<>();
		List<PageMeta> existingPageMetas = jdbcTemplate.query("select keywords, description, title from page_meta", (rs, n) -> {
			PageMeta p = new PageMeta();
			p.setKeywords(StringUtils.trimToNull(rs.getString("keywords")));
			p.setDescription(StringUtils.trimToNull(rs.getString("description")));
			p.setTitle(StringUtils.trimToNull(rs.getString("title")));
			// hijack the result extractor to load keywords forming the frequency map
			metaKeywords.add(p.getKeywords());
			metaKeywords.add(p.getDescription());
			metaKeywords.add(p.getTitle());
			return p;
		});
		metaFrequency = getKeywordFrequencyMap(metaKeywords);
		// run existing metas through the frequency gen to determine the base threshold
		double total = existingPageMetas.stream()
				.map(this::determineFrequency)
				.reduce(0D, Double::sum);
		double average = total / existingPageMetas.size();
		frequencyThreshold = average * .4D; // allow a pretty loose threshold as these are going to be compounded
		logger.info("Calculated existing meta match threshold: [{}]", String.format("%.2f", frequencyThreshold * 100));
	}

	@Override
	public PossibleAssetType getPossibleAssetType(@NotNull Document document) {
		PageMeta pageMeta = PageMetaBot.buildPageMeta(document);
		double frequency = determineFrequency(pageMeta);

		PossibleAssetType possibleAssetType = new PossibleAssetType();
		possibleAssetType.setRelativeProbability(frequency);
		possibleAssetType.setGuess(frequency >= frequencyThreshold);
		possibleAssetType.setAssetType(AssetType.AUTOMOBILE);

		return possibleAssetType;
	}

	private double determineFrequency(PageMeta pageMeta) {
		List<String> keywords = expandKeywords(Arrays.asList(pageMeta.getKeywords(), pageMeta.getDescription(), pageMeta.getTitle()));
		if(keywords.isEmpty()) {
			return 0D;
		}
		Map<String, DoubleAdder> attributeWeightMap = new LinkedHashMap<>();
		keywords.forEach(k ->
				attributeWeightMap.computeIfAbsent(k, v ->
						new DoubleAdder()).add(MapUtils.getDouble(metaFrequency, k, 0D)));

		double[] min = {Double.MAX_VALUE};
		double[] max = {-Double.MAX_VALUE};
		double sum = attributeWeightMap.entrySet().stream()
				.map(Map.Entry::getValue)
				.map(DoubleAdder::doubleValue)
				.peek(d -> {
					if(d < min[0]) {
						min[0] = d;
					}
					if(d > max[0]) {
						max[0] = d;
					}
				})
				.reduce(0D, Double::sum);
		double normalized;
		if(sum <= 0 || attributeWeightMap.size() <= 1 || sum == min[0] || max[0] == min[0]) {
			normalized = 0; // not enough datas
		} else {
			normalized = (sum / attributeWeightMap.size() - min[0]) / (max[0] - min[0]);
		}
		if(normalized > 1 || normalized < 0) {
			normalized = 0;
		}
		return normalized;
	}

	private static List<String> expandKeywords(List<String> keywords) {
		List<String> allKeywords = new ArrayList<>();
		keywords.stream()
				.filter(StringUtils::isNotBlank)
				.map(MetaFrequency::replacePunctuation)
				.map(k -> Arrays.stream(k.split(" "))
						.map(StringUtils::trimToNull)
						.filter(Objects::nonNull)
						.filter(StringUtils::isAlpha)
						.filter(s -> s.length() >= 3)
						.map(String::toLowerCase)
						.map(ScoutServices::normalize)
						.filter(s -> wordsToAvoid.stream().map(String::toLowerCase).noneMatch(s::equals))
						.collect(Collectors.toList())).forEach(allKeywords::addAll);
		return allKeywords;
	}

	private static String replacePunctuation(String str) {
		return str.replace(",", " ").replace(".", " ").replace("'s", " ").replace("|", " ").replace("?", " ").replace("!", " ");
	}

	private static Map<String, Double> getKeywordFrequencyMap(List<String> keywords) {
		List<String> allKeywords = expandKeywords(keywords);
		int allKeywordsSize = allKeywords.size();
		Map<String, Long> occurrenceMap = new LinkedHashMap<>();
		allKeywords.stream()
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
				.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(1000)
				.forEach(e -> occurrenceMap.put(e.getKey(), e.getValue()));

		Map<String, Double> metaKeywordOccurrenceFrequency = occurrenceMap.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> 100 * ((double)e.getValue() / allKeywordsSize),
						(v1, v2) -> null,
						LinkedHashMap::new));
		if(logger.isTraceEnabled()) {
			StringBuilder top = new StringBuilder();
			metaKeywordOccurrenceFrequency.entrySet()
					.stream()
					.limit(10)
					.forEach(e -> top.append(String.format("%-32s %.10f%n", e.getKey(), e.getValue())));
			logger.trace("Total meta frequency keyword: [{}] Top hits: \n{}", String.format("%,d", allKeywordsSize), top.toString());
		}
		return metaKeywordOccurrenceFrequency;
	}
}
