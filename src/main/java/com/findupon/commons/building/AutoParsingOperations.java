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

import com.findupon.commons.entity.product.attribute.*;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;


public class AutoParsingOperations {
	private static final Set<String> motorcyleVinPrefixes = new HashSet<>(Arrays.asList("KMY", "LAE", "LBB", "LB2",
			"LFG", "LMC", "LZP", "L5N", "L8X", "VBK", "VTH", "VTT", "ZKH", "KM1", "LAN", "LBP", "LCE", "LFY", "LPR",
			"L4B", "L5Y", "SMT", "VG5", "VTL", "YU7", "538"));
	private static final BigDecimal defaultMaxPrice = new BigDecimal("5000000");
	private static final String[] singleDigitVins = {"00000000000000000", "11111111111111111", "22222222222222222", "33333333333333333",
			"44444444444444444", "55555555555555555", "66666666666666666", "77777777777777777", "88888888888888888", "99999999999999999"};


	public static void setAttribute(Automobile automobile, String key, String val) {
		key = StringUtils.trimToNull(StringUtils.remove(key, ":"));
		val = StringUtils.trimToNull(val);
		if(val == null || key == null) {
			return;
		}
		switch(key.toLowerCase(Locale.ENGLISH)) {
			case "price":
				automobile.setPrice(parsePrice(val));
				break;
			case "location":
			case "address":
				AddressOperations.getAddress(val).ifPresent(a -> a.setAutomobileAddress(automobile));
				break;
			case "mileage":
			case "miles":
				val = StringUtils.removeAll(val, "\\D+");
				if(StringUtils.isNumeric(val)) {
					int mileage = Integer.parseInt(val);
					if(mileage >= 0 && mileage <= 500_000) {
						automobile.setMileage(mileage);
					}
				}
				break;
			case "transmission":
			case "gearbox":
				automobile.setTransmission(Transmission.of(val));
				break;
			case "exterior":
			case "exterior color":
			case "color":
				if(automobile.getExteriorColor() == null) {
					automobile.setExteriorColor(ExteriorColor.of(val));
				}
				break;
			case "interior":
			case "interior color":
				if(automobile.getInteriorColor() == null) {
					automobile.setInteriorColor(InteriorColor.of(val));
				}
				break;
			case "car type":
			case "body style":
				automobile.setBody(Body.of(val));
				break;
			case "drive type":
			case "drivetype":
			case "drive":
			case "drivetrain":
				automobile.setDrivetrain(Drivetrain.of(val));
				break;
			case "vin":
				if(vinRecognizer().test(val)) {
					automobile.setVin(val.toUpperCase(Locale.ENGLISH));
				}
				break;
			case "stock":
			case "stock number":
			case "stock #":
			case "stock#":
				if(!StringUtils.containsIgnoreCase(val, "unavailable")
						&& !StringUtils.containsIgnoreCase(val, "info")
						&& ScoutServices.isLooseHash(val)) {
					automobile.setStockNumber(val);
				}
				break;
			case "mpg":
				val = val.toLowerCase(Locale.ENGLISH);
				if(StringUtils.isNumeric(val)) {
					Integer singleMpg = parseMpg(val);
					automobile.setMpgCity(singleMpg);
					automobile.setMpgHighway(singleMpg);
				} else if((val.contains("city") && val.contains("highway")) || (val.contains("cty") && val.contains("hwy"))) {
					// TODO: recognize more spliterators
					if(val.contains("/")) {
						String[] mpgs = val.split("/");
						if(mpgs.length == 2) {
							int c;
							if(val.contains("city") && val.contains("highway")) {
								c = val.indexOf("city") < val.indexOf("highway") ? 0 : 1;
							} else if(val.contains("cty") && val.contains("hwy")) {
								c = val.indexOf("cty") < val.indexOf("hwy") ? 0 : 1;
							} else {
								break;
							}
							automobile.setMpgCity(parseMpg(mpgs[c]));
							automobile.setMpgHighway(parseMpg(mpgs[1 - c]));
						}
					}
				} else if(val.contains("city") && val.contains("hwy")) {
					if(val.contains("|")) {
						try {
							String[] mpgs = val.split("\\|");
							if(mpgs.length == 3) {
								int city = Integer.parseInt(mpgs[0].replaceAll("[^0-9]", ""));
								int highway = Integer.parseInt(mpgs[1].replaceAll("[^0-9]", ""));
								if(city <= highway) {
									automobile.setMpgCity(city);
									automobile.setMpgHighway(highway);
								}
							} else if(mpgs.length == 2) {
								int city = Integer.parseInt(mpgs[0].replaceAll("[^0-9]", ""));
								int highway = Integer.parseInt(mpgs[1].replaceAll("[^0-9]", ""));
								if(city <= highway) {
									automobile.setMpgCity(city);
									automobile.setMpgHighway(highway);
								}
							}
						} catch(NumberFormatException e) {
						}
					}
				} else if(val.contains("city")) {
					automobile.setMpgCity(parseMpg(val));
				} else if(val.contains("highway")) {
					automobile.setMpgHighway(parseMpg(val));
				} else if(val.contains("hwy")) {
					automobile.setMpgHighway(parseMpg(val));
				}
				break;
		}
	}

	private static Integer parseMpg(String mpgStr) {
		if(mpgStr.contains(".")) {
			mpgStr = mpgStr.replaceAll("\\..*", "");
		}
		mpgStr = mpgStr.replaceAll("[^\\d]", "");
		if(StringUtils.isNotBlank(mpgStr) && StringUtils.isNumeric(mpgStr)) {
			Integer mpg = Integer.parseInt(mpgStr);
			if(mpg > 0 && mpg < 300) {
				return mpg;
			}
		}
		return null;
	}

	public static BigDecimal parsePrice(String priceStr) {
		return parsePrice(priceStr, null);
	}

	public static BigDecimal parsePrice(String priceStr, BigDecimal maxPrice) {
		priceStr = PriceOperations.priceStringCleaner(priceStr);
		if(StringUtils.isNumeric(priceStr)) {
			BigDecimal price;
			try {
				price = new BigDecimal(priceStr);
			} catch(NumberFormatException e) {
				return null;
			}
			if(maxPrice == null) {
				maxPrice = defaultMaxPrice;
			}
			if(price.compareTo(maxPrice) < 0 && price.compareTo(BigDecimal.ZERO) > 0) {
				return price;
			}
		}
		return null;
	}

	public static Predicate<String> vinRecognizer() {
		return v -> {
			v = StringUtils.trimToNull(v);
			if(v == null) {
				return false;
			}
			if(v.length() != 17 || !StringUtils.isAlphanumeric(v)) {
				return false;
			}
			for(String singleDigitVin : singleDigitVins) {
				if(singleDigitVin.equals(v)) {
					return false;
				}
			}
			v = v.toUpperCase(Locale.ENGLISH);
			String weights = "8765432X098765432";
			String map = "0123456789X";
			int sum = 0;
			for(int x = 0; x < v.length(); ++x) {
				sum += "0123456789.ABCDEFGH..JKLMN.P.R..STUVWXYZ".indexOf(v.charAt(x)) % 10 * map.indexOf(weights.charAt(x));
			}
			return map.charAt(sum % 11) == v.charAt(8) && isNotMotorcycleVin(v);
		};
	}

	public static Optional<String> getContainingVin(String container) {
		int length;
		if(StringUtils.isEmpty(container) || (length = container.length()) < 17) {
			return Optional.empty();
		}
		for(int x = 0; x < length - 17 + 1; x++) {
			String potential = container.substring(x, x + 17);
			if(AutoParsingOperations.vinRecognizer().test(potential)) {
				return Optional.of(potential);
			}
		}
		return Optional.empty();
	}

	private static boolean isNotMotorcycleVin(String vin) {
		String prefix = StringUtils.substring(vin, 0, 3);
		return motorcyleVinPrefixes.stream().noneMatch(mvp -> StringUtils.equalsIgnoreCase(mvp, prefix));
	}

	public static Integer parseMileage(String val) {
		val = StringUtils.removeAll(val, "\\D+");
		if(StringUtils.isNumeric(val)) {
			Integer mileage;
			try {
				mileage = Integer.parseInt(val);
			} catch(NumberFormatException e) {
				return null;
			}
			if(mileage >= 0 && mileage <= 500_000) {
				return mileage;
			}
		}
		return null;
	}
}
