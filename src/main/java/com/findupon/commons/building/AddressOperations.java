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

import com.findupon.utilities.ContainsCollectionOwnText;
import com.google.common.base.Stopwatch;
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.commons.utilities.RegexConstants;
import net.sourceforge.jgeocoder.AddressComponent;
import net.sourceforge.jgeocoder.us.AddressParser;
import net.sourceforge.jgeocoder.us.AddressStandardizer;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Evaluator;
import org.quickgeo.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public final class AddressOperations {
	private static final Logger logger = LoggerFactory.getLogger(AddressOperations.class);
	private static final String[] falsePositives = {"email"};
	private static final String[] extraKeywords = {"directions", "direction"};
	private static final Set<String> streetSuffixes = loadStreetSuffixes();
	private static final Set<String> streetSuffixAbbreviations = loadStreetSuffixAbbreviations();


	public static Optional<Address> getAddress(Document document) {
		Stopwatch stopwatch = Stopwatch.createStarted();

		String[] attrValuesToRemove = {"relate", "similar", "recommend", "feature"};
		String[] addressKeywords = {"addr", "map", "direction", "location"};

		Document strippedDocument = null;
		List<Element> addressElements;
		List<String> addressStrings = new ArrayList<>();

		// look for schema.org postal address before anything else
		document.select("[itemtype*=schema.org/PostalAddress]").stream()
				.map(JsoupUtils.defaultFilteringTextMapper)
				.filter(StringUtils::isNotEmpty)
				.filter(containsState().or(containsStreet()))
				.filter(s -> getZipFromAddressLine(s).isPresent())
				.distinct()
				.map(AddressOperations::trimAddressLine)
				.filter(AddressOperations::isValidAddressLine)
				.forEach(addressStrings::add);

		// look for address strings in the base elements
		if(addressStrings.isEmpty()) {
			strippedDocument = JsoupUtils.defaultStripTags(document);
			JsoupUtils.defaultRemoveByAttributeValueContaining(strippedDocument, attrValuesToRemove);
			addressElements = JsoupUtils.defaultGetElementsByAttributeValueContaining(strippedDocument, addressKeywords);
			addressStrings = getAddressStringsFromElements(addressElements);
		}

		// look for containing text then step out at least 2 parents
		if(addressStrings.isEmpty()) {
			addressElements = Collector.collect(new ContainsCollectionOwnText(Arrays.asList(addressKeywords)), strippedDocument)
					.stream()
					.filter(e -> e.parent() != null && e.parent().parent() != null)
					.flatMap(e -> e.parent().parent().children().stream())
					.collect(Collectors.toList());
			addressStrings = getAddressStringsFromElements(addressElements);
		}

		// look for state abbreviations and zip code combinations in text
		// abbreviations are padded to loosely evaluate *alone-ness* (this is why we need a predicate evaluator)
		if(addressStrings.isEmpty()) {
			// TODO: new evaluator that takes a predicate for both single strings and collections. this will mean converting jsoup to java 8 but worth it
			addressElements = Collector.collect(new ContainsCollectionOwnText(State.paddedStateAbbreviations), strippedDocument);
			addressStrings = getAddressStringsFromElements(addressElements);
		}

		Map<String, Map<AddressComponent, String>> addressMap = new LinkedHashMap<>();
		for(String addressStr : addressStrings) {
			addressStr = trimAddressLine(addressStr);
			if(StringUtils.isBlank(addressStr)) {
				continue;
			}
			Map<AddressComponent, String> normalizedComponentMap = AddressStandardizer.normalizeParsedAddress(AddressParser.parseAddress(addressStr));
			String standardizedAddress = AddressStandardizer.toSingleLine(normalizedComponentMap);
			addressMap.putIfAbsent(standardizedAddress, normalizedComponentMap);
		}
		if(!addressMap.isEmpty()) {
			Map.Entry<String, Map<AddressComponent, String>> addressEntry = addressMap.entrySet().iterator().next();
			Map<AddressComponent, String> addressComponentMap = addressEntry.getValue();

			// if multiple addresses are found, use the first one with a zip code
			if(addressMap.size() > 1) {
				for(Map.Entry<String, Map<AddressComponent, String>> potentialAddressEntry : addressMap.entrySet()) {
					if(addressEntry.getValue().get(AddressComponent.ZIP) == null
							&& potentialAddressEntry.getValue().get(AddressComponent.ZIP) != null) {
						addressEntry = potentialAddressEntry;
						addressComponentMap = addressEntry.getValue();
					}
				}
			}
			Address address = new Address();
			String addressLine = addressEntry.getKey().trim();
			if(addressLine.length() > 255) {
				addressLine = addressLine.substring(0, 255);
			}
			address.setLine(addressLine);
			String zip = addressComponentMap.get(AddressComponent.ZIP);

			if(StringUtils.isNotEmpty(zip)) {
				setAddressFromZip(address, zip);
			} else {
				// in the rare case the parsed address component is missing a zip, try to pick it out manually
				getZipFromAddressLine(address.getLine()).ifPresent(s -> setAddressFromZip(address, s));
			}
			logger.trace(ConsoleColors.cyan("Address found! Time taken [{}] ms Zip [{}] Lat [{}] Lon [{}] Address [{}]"),
					stopwatch.elapsed(TimeUnit.MILLISECONDS),
					address.getZip(), address.getLatitude(), address.getLongitude(), address.getLine());
			return Optional.of(address);
		}
		logger.trace(ConsoleColors.red("No address found at [{}]"), document.location());
		return Optional.empty();
	}

	public static Optional<Address> getAddress(String addressLine) {
		Address address = new Address();
		Map<AddressComponent, String> addressComponentMap;

		addressLine = trimAddressLine(addressLine);
		if(addressLine == null) {
			return Optional.empty();
		}
		if(!isValidAddressLine(addressLine)) {
			Optional<String> zipOptional = getZipFromAddressLine(addressLine);
			if(zipOptional.isPresent()) {
				setAddressFromZip(address, zipOptional.get());
				return Optional.of(address);
			} else {
				// TODO: try by city
			}
			return Optional.empty();
		} else {
			try {
				addressComponentMap = AddressStandardizer.normalizeParsedAddress(AddressParser.parseAddress(addressLine));
			} catch(Exception e) {
				LoggerFactory.getLogger(AutoParsingOperations.class).debug("Could not parse address from [{}]", addressLine);
				return Optional.empty();
			}
			String standardizedAddress = AddressStandardizer.toSingleLine(addressComponentMap);
			address.setLine(standardizedAddress.trim());

			String zip = addressComponentMap.get(AddressComponent.ZIP);
			if(StringUtils.isNotEmpty(zip)) {
				setAddressFromZip(address, zip);
				return Optional.of(address);
			} else {
				Optional<String> zipOptional = getZipFromAddressLine(addressLine);
				if(zipOptional.isPresent()) {
					setAddressFromZip(address, zipOptional.get());
					return Optional.of(address);
				} else {
					// TODO: try by city
				}
				return Optional.empty();
			}
		}
	}

	private static List<String> getAddressStringsFromElements(List<Element> addressElements) {
		return addressElements
				.stream()
				.map(e -> JsoupUtils.filteringTextMapper.apply(e,
						s -> Arrays.stream(falsePositives).noneMatch(f -> StringUtils.containsIgnoreCase(s, f))))
				.filter(StringUtils::isNotEmpty)
				.filter(s -> s.split(" ").length < 32)
				.filter(containsState().or(containsStreet()))
				.filter(s -> getZipFromAddressLine(s).isPresent())
				.distinct()
				.sorted()
				.map(a -> AttributeOperations.getLargestValidValueInsideString(a, AddressOperations::isValidAddressLine))
				.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
				.collect(Collectors.toList());
	}

	private static boolean isValidAddressLine(String addressLine) {
		if(StringUtils.isBlank(addressLine)) {
			return false;
		}
		try {
			Map<AddressComponent, String> parsed = AddressParser.parseAddress(addressLine);
			if(MapUtils.isEmpty(parsed)) {
				return false;
			}
			Map<AddressComponent, String> normalized = AddressStandardizer.normalizeParsedAddress(parsed);
			if(MapUtils.isEmpty(normalized)) {
				return false;
			}
			return normalized.get(AddressComponent.ZIP) != null;
		} catch(Exception e) {
			return false;
		}
	}

	public static void setAddressFromZip(Address address, String zip) {
		getNearestPlaceFromZip(zip).ifPresent(p -> {
			address.setLatitude(Float.parseFloat(Double.toString(p.getLatitude())));
			address.setLongitude(Float.parseFloat(Double.toString(p.getLongitude())));
			address.setZip(zip);
			address.setCity(p.getPlaceName());
			address.setState(State.valueOfAbbreviation(p.getAdminCode1()));
		});
	}

	/**
	 * Use case: "Palo Alto, CA"
	 */
	public static Optional<Address> getAddressFromCityStateStr(String cityStateStr) {
		cityStateStr = StringUtils.trimToNull(cityStateStr);
		if(cityStateStr == null) {
			return Optional.empty();
		}
		State state = null;
		for(State stateToCheck : State.values()) {
			if(AttributeOperations.containsLoneAttribute(cityStateStr, stateToCheck.getAbbreviation())) {
				state = stateToCheck;
				cityStateStr = StringUtils.removeIgnoreCase(cityStateStr, stateToCheck.getAbbreviation());
			}
			if(AttributeOperations.containsLoneAttribute(cityStateStr, stateToCheck.getName())) {
				state = stateToCheck;
				cityStateStr = StringUtils.removeIgnoreCase(cityStateStr, stateToCheck.getName());
			}
			if(state != null) {
				break;
			}
		}
		if(state == null) {
			return Optional.empty();
		}
		cityStateStr = StringUtils.remove(cityStateStr, ',');
		cityStateStr = removeExtraSpacing(cityStateStr);
		cityStateStr = StringUtils.trimToEmpty(cityStateStr);
		if(cityStateStr.length() < 3) {
			return Optional.empty();
		}
		for(Place p : PostalLookupService.placesByName(cityStateStr)) {
			if(StringUtils.equalsIgnoreCase(state.getAbbreviation(), p.getAdminCode1())) {
				return Optional.of(mapPlaceToAddress(p, p.getPlaceName() + ", " + state.getAbbreviation()));
			}
		}
		return Optional.empty();
	}

	// only use this as a last resort, as there will be conflicts with multiple city names
	public static Optional<Address> getAddressFromCity(String city) {
		return getNearestPlaceFromCity(city).map(p -> mapPlaceToAddress(p, city));
	}

	public static Optional<Address> getAddressFromCityState(String city, State state) {
		return getNearestPlaceFromCityState(city, state).map(p -> mapPlaceToAddress(p, WordUtils.capitalizeFully(city) + ", " + state));
	}

	public static Optional<Place> getNearestPlaceFromZip(String zip) {
		zip = StringUtils.trimToNull(zip);
		if(zip == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(PostalLookupService.placeByZip(zip));
	}

	// only use this as a last resort, as there will be conflicts with multiple city names
	public static Optional<Place> getNearestPlaceFromCity(String city) {
		List<Place> places = getPlacesByCity(city);
		return places.isEmpty() ? Optional.empty() : Optional.of(places.get(0));
	}

	public static Optional<Place> getNearestPlaceFromCityState(String city, State state) {
		city = StringUtils.trimToNull(city);
		if(city == null || state == null) {
			return Optional.empty();
		}
		List<Place> places = getPlacesByCity(city);
		for(Place p : places) {
			if(StringUtils.equalsIgnoreCase(state.getAbbreviation(), p.getAdminCode1())
					|| StringUtils.equalsIgnoreCase(state.getName(), p.getAdminName1())) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	private static List<Place> getPlacesByCity(String city) {
		city = StringUtils.trimToNull(city);
		if(city == null) {
			return new ArrayList<>();
		}
		city = city.toLowerCase();
		if(city.startsWith("st ")) {
			city = "saint " + city.substring(3);
		} else if(city.startsWith("st. ")) {
			city = "saint " + city.substring(4);
		}
		return PostalLookupService.placesByName(city);
	}

	public static Address mapPlaceToAddress(Place place, String addressLine) {
		if(place == null) {
			return null;
		}
		Address address = new Address();
		address.setLine(addressLine);
		address.setLatitude(Float.parseFloat(Double.toString(place.getLatitude())));
		address.setLongitude(Float.parseFloat(Double.toString(place.getLongitude())));
		address.setZip(place.getPostalCode());
		address.setCity(place.getPlaceName());
		address.setState(State.valueOfAbbreviation(place.getAdminCode1()));
		return address;
	}

	private static String trimAddressLine(String addressLine) {
		String trimmedAddress = StringUtils.trimToNull(addressLine);
		if(trimmedAddress == null) {
			return null;
		}
		if(!trimmedAddress.matches(".*\\d+.*")) {
			return null; // no numbers, boink off
		}
		trimmedAddress = trimmedAddress.toUpperCase(Locale.ENGLISH);
		trimmedAddress = removePhoneNumber(trimmedAddress);
		trimmedAddress = removeExtraMatchingKeywords(trimmedAddress);
		trimmedAddress = trimBeforeStreetNumber(trimmedAddress);
		trimmedAddress = trimAfterZip(trimmedAddress);
		if(isValidAddressLine(trimmedAddress)) {
			return trimmedAddress;
		}
		return addressLine;
	}

	private static Optional<String> getZipFromAddressLine(String addressLine) {
		// replace first numbers (i.e. st num) to not throw off zip matching
		addressLine = addressLine.replaceAll("^\\d+", "").trim();
		for(String s : addressLine.split(" ")) {
			Matcher matcher = Pattern.compile(RegexConstants.ZIP_MATCH).matcher(s);
			if(matcher.find()) {
				String zip = matcher.group();
				return Optional.of(zip);
			}
		}
		return Optional.empty();
	}

	private static String removePhoneNumber(String addressLine) {
		return AttributeOperations.getLargestValidValueInsideString(addressLine,
				s -> s.matches(RegexConstants.PHONE_MATCH))
				.map(s -> addressLine.replace(s, " "))
				.map(AddressOperations::removeExtraSpacing)
				.orElse(addressLine);
	}

	private static String removeExtraMatchingKeywords(String addressLine) {
		for(String extraKeyword : extraKeywords) {
			extraKeyword = extraKeyword.toUpperCase(Locale.ENGLISH);
			if(StringUtils.contains(addressLine, extraKeyword)) {
				addressLine = addressLine.replace(extraKeyword, " ");
			}
		}
		return removeExtraSpacing(addressLine);
	}

	private static String removeExtraSpacing(String addressLine) {
		return StringUtils.replaceAll(addressLine, " +", " ");
	}

	private static String trimAfterZip(String addressLine) {
		Optional<String> zipOptional = getZipFromAddressLine(addressLine);
		if(zipOptional.isPresent()) {
			String zip = zipOptional.get();
			return addressLine.substring(0, addressLine.indexOf(zip) + zip.length());
		}
		return addressLine;
	}

	private static String trimBeforeStreetNumber(String addressLine) {
		int firstDigitIndex = firstDigitIndex(addressLine);
		if(firstDigitIndex != -1) {
			// only trim before if there are more letters after the alleged street number
			for(int x = firstDigitIndex; x < addressLine.length(); x++) {
				if(Character.isLetter(addressLine.charAt(x))) {
					return addressLine.substring(firstDigitIndex);
				}
			}
		}
		return addressLine;
	}

	private static int firstDigitIndex(String str) {
		for(int x = 0; x < str.length(); x++) {
			if(Character.isDigit(str.charAt(x))) {
				return x;
			}
		}
		return -1;
	}

	public static Predicate<String> containsState() {
		return address -> Arrays.stream(address.replace(".", "").split("[ ,]"))
				.anyMatch(((Predicate<String>)s ->
						State.stateAbbreviations.stream().anyMatch(x -> StringUtils.equalsIgnoreCase(s, x)))
						.or(s -> State.stateNames.stream().anyMatch(x -> StringUtils.equalsIgnoreCase(s, x))));
	}

	public static Predicate<String> containsStreet() {
		return address -> Arrays.stream(address.replace(".", "").split("[ ,]"))
				.anyMatch(((Predicate<String>)s ->
						streetSuffixes.stream().anyMatch(x -> StringUtils.equalsIgnoreCase(s, x)))
						.or(s -> streetSuffixAbbreviations.stream().anyMatch(x -> StringUtils.equalsIgnoreCase(s, x))));
	}

	private static Set<String> loadStreetSuffixes() {
		return new HashSet<>(Arrays.asList("Alley", "Avenue", "Bayou", "Beach", "Bend", "Boulevard", "Branch", "Bridge", "Brook", "Bypass",
				"Canyon", "Cape", "Causeway", "Center", "Circle", "Corner", "Court", "Cove", "Creek", "Crossing",
				"Crossroad", "Drive", "Expressway", "Freeway", "Garden", "Gateway", "Glen", "Grove", "Harbor",
				"Highway", "Hollow", "Island", "Isle", "Junction", "Knoll", "Lake", "Lane", "Loop", "Motorway",
				"Overpass", "Park", "Parkway", "Pass", "Passage", "Place", "Plaza", "Point", "Ranch", "River", "Road",
				"Route", "Row", "Shore", "Skyway", "Square", "Station", "Stream", "Street", "Summit", "Terrace",
				"Throughway", "Trace", "Trafficway", "Trail", "Tunnel", "Turnpike", "Underpass", "Valley", "Viaduct",
				"View", "Village", "Ville", "Vista", "Walk", "Way"));
	}

	private static Set<String> loadStreetSuffixAbbreviations() {
		return new HashSet<>(Arrays.asList("ALY", "ANX", "ARC", "AVE", "BCH", "BLVD", "BND", "BR", "BRG", "BRK", "BRKS", "BTM", "BYP", "BYU",
				"CIR", "CLB", "CLF", "CLFS", "CMN", "CMNS", "COR", "CORS", "CP", "CPE", "CRK", "CSWY", "CT", "CTR",
				"CTRS", "CTS", "CURV", "CV", "CVS", "CYN", "DR", "DRS", "EXPY", "EXT", "EXTS", "FLD", "FLDS", "FLT",
				"FLTS", "FRD", "FRDS", "FRG", "FRGS", "FRK", "FRST", "FRY", "FT", "FWY", "GDN", "GDNS", "GLN", "GLNS",
				"GRN", "GRNS", "GRV", "GRVS", "GTWY", "HBR", "HBRS", "HL", "HLS", "HOLW", "HTS", "HVN", "HWY", "INLT",
				"IS", "ISLE", "ISS", "JCT", "JCTS", "KNL", "KNLS", "KY", "KYS", "LAND", "LCK", "LCKS", "LDG", "LGT",
				"LGTS", "LK", "LKS", "LN", "LNDG", "LOOP", "MALL", "MDW", "MDWS", "ML", "MLS", "MNR", "MNRS", "MSN",
				"MT", "MTN", "MTNS", "MTWY", "OPAS", "ORCH", "OVAL", "PARK", "PASS", "PATH", "PIKE", "PKWY", "PL",
				"PLN", "PLNS", "PLZ", "PNE", "PNES", "PRT", "PRTS", "PSGE", "PT", "PTS", "RADL", "RAMP", "RD", "RDG",
				"RDGS", "RDS", "RIV", "RNCH", "ROW", "RPD", "RPDS", "RST", "RTE", "RUE", "RUN", "SHL", "SHLS", "SHR",
				"SHRS", "SKWY", "SMT", "SPG", "SPGS", "SPUR", "SQ", "SQS", "ST", "STA", "STRA", "STRM", "STS", "TER",
				"TPKE", "TRAK", "TRCE", "TRFY", "TRL", "TRLR", "TRWY", "TUNL", "UN", "UNS", "UPAS", "VIA", "VIS", "VL",
				"VLG", "VLGS", "VLY", "VLYS", "VW", "VWS", "WALK", "WALL", "WAY", "WAYS", "XING", "XRD", "XRDS"));
	}
}
