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

import com.findupon.utilities.AttributeMatch;
import com.findupon.utilities.PermutableAttribute;
import com.findupon.utilities.SimplePermutableAttribute;
import com.google.common.base.Stopwatch;
import com.findupon.commons.building.*;
import com.findupon.commons.dao.ProductDao;
import com.findupon.commons.entity.building.TagWeight;
import com.findupon.commons.entity.product.attribute.Body;
import com.findupon.commons.entity.product.attribute.Drivetrain;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.Transmission;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.entity.product.automotive.AutomobileMake;
import com.findupon.commons.entity.product.automotive.AutomobileModel;
import com.findupon.commons.entity.product.automotive.AutomobileTrim;
import com.findupon.commons.exceptions.SoldException;
import com.findupon.commons.utilities.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.nodes.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AutomotiveGatherer extends AbstractProductGatherer<Automobile> {

	@Autowired private AutomobileAttributeMatcher attributeMatcher;

	// this might be better suited as a map assigning a weight value to each keyword
	private static final Set<String> edgePriorityKeywords = new HashSet<>(Arrays.asList(
			"inventory", "sitemap", "new", "used", "preowned", "certified", "for-sale", "details", "vehicle", "vin", "vuid"));
	private static final Set<String> parentPriorityKeywords = new HashSet<>(Arrays.asList("inventory", "sitemap"));
	private static final String[] keywordsToAvoid = {"financ", "service", "maintenance", "parts", "about", "contact", "staff",
			"career", "employ", "direction", "address", "blog", "review", "hour", "schedule", "recall", "login", "register",
			"rss", "appointment", "application", "form", "email", "phone", "privacy", "sell", "trade", "calculator",
			"collision", "gallery", "complaint", "program", "protection", "warranty", "credit", "loans", "image", "photo", "video",
			"award", "accolade", "faq", "research", "inspection", "news", "pdf", "tel:", "misc", "survey", "sticker", "testdrive",
			"incentive", "offer", "profile", "report", "carfax", "history", "partnet", "quote", "privacy", "policy", "tire", "appt",
			"account", "meet", "affiliate", "accessor", "saved", "kbb", "availab", "choice", "insurance", "consign",
			"benefits", "ownercenter", "valueyourtrade", "loan", "lease", "social", "facebook", "twitter", "google", "safety",
			"check", "brake", "article", "build", "video", "inquir", "edmunds", "kelly", "sticker", "fuel", "print", "popup",
			"legal", "coupon", "business", "commercial", "lube"};

	private static final Set<String> automotiveIdentifiers = new HashSet<>(Arrays.asList(
			"vin", "vuid", "vehicleid", "vehicle_id", "vehicle-id",
			"automobileid", "automobile-id", "automobile_id",
			"autoid", "auto-id", "auto_id"));

	private static final Set<String> mileageKeywordsMatches = new HashSet<>(Arrays.asList("mileage", "miles", "odometer"));
	private static final Set<String> mileageKeywordsAvoid = new HashSet<>(Arrays.asList("warranty", "coverage", "month", "year", "mpg", "fuel"));
	private static final Set<String> stockNumberKeywordsMatches = new HashSet<>(Arrays.asList("stock", "stock #", "stock#", "stock number"));
	private static final Set<String> stockNumberKeywordsAvoid = new HashSet<>();


	@Override
	protected Comparator<String> edgePriority() {
		return (s1, s2) -> {
			boolean s1containsKeyword = false;
			for(String k : edgePriorityKeywords) {
				if(StringUtils.containsIgnoreCase(s1, k)) {
					s1containsKeyword = true;
					break;
				}
			}
			boolean priority1 = s1containsKeyword || AutoParsingOperations.getContainingVin(s1).isPresent();
			boolean s2containsKeyword = false;
			for(String k : edgePriorityKeywords) {
				if(StringUtils.containsIgnoreCase(s2, k)) {
					s2containsKeyword = true;
					break;
				}
			}
			boolean priority2 = s2containsKeyword || AutoParsingOperations.getContainingVin(s2).isPresent();
			return priority1 && !priority2 ? -1 : priority1 == priority2 ? 0 : 1;
		};
	}

	@Override
	protected Comparator<String> parentPriority() {
		return (s1, s2) -> {
			boolean priority1 = parentPriorityKeywords.stream().anyMatch(s -> StringUtils.containsIgnoreCase(s1, s));
			boolean priority2 = parentPriorityKeywords.stream().anyMatch(s -> StringUtils.containsIgnoreCase(s2, s));
			return priority1 && !priority2 ? -1 : priority1 == priority2 ? 0 : 1;
		};
	}

	@Override
	protected String[] keywordsToAvoid() {
		return keywordsToAvoid;
	}

	@Override
	public void createProductIfFound(Document document) {
		String identifier = ScoutServices.getUniqueIdentifierFromUrl(document.location(), automotiveIdentifiers, AutoParsingOperations.vinRecognizer());
		Automobile automobile = null;

		if(identifier != null && StringUtils.containsIgnoreCase(document.html(), identifier)) {
			if(!insensitiveProductIds.add(identifier)) {
				logger.trace("[AutomotiveGatherer] - Identifier [{}] already collected, not saving", identifier);
				return;
			}
			URL url = ScoutServices.getUrlFromString(document.location(), false);
			if(url == null) {
				logger.warn("[AutomotiveGatherer] - Invalid URL from automobile document location [{}]", document.location());
				return;
			}
			automobile = buildAutomobile(document, JsoupUtils.defaultRemoveUnneeded(document.clone()), url, identifier);
		}

		if(automobile != null) {
			/* Check if already built by VIN, if the VIN is not the identifier */
			if(!StringUtils.equalsIgnoreCase(automobile.getListingId(), automobile.getVin()) && !insensitiveProductIds.add(automobile.getVin())) {
				logger.trace("[AutomotiveGatherer] - VIN [{}] already collected, not saving", automobile.getVin());
				return;
			}
			if(currentDataSource != null) {
				// currentDataSource will not be null during a full generic gathering
				automobile.setDealerUrl(currentDataSource.getUrl());
				automobile.setDataSourceId(currentDataSource.getId());
			}
			productUtils.mergeExistingProductAndRefreshAggregates(automobile);

			if(products.add(automobile)) {
				builtProducts.increment();
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				persistAndClear();
			}
		}
	}

	@Override
	protected long revisit() {
		Stopwatch stopwatch = Stopwatch.createStarted();
		List<Automobile> existingCars = automobileDao.findAllByDataSourceId(currentDataSource.getId());
		logger.debug(nodePre() + "Refreshing [{}] products for dealer [{}]", existingCars.size(), currentDataSource.getUrl());
		Stopwatch refreshTimer = Stopwatch.createStarted();
		ExecutorService revisitService = Executors.newFixedThreadPool(1);

		existingCars.stream().<Runnable>map(e -> () -> {
			insensitiveAnalyzedLinks.add(e.getUrl());
			insensitiveVisitedUrls.add(e.getUrl());
			insensitiveProductIds.add(e.getListingId());
			insensitiveProductIds.add(e.getVin());
			if(checkAbort()) {
				return;
			}
			Document document = download(e.getUrl()); // should this still abort? think so...
			Automobile refresh;
			boolean remove = false;
			if(document == null) {
				remove = true;
			} else {
				refresh = buildProduct(document);
				if(refresh == null) {
					remove = true;
				} else {
					Date now = new Date();
					refresh.setDealerUrl(currentDataSource.getUrl());
					refresh.setDataSourceId(currentDataSource.getId());
					productUtils.mergeExistingProductAndRefreshAggregates(refresh, e, now);

					// in case the listing ID or VIN was updated
					insensitiveProductIds.add(refresh.getListingId());
					insensitiveProductIds.add(refresh.getVin());
					products.add(refresh);
					builtProducts.increment();

					if(products.size() >= ProductDao.productWriteThreshold) {
						persistAndClear();
					}
				}
			}
			if(remove) {
				logger.debug("Automobile [{}] was sold, missing, or error-ed out. Refreshing aggregates and transitioning to history.", e.getId());
				productUtils.removeProductAndRefreshAggregates(e);
				removedProducts.increment();
			}
		}).forEach(revisitService::execute);

		shutdownService(revisitService, () -> {}, "revisit");

		refreshTimer.stop();
		logger.debug(nodePre() + "Refresh complete for [{}] products in [{}] for dealer [{}]", existingCars.size(), TimeUtils.format(refreshTimer), currentDataSource.getUrl());
		existingCars.clear();
		return stopwatch.elapsed(TimeUnit.MILLISECONDS);
	}

	private boolean setMakeModelTrimYear(Automobile automobile, Document document) {
		URL url = ScoutServices.getUrlFromString(document.location(), false);
		Map<Integer, AttributeMatch> makeMatches = AttributeOperations.matchTransformer(attributeMatcher.getFullAttributeMap(), document, url);
		if(makeMatches.isEmpty()) {
			return false;
		}
		if(makeMatches.size() > 1) {
			Integer keyToRemove = getDoubleMakeModelKey(makeMatches.keySet());
			if(keyToRemove != null) {
				makeMatches.remove(keyToRemove);
			}
			if(makeMatches.size() > 1) {
				logger.debug("More than one make found! [{}]",
						makeMatches.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")));
			}
		}
		Map.Entry<Integer, AttributeMatch> makeEntry = makeMatches.entrySet().iterator().next();
		AutomobileMake make = (AutomobileMake)attributeMatcher.getFullAttributeMap().get(makeEntry.getKey());
		automobile.setMakeId(makeEntry.getKey());
		automobile.setMake(make.getAttribute());

		Map<Integer, AttributeMatch> modelMatches = AttributeOperations.matchTransformer(make.getChildren(), document, url);
		Map.Entry<Integer, AttributeMatch> modelEntry;
		if(modelMatches.isEmpty()) {
			logger.debug("No model match found for make: {}", make.getAttribute());
			return false;
		} else if(modelMatches.size() > 1) {
			logger.trace(ConsoleColors.yellow("Determining model from tied attributes: [{}]"),
					modelMatches.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")));
			modelEntry = modelCollisionHandler(make, modelMatches);
		} else {
			modelEntry = modelMatches.entrySet().iterator().next();
		}
		if(modelEntry == null) {
			return false;
		}
		AutomobileModel model = (AutomobileModel)make.getChildren().get(modelEntry.getKey());
		automobile.setModelId(modelEntry.getKey());
		automobile.setModel(model.getAttribute());

		setAutomobileTrim(automobile, model, modelEntry.getValue().getMatchingElements(), url);
		setAutomobileYear(automobile, model, document, url);

		logger.trace("Make [{}] Model [{}] Trim [{}] Year [{}] URL [{}]",
				automobile.getMake(), automobile.getModel(), automobile.getTrim(), automobile.getYear(), document.location());
		return automobile.getMakeId() != null & automobile.getModelId() != null;
	}

	private void setAutomobileTrim(Automobile automobile, AutomobileModel model, Collection<Element> elements, URL url) {
		Map<Integer, PermutableAttribute> trims = model.getChildren();
		Map<Integer, AttributeMatch> trimMatches = AttributeOperations.matchTransformer(trims, elements, url);
		if(trimMatches.isEmpty()) {
			return;
		} else if(trimMatches.size() > 1) {
			// more than 1 trim match, TODO: look for occurrences based on split i.e. 750i xDrive, 750i

			logger.debug("Determining trim from tied attributes: {}",
					trimMatches.entrySet().stream().map(e -> e.getValue().getAttribute()).collect(Collectors.joining(", ")));
			// trimMatches.sort(Comparator.comparing(x -> x.getAttribute().length()));
		}
		Map.Entry<Integer, AttributeMatch> trimEntry = trimMatches.entrySet().iterator().next();
		AutomobileTrim trim = (AutomobileTrim)trims.get(trimEntry.getKey());
		automobile.setTrimId(trimEntry.getKey());
		automobile.setTrim(trim.getAttribute());
	}

	private void setAutomobileYear(Automobile automobile, AutomobileModel model, Document document, URL url) {
		Map<Integer, PermutableAttribute> yearMap = new HashMap<>();
		IntStream.rangeClosed(model.getMinYear(), model.getMaxYear())
				.forEach(year -> yearMap.put(year, new SimplePermutableAttribute(String.valueOf(year))));
		AttributeOperations.matchTransformer(yearMap, document, url).entrySet().stream()
				.findFirst()
				.ifPresent(e -> automobile.setYear(e.getKey()));
	}

	private Map.Entry<Integer, AttributeMatch> modelCollisionHandler(AutomobileMake make, Map<Integer, AttributeMatch> modelMatches) {
		modelMatches = AttributeOperations.sortByValue(modelMatches);
		Map<Integer, PermutableAttribute> directModels = modelMatches.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, v -> make.getChildren().get(v.getKey())));
		Map<Integer, String> differenceMap = new HashMap<>();
		directModels.forEach((k, v) -> {
			String v1 = v.getAttribute();
			directModels.entrySet().stream().map(e -> e.getValue().getAttribute())
					.filter(v2 -> !v2.equals(v1))
					.forEachOrdered(v2 -> AttributeOperations.getLargestValidValueInsideString(
							v2, s -> StringUtils.containsIgnoreCase(v1, v2)).ifPresent(o ->
							differenceMap.putIfAbsent(k, StringUtils.trim(StringUtils.remove(v1, v2)))
					));
		});
		List<Element> matchingElements = modelMatches.entrySet().stream()
				.flatMap(e -> e.getValue().getMatchingElements().stream())
				.collect(Collectors.toList());
		AtomicBoolean matchFound = new AtomicBoolean();
		differenceMap.forEach((k, v) -> {
			if(!matchFound.get()) {
				LongAdder diffs = new LongAdder();
				matchingElements.forEach(e -> {
					if(AttributeOperations.containsLoneAttribute(e.ownText(), v)) {
						diffs.increment();
					}
				});
				long c = diffs.longValue();
				logger.trace("Diff count for [{}]: {}", v, c);
				if(c == 0) {
					directModels.remove(k);
				} else if(c > 1) {
					directModels.entrySet().removeIf(e -> !e.getKey().equals(k));
					matchFound.set(true); // TODO: not have this short out, handle multiple diffs
				}
			}
		});
		logger.trace("Final model matches [{}]", directModels.entrySet().stream()
				.map(Map.Entry::getValue).map(PermutableAttribute::getAttribute).collect(Collectors.joining(", ")));
		return modelMatches.entrySet().stream()
				.filter(e -> e.getKey().equals(directModels.entrySet().iterator().next().getKey()))
				.findFirst()
				.orElse(null);
	}

	private Integer getDoubleMakeModelKey(Set<Integer> makeKeys) {
		if(makeKeys.size() == 2) {
			// filter makes that also made models as another make... le sigh
			Integer hyundaiKey = attributeMatcher.getMakeId("Hyundai");
			Integer genesisKey = attributeMatcher.getMakeId("Genesis");
			Integer dodgeKey = attributeMatcher.getMakeId("Dodge");
			Integer ramKey = attributeMatcher.getMakeId("Ram");
			if(makeKeys.stream().allMatch(e -> hyundaiKey.equals(e) || genesisKey.equals(e))) {
				return genesisKey;
			} else if(makeKeys.stream().allMatch(e -> dodgeKey.equals(e) || ramKey.equals(e))) {
				return dodgeKey;
			}
		}
		return null;
	}

	public boolean setMakeModelTrimYear(Automobile automobile, String text) {
		if(StringUtils.isBlank(text)) {
			return false;
		}
		Map<Integer, PermutableAttribute> makeMatches = AttributeOperations.matchFromText(attributeMatcher.getFullAttributeMap(), text);
		if(makeMatches.isEmpty()) {
			return false;
		}
		if(makeMatches.size() > 1) {
			Integer keyToRemove = getDoubleMakeModelKey(makeMatches.keySet());
			if(keyToRemove != null) {
				makeMatches.remove(keyToRemove);
			}
		}
		Integer makeKey = makeMatches.entrySet().iterator().next().getKey();
		AutomobileMake make = (AutomobileMake)attributeMatcher.getFullAttributeMap().get(makeKey);
		automobile.setMakeId(makeKey);
		automobile.setMake(make.getAttribute());

		Map<Integer, PermutableAttribute> modelMatches = AttributeOperations.matchFromText(make.getChildren(), text);
		if(modelMatches.isEmpty()) {
			return false;
		}
		Integer modelKey = modelMatches.entrySet().iterator().next().getKey();
		AutomobileModel model = (AutomobileModel)make.getChildren().get(modelKey);
		automobile.setModelId(modelKey);
		automobile.setModel(model.getAttribute());

		Map<Integer, PermutableAttribute> trimMatches = AttributeOperations.matchFromText(model.getChildren(), text);
		if(!trimMatches.isEmpty()) {
			Integer trimId = trimMatches.entrySet().iterator().next().getKey();
			AutomobileTrim trim = (AutomobileTrim)model.getChildren().get(trimId);
			automobile.setTrimId(trimId);
			automobile.setTrim(trim.getAttribute());
		}
		Map<Integer, PermutableAttribute> yearMap = new HashMap<>();
		IntStream.rangeClosed(model.getMinYear(), model.getMaxYear())
				.forEach(year -> yearMap.put(year, new SimplePermutableAttribute(String.valueOf(year))));
		AttributeOperations.matchFromText(yearMap, text).entrySet().stream()
				.findFirst()
				.ifPresent(e -> automobile.setYear(e.getKey()));
		return true;
	}

	@Override
	public Automobile buildProduct(Document document) {
		if(document == null) {
			logger.warn("[AutomotiveGatherer] - Null document passed into build method");
			return null;
		}

		/* URL parsing and validation */
		URL url = ScoutServices.getUrlFromString(document.location(), false);
		if(url == null) {
			logger.error("[AutomotiveGatherer] - Invalid URL from document location... how did this even happen [{}]", document.location());
			return null;
		}

		/* Identifier (listing ID) */
		String identifier = ScoutServices.getUniqueIdentifierFromUrl(url.toString(), automotiveIdentifiers, AutoParsingOperations.vinRecognizer());
		if(identifier == null || !StringUtils.containsIgnoreCase(document.html(), identifier)) {
			logger.trace("[AutomotiveGatherer] - Missing identifier in URL or identifier not found in HTML [{}]", url.toString());
			return null;
		}
		return buildAutomobile(document, JsoupUtils.defaultRemoveUnneeded(document.clone()), url, identifier);
	}

	public Automobile buildAutomobile(Document originalDocument, Document strippedDocument, URL url, String identifier) {
		return buildAutomobile(originalDocument, strippedDocument, url, identifier, null);
	}

	public Automobile buildAutomobile(Document originalDocument, Document strippedDocument, URL url, String identifier, String vin) {

		Automobile automobile = new Automobile(url.toString());

		if(!setMakeModelTrimYear(automobile, strippedDocument)) {
			logger.debug("[AutomotiveGatherer] - No make, model, year found [{}]", automobile.getUrl());
			return null;
		}

		/* Listing ID & VIN */
		automobile.setListingId(identifier);

		if(AutoParsingOperations.vinRecognizer().test(vin)) {
			automobile.setVin(vin.toUpperCase(Locale.ENGLISH));
		} else if(AutoParsingOperations.vinRecognizer().test(identifier)) {
			vin = identifier.toUpperCase(Locale.ENGLISH);
			automobile.setVin(vin);
			automobile.setListingId(vin); // set it again upper case to avoid discrepancies
		} else {
			// look in the dom for a vin
			// TODO: IMPROVE THIS *********************************
			JsoupUtils.streamText(strippedDocument)
					.map(s -> s.replace(",", "").replace("-", ""))
					.filter(AutoParsingOperations.vinRecognizer())
					.findFirst()
					.ifPresent(v -> automobile.setVin(v.toUpperCase()));
		}

		if(automobile.getVin() == null) {
			logger.debug("[AutomotiveGatherer] - No VIN found, abort! [{}]", url.toString());
			return null;
		}


		/* Loose Sold Recognition */
		if(StringUtils.containsIgnoreCase(url.getPath(), "sold")) {
			logger.trace("[AutomotiveGatherer] - Car was picked up as sold from the URL [{}]", url.toString());
			return null;
		}


		/* Price */
		String price;
		try {
			price = PriceOperations.getPrice(strippedDocument, p -> autoPriceScrubber(p, automobile));
		} catch(SoldException e) {
			logger.debug("[AutomotiveGatherer] - Car was picked up as sold from the product builder [{}]", url.toString());
			return null;
		}
		if(price != null) {
			try {
				automobile.setPrice(new BigDecimal(price));
			} catch(NumberFormatException e) {
				logger.trace("[AutomotiveGatherer] - Could not determine price, abort! [{}]", url.toString(), e);
				return null;
			}
		} else {
			automobile.setPrice(null);
		}


		/* Mileage */
		String mileageStr = AttributeOperations.getGenericAttributeValue(strippedDocument,
				s -> s.matches(RegexConstants.COMMA_NUMBER_MATCH)
						&& !StringUtils.equalsIgnoreCase(s.replace(",", "").trim(), String.valueOf(automobile.getYear())),
				s -> s.length() < 32,
				mileageKeywordsMatches,
				mileageKeywordsAvoid,
				false);

		if(mileageStr != null) {
			mileageStr = mileageStr.replace(",", "");
			if(NumberUtils.isDigits(mileageStr)) {
				int mileage = 0;
				try {
					mileage = Integer.parseInt(mileageStr);
				} catch(NumberFormatException e) {
					logger.warn("[AutomotiveGatherer] - Invalid mileage str", e);
				}
				if(mileage >= 0 && mileage <= 500_000) {
					automobile.setMileage(mileage);
				}
			}
		} else if(StringUtils.containsIgnoreCase(url.getPath(), "new") || (automobile.getYear() != null && automobile.getYear() >= 2018)) {
			// if we haven't found mileage at this point & loosely believe the car to be new, set the miles to 0
			automobile.setMileage(0);
		} else {
			automobile.setMileage(null);
		}


		/* Stock Number */
		String stockNumber = AttributeOperations.getGenericAttributeValue(strippedDocument,
				s -> NumUtils.isDigits(s) || ScoutServices.isLooseHash(s),
				s -> s.length() > 4 && s.length() < 32,
				stockNumberKeywordsMatches,
				stockNumberKeywordsAvoid,
				true);
		automobile.setStockNumber(stockNumber);


		/* Address */
		AddressOperations.getAddress(originalDocument).ifPresent(a -> a.setAutomobileAddress(automobile));


		/* Image(s) */
		String imageUrl = ImageOperations.getMainImageUrl(strippedDocument, automobile.getListingId(), automobile.getVin(), false);
		automobile.setMainImageUrl(imageUrl);


		/* Colors */
		Document formattingTagStrippedDocument = JsoupUtils.stripTags(strippedDocument, TagWeight.formattingTags);
		automobile.setExteriorColor(ColorOperations.getExteriorColor(formattingTagStrippedDocument));
		automobile.setInteriorColor(ColorOperations.getInteriorColor(formattingTagStrippedDocument));


		setAdditionalAttributes(automobile);

		/* Fuel */
		if(automobile.getFuel() == null) {
			List<Fuel> modelFuelTypes = attributeMatcher.getModelFuelTypes(automobile.getModelId());
			if(modelFuelTypes.isEmpty()) {
				modelFuelTypes.addAll(Arrays.asList(Fuel.values()));
			}
			String fuelStr = AttributeOperations.getGenericAttributeValue(strippedDocument,
					s -> modelFuelTypes.stream()
							.flatMap(f -> f.getAllowedMatches().stream())
							.anyMatch(fm -> AttributeOperations.containsLoneAttribute(s, fm)),
					s -> s.length() > 2 && s.length() < 32,
					new HashSet<>(Collections.singletonList("fuel")),
					new HashSet<>(),
					true);
			automobile.setFuel(Fuel.of(fuelStr));
		}

		/* Transmission */
		if(automobile.getTransmission() == null) {
			List<Transmission> modelTransmissionTypes = attributeMatcher.getModelTransmissionTypes(automobile.getModelId());
			if(modelTransmissionTypes.isEmpty()) {
				modelTransmissionTypes.addAll(Arrays.asList(Transmission.values()));
			}
			String transStr = AttributeOperations.getGenericAttributeValue(strippedDocument,
					s -> modelTransmissionTypes.stream()
							.flatMap(f -> f.getAllowedMatches().stream())
							.anyMatch(fm -> AttributeOperations.containsLoneAttribute(s, fm)),
					s -> s.length() > 2 && s.length() < 32,
					new HashSet<>(Collections.singletonList("transmission")),
					new HashSet<>(),
					true);
			automobile.setTransmission(Transmission.of(transStr));
		}

		/* Drivetrain */
		if(automobile.getDrivetrain() == null) {
			List<Drivetrain> modelDrivetrainTypes = attributeMatcher.getModelDrivetrainTypes(automobile.getModelId());
			if(modelDrivetrainTypes.isEmpty()) {
				modelDrivetrainTypes.addAll(Arrays.asList(Drivetrain.values()));
			}
			String driveStr = AttributeOperations.getGenericAttributeValue(strippedDocument,
					s -> modelDrivetrainTypes.stream()
							.flatMap(f -> f.getAllowedMatches().stream())
							.anyMatch(fm -> AttributeOperations.containsLoneAttribute(s, fm)),
					s -> s.length() > 2 && s.length() < 32,
					new HashSet<>(Arrays.asList("drivetrain", "drive type")),
					new HashSet<>(),
					true);
			automobile.setDrivetrain(Drivetrain.of(driveStr));
		}

		/* Body */
		if(automobile.getBody() == null) {
			List<Body> modelBodyTypes = attributeMatcher.getModelBodyTypes(automobile.getModelId());
			if(modelBodyTypes.isEmpty()) {
				modelBodyTypes.addAll(Arrays.asList(Body.values()));
			}
			String bodyStr = AttributeOperations.getGenericAttributeValue(strippedDocument,
					s -> modelBodyTypes.stream()
							.flatMap(f -> f.getAllowedMatches().stream())
							.anyMatch(fm -> AttributeOperations.containsLoneAttribute(s, fm)),
					s -> s.length() > 2 && s.length() < 32,
					new HashSet<>(Collections.singletonList("body")),
					new HashSet<>(),
					true);
			automobile.setBody(Body.of(bodyStr));
		}

		logger.trace("[AutomotiveGatherer] - Built {}", automobile.toLog());
		return automobile;
	}

	public void setAdditionalAttributes(Automobile automobile) {
		String modelTrim = StringUtils.defaultString(automobile.getModel()) + " " + StringUtils.defaultString(automobile.getTrim());
		if(automobile.getFuel() == null) {
			List<Fuel> modelFuelTypes = attributeMatcher.getModelFuelTypes(automobile.getModelId());
			if(modelFuelTypes.size() == 1) {
				automobile.setFuel(modelFuelTypes.get(0));
			} else {
				if(modelFuelTypes.isEmpty()) {
					modelFuelTypes.addAll(Arrays.asList(Fuel.values()));
				}
				// trim-make specific checks
				if(StringUtils.equalsIgnoreCase(automobile.getMake(), "BMW")) {
					if(StringUtils.isNotEmpty(automobile.getTrim())) {
						if(automobile.getTrim().matches("^[\\d]{3}[e]$")) {
							automobile.setFuel(Fuel.HYBRID);
						} else if(automobile.getTrim().matches("^[\\d]{3}[d]$")) {
							automobile.setFuel(Fuel.DIESEL);
						}
					}
				}
				if(automobile.getFuel() == null) {
					fuelSetter:
					for(Fuel fuel : modelFuelTypes) {
						for(String match : fuel.getAllowedMatches()) {
							if(AttributeOperations.containsLoneAttribute(modelTrim, match)) {
								automobile.setFuel(fuel);
								break fuelSetter;
							}
						}
					}
				}
			}
		}
		if(automobile.getTransmission() == null) {
			List<Transmission> modelTransmissionTypes = attributeMatcher.getModelTransmissionTypes(automobile.getModelId());
			if(modelTransmissionTypes.size() == 1) {
				automobile.setTransmission(modelTransmissionTypes.get(0));
			} else {
				if(modelTransmissionTypes.isEmpty()) {
					modelTransmissionTypes.addAll(Arrays.asList(Transmission.values()));
				}
				transSetter:
				for(Transmission transmission : modelTransmissionTypes) {
					for(String match : transmission.getAllowedMatches()) {
						if(AttributeOperations.containsLoneAttribute(modelTrim, match)) {
							automobile.setTransmission(transmission);
							break transSetter;
						}
					}
				}
			}
		}
		if(automobile.getDrivetrain() == null) {
			List<Drivetrain> modelDrivetrainTypes = attributeMatcher.getModelDrivetrainTypes(automobile.getModelId());
			if(modelDrivetrainTypes.size() == 1) {
				automobile.setDrivetrain(modelDrivetrainTypes.get(0));
			} else {
				if(modelDrivetrainTypes.isEmpty()) {
					modelDrivetrainTypes.addAll(Arrays.asList(Drivetrain.values()));
				}
				if(StringUtils.equalsIgnoreCase(automobile.getMake(), "BMW")) {
					if(StringUtils.isNotEmpty(automobile.getTrim())) {
						if(automobile.getTrim().matches("^[\\d]{3}xi$")) {
							automobile.setDrivetrain(Drivetrain.ALL_WHEEL_DRIVE);
						}
					}
				}
				driveSetter:
				for(Drivetrain drivetrain : modelDrivetrainTypes) {
					for(String match : drivetrain.getAllowedMatches()) {
						if(AttributeOperations.containsLoneAttribute(modelTrim, match)) {
							automobile.setDrivetrain(drivetrain);
							break driveSetter;
						}
					}
				}
			}
		}
		if(automobile.getBody() == null) {
			List<Body> modelBodyTypes = attributeMatcher.getModelBodyTypes(automobile.getModelId());
			if(modelBodyTypes.size() == 1) {
				automobile.setBody(modelBodyTypes.get(0));
			} else {
				if(modelBodyTypes.isEmpty()) {
					modelBodyTypes.addAll(Arrays.asList(Body.values()));
				}
				bodySetter:
				for(Body body : modelBodyTypes) {
					for(String match : body.getAllowedMatches()) {
						if(AttributeOperations.containsLoneAttribute(modelTrim, match)) {
							automobile.setBody(body);
							break bodySetter;
						}
					}
				}
			}
		}
	}

	private void autoPriceScrubber(List<String> priceValues, Automobile automobile) {
		if(priceValues.isEmpty()) {
			return;
		}
		Map<String, Double> doubleValues = new HashMap<>();
		priceValues.stream()
				.filter(NumberUtils::isDigits)
				.forEach(s -> doubleValues.putIfAbsent(s, Double.parseDouble(s)));
		doubleValues.forEach((k, v) -> {
			if(v < 1000) {
				priceValues.removeAll(Collections.singleton(k));
			} else {
				// scrub based on year
				if(automobile.getYear() != null) {
					int year = automobile.getYear();
					if(year >= 2019 && v < 20000
							|| year >= 2018 && v < 12000
							|| year >= 2017 && v < 8800
							|| year >= 2016 && v < 6400
							|| year >= 2015 && v < 5500
							|| year >= 2014 && v < 5000
							|| year >= 2013 && v < 4600
							|| year >= 2012 && v < 4200
							|| year >= 2011 && v < 3800
							|| year >= 2010 && v < 3600
							|| year >= 2009 && v < 3200
							|| year >= 2008 && v < 3000
							|| year >= 2007 && v < 2800
							|| year >= 2006 && v < 2400
							|| year >= 2005 && v < 2000) {
						priceValues.removeAll(Collections.singleton(k));
					}
				}
			}
		});
		AutomobileModel model = null;
		if(automobile.getMakeId() != null && automobile.getModelId() != null) {
			PermutableAttribute make = attributeMatcher.getFullAttributeMap().get(automobile.getMakeId());
			if(make != null) {
				model = (AutomobileModel)make.getChildren().get(automobile.getModelId());
			}
		}
		if(model == null) {
			logger.warn("No price range found for make/ model [{}] [{}]", automobile.getMake(), automobile.getModel());
			return;
		}
		for(Map.Entry<String, Double> entry : doubleValues.entrySet()) {
			if(entry.getValue() < model.getMinPrice() || entry.getValue() > model.getMaxPrice()) {
				priceValues.removeAll(Collections.singleton(entry.getKey()));
			}
		}
	}
}
