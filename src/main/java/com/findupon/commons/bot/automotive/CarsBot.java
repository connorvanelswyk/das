// import com.google.common.base.Stopwatch;
// import com.findupon.commons.building.AutoParsingOperations;
// import com.findupon.commons.searchparty.ScoutServices;
// import org.apache.commons.lang3.StringUtils;
// import org.apache.commons.lang3.math.NumberUtils;
// import org.json.JSONArray;
// import org.json.JSONException;
// import org.json.JSONObject;
// import org.jsoup.nodes.Document;
// import org.jsoup.nodes.Element;
// import org.springframework.beans.factory.config.ConfigurableBeanFactory;
// import org.springframework.context.annotation.Scope;
// import org.springframework.stereotype.Component;
//
// import java.util.*;
// import java.util.concurrent.TimeUnit;
// import java.util.stream.Collectors;
//
// import static com.findupon.commons.utilities.ConsoleColors.*;
// import static com.findupon.commons.utilities.JsonUtils.optInteger;
// import static com.findupon.commons.utilities.JsoupUtils.firstChild;
//
//
// @Component
// @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
// public class CarsBot extends ListingAutomobileBot {
// 	private static final Map<Integer, Integer> yearIdMap = buildYearIdMap();
// 	private static final Map<Integer, String> makeIdMap = buildMakeIdMap();
//
//
//
// 	@Override
// 	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
// 		this.nodeId = nodeId;
// 		retrieveAllProductUrls(baseUrls.get(0));
// 		logger.debug("[CarsBot] - Last batch car URLs size [{}]", productUrls.size());
// 		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
// 	}
//
// 	private void retrieveAllProductUrls(String baseUrl) {
// 		final int CARS_PER_PAGE = 100;
// 		final int MAX_PRICE = 4_000_000;
// 		final int MAX_RESULT_SIZE = 5000;
//
// 		Integer makeId = Integer.parseInt(baseUrl);
// 		String make = makeIdMap.get(makeId);
// 		Stopwatch timer = Stopwatch.createStarted();
// 		logger.info("[CarsBot] - Retrieving all product URLs for make [{}]", make);
//
// 		for(int year = 1970; year <= Calendar.getInstance().get(Calendar.YEAR) + 1; year++) {
// 			boolean rangeNextPass = true;
// 			int range = 20000;
// 			int minPrice = 0;
// 			int maxPrice;
// 			int yearId = yearIdMap.get(year);
//
// 			while((maxPrice = minPrice + range) < MAX_PRICE) {
// 				String url = "https://www.cars.com/for-sale/searchresults.action/" +
// 						"?mkId=" + makeId +
// 						"&page=" + 1 +
// 						"&perPage=" + CARS_PER_PAGE +
// 						"&prMn=" + minPrice +
// 						"&prMx=" + maxPrice +
// 						"&rd=99999" +
// 						"&searchSource=GN_REFINEMENT" +
// 						"&sort=price-lowest" +
// 						"&yrId=" + yearId +
// 						"&zc=94301";
//
// 				Document document = download(url);
// 				if(sleep()) {
// 					return;
// 				}
// 				if(document == null) {
// 					logger.warn("Unable to retrieve total listings document, ABORT!");
// 					return;
// 				}
// 				Element srpHeader = firstChild(document.select(".srp-header"));
// 				if(srpHeader == null) {
// 					logger.warn("Unable to retrieve total listing count, ABORT!");
// 					return;
// 				}
// 				String totalCarsStr = StringUtils.remove(StringUtils.split(srpHeader.text())[0], ",");
//
// 				if(NumberUtils.isDigits(totalCarsStr)) {
// 					int totalCars = Integer.valueOf(totalCarsStr);
// 					int numberOfPages = totalCars / CARS_PER_PAGE + (totalCars % CARS_PER_PAGE == 0 ? 0 : 1);
//
// 					if(document.html().contains("No exact matches") || totalCars <= 0) {
// 						minPrice += range;
// 						range *= 2;
// 						continue;
// 					} else if(totalCars > MAX_RESULT_SIZE) {
// 						range /= ((totalCars + MAX_RESULT_SIZE) / MAX_RESULT_SIZE);
// 						rangeNextPass = false;
// 						continue;
// 					} else if(totalCars > MAX_RESULT_SIZE / 2 + 1000) {
// 						rangeNextPass = false;
// 					}
// 					int carUrlsSizeBefore = productUrls.size();
// 					for(int pageNum = 1; pageNum <= numberOfPages; pageNum++) {
// 						url = "https://www.cars.com/for-sale/searchresults.action/" +
// 								"?mkId=" + makeId +
// 								"&page=" + pageNum +
// 								"&perPage=" + CARS_PER_PAGE +
// 								"&prMn=" + minPrice +
// 								"&prMx=" + maxPrice +
// 								"&rd=99999" +
// 								"&searchSource=GN_REFINEMENT" +
// 								"&sort=price-lowest" +
// 								"&yrId=" + yearId +
// 								"&zc=94301";
// 						Document page;
// 						if(document == null) {
// 							page = download(url);
// 							if(sleep()) {
// 								return;
// 							}
// 						} else {
// 							page = document;
// 							document = null;
// 						}
// 						if(page == null) {
// 							logger.warn("[CarsBot] - Page came back null from html unit. Continuing... [{}]" + url);
// 							continue;
// 						}
// 						for(Element div : page.select("div.listing-row__image")) {
// 							for(Element a : div.select("a")) {
// 								String href = a.attr("abs:href");
// 								if(StringUtils.isNotEmpty(href)) {
// 									productUrls.add(href);
// 								}
// 							}
// 						}
// 					}
// 					long millis = timer.elapsed(TimeUnit.MILLISECONDS);
// 					long second = (millis / 1000) % 60;
// 					long minute = (millis / (1000 * 60)) % 60;
// 					long hour = (millis / (1000 * 60 * 60)) % 24;
// 					String timeStr = "Time: [" + blue(String.format("%02d", hour)) + ":"
// 							+ blue(String.format("%02d", minute)) + ":"
// 							+ blue(String.format("%02d", second)) + "]";
//
// 					String printOut = String.format("[CarsBot Indexer] - %-33s %-48s %-26s %-30s %-28s %-28s %s",
// 							"Make: [" + blue(make) + "]",
// 							"Range: [" + cyan(String.valueOf(minPrice)) + "-" + cyan(String.valueOf(maxPrice)) + "]",
// 							"Year: [" + cyan(String.valueOf(year)) + "]",
// 							"Expected: [" + cyan(String.valueOf(totalCarsStr)) + "]",
// 							"Actual: [" + cyan(String.valueOf(productUrls.size() - carUrlsSizeBefore)) + "]",
// 							"Total: [" + cyan(String.valueOf(productUrls.size())) + "]",
// 							timeStr);
// 					logger.info(printOut);
//
// 					if(productUrls.size() > urlWriteThreshold) {
// 						logger.info("[CarsBot] - Car URLs [{}] over [{}] max - persisting & clearing", productUrls.size(), urlWriteThreshold);
// 						listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
// 						productUrls.clear();
// 					}
// 					minPrice += range;
// 					if(rangeNextPass) {
// 						range *= Math.ceil((Math.PI + 1) / Math.log10(totalCars * 100) + 1);
// 					}
// 					rangeNextPass = true;
// 				} else {
// 					logger.warn("[CarsBot] - Could not retrieve total results size", new RuntimeException());
// 				}
// 			}
// 		}
// 		long millis = timer.elapsed(TimeUnit.MILLISECONDS);
// 		long second = (millis / 1000) % 60;
// 		long minute = (millis / (1000 * 60)) % 60;
// 		long hour = (millis / (1000 * 60 * 60)) % 24;
// 		String timeStr = blue(String.format("%02d", hour)) + ":"
// 				+ blue(String.format("%02d", minute)) + ":"
// 				+ blue(String.format("%02d", second));
// 		logger.info("[CarsBot] - Indexing complete for make [{}] in [{}]", make, timeStr);
// 	}
//
// 	@Override
// 	public LinkedHashSet<String> retrieveBaseUrls() {
// 		baseUrls = makeIdMap.keySet().stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
// 		logger.info("[CarsBot] - Base URL collection size [{}]", baseUrls.size());
// 		return baseUrls;
// 	}
//
// 	@Override
// 	public Automobile buildProduct(String url) {
// 		String listingIdStr;
// 		if(!url.contains("/detail/") || !url.contains("/overview/")) {
// 			return null;
// 		} else {
// 			listingIdStr = url.substring(url.indexOf("/detail/") + "/detail/".length(), url.indexOf("/overview/"));
// 			if(!NumberUtils.isDigits(listingIdStr)) {
// 				logger.warn(red("[CarsBot] - Could not parse listing ID [{}]"), url);
// 				return null;
// 			}
// 		}
//
// 		/* HISTORY */
// 		Document document = download(url);
// 		if(StringUtils.containsIgnoreCase(document.html(), "This listing doesn't exist") || document.html().contains("REMOVED")) {
// 			List<Automobile> currentAutos = automobileRepo.findBySourceUrlAndListingId(listingIdStr, getDataSource().getUrl());
// 			if(!currentAutos.isEmpty()) {
// 				automobileRepo.deleteById(currentAutos.get(0).getId());
// 				logger.info("[CarsBot] - Listing ID [{}] was removed. Transitioned to history.", listingIdStr);
// 			} else {
// 				logger.info("[CarsBot] - Listing ID [{}] was removed or invalid", listingIdStr);
// 			}
// 			return null;
// 		}
//
// 		Automobile automobile = new Automobile(url);
// 		try {
// 			// TODO: if make, model, or vin is null in the JO, look elsewhere
//
// 			Element dtoElement = firstChild(document.getElementsByTag("cars-data-broker")); // TODO: this is null sometimes... look elsewhere?
// 			if(dtoElement == null) {
// 				logger.warn(red("[CarsBot] - DTO element came back null for ID [{}]"), listingIdStr);
// 				return null;
// 			}
// 			JSONObject listingJo;
// 			try {
// 				listingJo = new JSONObject(dtoElement.text());
// 			} catch(JSONException e) {
// 				logger.warn("[CarsBot] - Could not parse json object from dto element text! Listing ID: [{}] Error: [{}]",
// 						listingIdStr, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
// 				return null;
// 			}
// 			JSONObject detailJo = listingJo.optJSONObject("listingDetailDto");
// 			if(detailJo == null) {
// 				logger.warn(red("[CarsBot] - No listing detail dto json object found! Listing ID: [{}]"), listingIdStr);
// 				return null;
// 			}
//
// 			/* AUTOMOBILE META DATA */
// 			automobile.setListingId(listingIdStr);
// 			String dealerUrlStr = detailJo.optString("dealerWebsiteUrl", null);
// 			if(StringUtils.isNotBlank(dealerUrlStr)) {
// 				automobile.setDealerUrl(ScoutServices.formUrlFromString(dealerUrlStr, true));
// 			}
// 			automobile.setDealerName(detailJo.optString("dealerName", null));
// 			automobile.setStockNumber(detailJo.optString("stockNumber", null));
//
// 			automobile.setAddress(detailJo.optString("streetAddress", null));
// 			automobile.setZip(detailJo.optString("zipcode", null));
//
// 			Float latitude = null;
// 			Float longitude = null;
// 			String location = detailJo.optString("location", null);
// 			if(location != null) {
// 				String[] latAndLong = StringUtils.split(location, ",");
// 				if(latAndLong.length == 2) {
// 					try {
// 						latitude = Float.parseFloat(latAndLong[0]);
// 						longitude = Float.parseFloat(latAndLong[1]);
// 					} catch(NumberFormatException e) {
// 						logger.warn("[CarsBot] - Could not parse lat/ lon from [{}]", location);
// 					}
// 				}
// 			}
// 			automobile.setLatitude(latitude);
// 			automobile.setLongitude(longitude);
//
// //			String description = "";
// //			String sellNotes1 = detailJo.optString("sellerNotesPart1", null);
// //			String sellNotes2 = detailJo.optString("sellerNotesPart2", null);
// //			if(sellNotes1 != null) {
// //				description += sellNotes1;
// //			}
// //			if(sellNotes2 != null) {
// //				description += " " + sellNotes2;
// //			}
// //			if(StringUtils.isNotBlank(description)) {
// //				automobile.setDescription(description);
// //			}
//
// 			/* AUTOMOBILE DATA */
// 			String vin = detailJo.optString("vin", null);
// 			if(AutoParsingOperations.vinRecognizer().test(vin)) {
// 				automobile.setVin(vin);
// 			} else {
// 				logger.warn("[CarsBot] - VIN [{}] did not pass quality gates for listing ID [{}]", vin, listingIdStr);
// 				return null;
// 			}
// 			automobile.setMake(detailJo.optString("makeName", null));
// 			automobile.setModel(detailJo.optString("modelName", null));
//
// 			automobile.setYear(optInteger(detailJo, "modelYear"));
// 			automobile.setMileage(optInteger(detailJo, "milesInteger"));
// 			automobile.setPrice(detailJo.optBigDecimal("price", null));
//
//
// 			/* AUTOMOBILE ATTRIBUTES */
// 			automobile.setExteriorColor(detailJo.optString("exteriorColor", null));
// 			automobile.setInteriorColor(detailJo.optString("interiorColor", null));
// 			automobile.setEngine(detailJo.optString("engineDescription", null));
// 			automobile.setTransmission(detailJo.optString("transmission", null));
// 			automobile.setDriveType(detailJo.optString("drivetrain", null));
//
// 			automobile.setMpgCity(optInteger(detailJo, "mpgCity"));
// 			automobile.setMpgHighway(optInteger(detailJo, "mpgHwy"));
// 			automobile.setDoors(optInteger(detailJo, "doorCount"));
//
// 			automobile.setBodyStyle(detailJo.optString("bodystyleName", null));
// 			automobile.setTrim(detailJo.optString("trimName", null));
//
//
// 			JSONArray imageJsonArray = detailJo.optJSONArray("photoUrlsLarge");
// 			if(imageJsonArray != null && imageJsonArray.length() > 0) {
// 				automobile.setMainImageUrl(imageJsonArray.optString(0, null));
// 			}
// 		} catch(Exception e) {
// 			logger.warn(red("[CarsBot] - Error building automobile with listing ID [{}]"), listingIdStr,
// 					e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
// 			return null;
// 		}
//
// 		// todo - add the following to automobile table [fuelType, new or used flag, phone number]
//
// 		return automobile;
// 	}
//
// 	private static Map<Integer, String> buildMakeIdMap() {
// 		Map<Integer, String> makeIdMap = new HashMap<>();
// 		makeIdMap.put(35354491, "Genesis");
// 		makeIdMap.put(20001, "Acura");
// 		makeIdMap.put(20047, "Alfa Romeo");
// 		makeIdMap.put(20003, "Aston Martin");
// 		makeIdMap.put(20049, "Audi");
// 		makeIdMap.put(20050, "Avanti Motors");
// 		makeIdMap.put(20051, "Bentley");
// 		makeIdMap.put(20005, "BMW");
// 		makeIdMap.put(33583, "Bugatti");
// 		makeIdMap.put(20006, "Buick");
// 		makeIdMap.put(20052, "Cadillac");
// 		makeIdMap.put(20053, "Chevrolet");
// 		makeIdMap.put(20008, "Chrysler");
// 		makeIdMap.put(20012, "Dodge");
// 		makeIdMap.put(20058, "Eagle");
// 		makeIdMap.put(20014, "Ferrari");
// 		makeIdMap.put(20060, "FIAT");
// 		makeIdMap.put(41703, "Fisker");
// 		makeIdMap.put(20015, "Ford");
// 		makeIdMap.put(20061, "GMC");
// 		makeIdMap.put(20017, "Honda");
// 		makeIdMap.put(20018, "Hummer");
// 		makeIdMap.put(20064, "Hyundai");
// 		makeIdMap.put(20019, "INFINITI");
// 		makeIdMap.put(20065, "International");
// 		makeIdMap.put(20020, "Isuzu");
// 		makeIdMap.put(20066, "Jaguar");
// 		makeIdMap.put(20021, "Jeep");
// 		makeIdMap.put(20068, "Kia");
// 		makeIdMap.put(33663, "Koenigsegg");
// 		makeIdMap.put(20069, "Lamborghini");
// 		makeIdMap.put(20024, "Land Rover");
// 		makeIdMap.put(20070, "Lexus");
// 		makeIdMap.put(20025, "Lincoln");
// 		makeIdMap.put(20071, "Lotus");
// 		makeIdMap.put(20072, "Maserati");
// 		makeIdMap.put(20027, "Maybach");
// 		makeIdMap.put(20073, "Mazda");
// 		makeIdMap.put(47903, "McLaren");
// 		makeIdMap.put(20028, "Mercedes-Benz");
// 		makeIdMap.put(20074, "Mercury");
// 		makeIdMap.put(20075, "MINI");
// 		makeIdMap.put(20030, "Mitsubishi");
// 		makeIdMap.put(20076, "Morgan");
// 		makeIdMap.put(20077, "Nissan");
// 		makeIdMap.put(20032, "Oldsmobile");
// 		makeIdMap.put(20079, "Panoz");
// 		makeIdMap.put(20034, "Peugeot");
// 		makeIdMap.put(20080, "Plymouth");
// 		makeIdMap.put(20035, "Pontiac");
// 		makeIdMap.put(20081, "Porsche");
// 		makeIdMap.put(44763, "RAM");
// 		makeIdMap.put(20037, "Rolls-Royce");
// 		makeIdMap.put(20038, "Saab");
// 		makeIdMap.put(20084, "Saleen");
// 		makeIdMap.put(20039, "Saturn");
// 		makeIdMap.put(20085, "Scion");
// 		makeIdMap.put(20228, "smart");
// 		makeIdMap.put(33584, "Spyker");
// 		makeIdMap.put(20040, "Sterling");
// 		makeIdMap.put(20041, "Subaru");
// 		makeIdMap.put(20042, "Suzuki");
// 		makeIdMap.put(28263, "Tesla");
// 		makeIdMap.put(20088, "Toyota");
// 		makeIdMap.put(20089, "Volkswagen");
// 		makeIdMap.put(20044, "Volvo");
// 		return makeIdMap;
// 	}
//
// 	private static Map<Integer, Integer> buildYearIdMap() {
// 		Map<Integer, Integer> yearIdMap = new HashMap<>();
// 		yearIdMap.put(2018, 35797618);
// 		yearIdMap.put(2017, 30031936);
// 		yearIdMap.put(2016, 58487);
// 		yearIdMap.put(2015, 56007);
// 		yearIdMap.put(2014, 51683);
// 		yearIdMap.put(2013, 47272);
// 		yearIdMap.put(2012, 39723);
// 		yearIdMap.put(2011, 34923);
// 		yearIdMap.put(2010, 27381);
// 		yearIdMap.put(2009, 20201);
// 		yearIdMap.put(2008, 20145);
// 		yearIdMap.put(2007, 20200);
// 		yearIdMap.put(2006, 20144);
// 		yearIdMap.put(2005, 20199);
// 		yearIdMap.put(2004, 20143);
// 		yearIdMap.put(2003, 20198);
// 		yearIdMap.put(2002, 20142);
// 		yearIdMap.put(2001, 20197);
// 		yearIdMap.put(2000, 20141);
// 		yearIdMap.put(1999, 20196);
// 		yearIdMap.put(1998, 20140);
// 		yearIdMap.put(1997, 20195);
// 		yearIdMap.put(1996, 20139);
// 		yearIdMap.put(1995, 20194);
// 		yearIdMap.put(1994, 20138);
// 		yearIdMap.put(1993, 20193);
// 		yearIdMap.put(1992, 20137);
// 		yearIdMap.put(1991, 20192);
// 		yearIdMap.put(1990, 20136);
// 		yearIdMap.put(1989, 20191);
// 		yearIdMap.put(1988, 20135);
// 		yearIdMap.put(1987, 20190);
// 		yearIdMap.put(1986, 20134);
// 		yearIdMap.put(1985, 20189);
// 		yearIdMap.put(1984, 20133);
// 		yearIdMap.put(1983, 20188);
// 		yearIdMap.put(1982, 20132);
// 		yearIdMap.put(1981, 20187);
// 		yearIdMap.put(1980, 20131);
// 		yearIdMap.put(1979, 20186);
// 		yearIdMap.put(1978, 20130);
// 		yearIdMap.put(1977, 20185);
// 		yearIdMap.put(1976, 20129);
// 		yearIdMap.put(1975, 20184);
// 		yearIdMap.put(1974, 20128);
// 		yearIdMap.put(1973, 20183);
// 		yearIdMap.put(1972, 20127);
// 		yearIdMap.put(1971, 20182);
// 		yearIdMap.put(1970, 20126);
// 		return yearIdMap;
// 	}
// }