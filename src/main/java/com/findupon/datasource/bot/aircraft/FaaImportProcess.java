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

package com.findupon.datasource.bot.aircraft;

import com.google.common.base.Stopwatch;
import com.neovisionaries.i18n.CountryCode;
import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.datasource.bot.AbstractImportProcess;
import org.apache.commons.lang3.StringUtils;
import org.quickgeo.Place;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * This ƒindupon crawler serves the purpose of downloading, parsing, and importing data
 * from the FAA. Specifically, it imports the data from the FAA Master file obtainable
 * via direct download from the FAA. This file contains critical data about each
 * aircraft registered in the US.
 */
@Component
public class FaaImportProcess extends AbstractImportProcess<com.findupon.commons.entity.product.aircraft.Aircraft> {

	@Autowired private com.findupon.commons.utilities.AircraftAttributeMatcher attributeMatcher;
	@Autowired private com.findupon.commons.dao.AircraftDao aircraftDao;

	// The URL of the FAA's zip file containing all relevant aircraft data
	private final String sourcePath = "database/ReleasableAircraft.zip";


	@Override
	public void init() {
		Stopwatch stopwatch = Stopwatch.createStarted();

		List<String> ediRows = getEdiRows();
		if(ediRows.isEmpty()) {
			logger.error("[FaaImportProcess] - No rows parsed from aircraft file!");
			return;
		}
		List<Aircraft> aircrafts = parseAircraftEdi(ediRows);

		setMeta(aircrafts);
		logger.debug("[FaaImportProcess] - Aircraft meta setting complete, getting IDs we did not pick up");

		List<Long> idsToDelete = idsWeDidNotPickup(aircrafts);
		logger.debug("[FaaImportProcess] - IDs queued for deletion ({}), moving on to persistence", idsToDelete.size());

		aircraftDao.saveAll(aircrafts);
		logger.debug("[FaaImportProcess] - Persistence process complete, removing [{}] old aircrafts", idsToDelete.size());

		aircraftDao.deleteAllById(idsToDelete);

		slackMessenger.sendMessageWithArgs("FAA load process complete. %n```" +
						"Created/ Updated:  [%s] %n" +
						"Removed:           [%s] %n" +
						"Total time taken:  [%s] ```",
				String.format("%,d", aircrafts.size()), String.format("%,d", idsToDelete.size()), com.findupon.commons.utilities.TimeUtils.format(stopwatch));
	}

	private List<String> getEdiRows() {
		List<String> mmsCodes = jdbcTemplate.queryForList("select mfr_mdl_srs_code from aircraft_faa_acftref", String.class);
		String url = getDataSource().getUrl() + sourcePath;
		logger.info("Downloading FAA files from: {}...", url);

		List<String> rows = new ArrayList<>();
		try(ZipInputStream zis = new ZipInputStream(new URL(url).openStream())) {
			ZipEntry zipEntry = zis.getNextEntry();
			while(zipEntry != null) {
				if("MASTER.txt".equals(zipEntry.getName())) {
					rows = new BufferedReader(new InputStreamReader(zis))
							.lines()
							.skip(0) // title row
							.filter(row -> mmsCodes.contains(StringUtils.split(row, ",")[2]))
							.parallel()
							.collect(Collectors.toList());
					logger.info("FAA rows size: [{}]", rows.size());
					zipEntry = null;
				} else {
					zipEntry = zis.getNextEntry();
				}
			}
		} catch(Exception e) {
			logger.error("updateReferenceData fack", e);
		}
		return rows;
	}

	private List<Long> idsWeDidNotPickup(List<Aircraft> aircrafts) {
		Set<String> newListingIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		List<Long> idsToDelete = new ArrayList<>();
		aircrafts.stream().map(com.findupon.commons.entity.product.aircraft.Aircraft::getListingId).forEach(newListingIds::add);
		List<Aircraft> existingAircrafts = aircraftDao.findAllByDataSourceId(getDataSource().getId());
		logger.debug("[FaaImportProcess] - Existing aircraft size [{}]", existingAircrafts.size());
		existingAircrafts.forEach(a -> {
			if(!newListingIds.contains(a.getListingId())) {
				idsToDelete.add(a.getId());
			}
		});
		existingAircrafts.clear();
		return idsToDelete;
	}

	// N-NUMBER,
	// SERIAL NUMBER,
	// MFR MDL CODE,
	// ENG MFR MDL,
	// YEAR MFR,
	//
	// TYPE REGISTRANT,
	// NAME,
	// STREET,
	// STREET2,
	// CITY,
	//
	// STATE,
	// ZIP CODE,
	// REGION,
	// COUNTY,
	// COUNTRY,
	//
	// LAST ACTION DATE,
	// CERT ISSUE DATE,
	// CERTIFICATION,
	// TYPE AIRCRAFT,
	// TYPE ENGINE,
	//
	// STATUS CODE,
	// MODE S CODE,
	// FRACT OWNER,
	// AIR WORTH DATE,
	// OTHER NAMES(1),
	//
	// OTHER NAMES(2),
	// OTHER NAMES(3),
	// OTHER NAMES(4),
	// OTHER NAMES(5),
	// EXPIRATION DATE,
	//
	// UNIQUE ID,
	// KIT MFR,
	// KIT MODEL,
	// MODE S CODE HEX,
	private List<Aircraft> parseAircraftEdi(List<String> ediRows) {
		List<Aircraft> aircrafts = new ArrayList<>();

		for(int i = 0; i < ediRows.size(); i++) {
			String row = ediRows.get(i);
			com.findupon.commons.entity.product.aircraft.Aircraft aircraft = new com.findupon.commons.entity.product.aircraft.Aircraft();
			String[] cols = StringUtils.split(row, ",");

			String street1 = StringUtils.trimToNull(cols[7]);
			String street2 = StringUtils.trimToNull(cols[8]);
			String city = StringUtils.trimToNull(cols[9]);
			String state = StringUtils.trimToNull(cols[10]);

			if(street1 != null) {
				if(street2 != null) {
					street1 += " " + street2;
				}
				if(city != null) {
					street1 += ", " + city;
				}
				if(state != null) {
					com.findupon.commons.entity.building.State s = com.findupon.commons.entity.building.State.valueOfAbbreviation(state);
					if(s != null) {
						state = s.getAbbreviation();
					}
					street1 += ", " + state;
				}
			}
			String zip = StringUtils.trimToNull(cols[11]);
			Double lat = null;
			Double lng = null;
			if(zip != null) {
				if(zip.length() > 5) {
					zip = zip.substring(0, 5);
				}
				street1 += ", " + zip;
				Optional<Place> place = com.findupon.commons.building.AddressOperations.getNearestPlaceFromZip(zip);
				if(place.isPresent()) {
					lat = place.get().getLatitude();
					lng = place.get().getLongitude();
				}
			}

			aircraft.setListingId(StringUtils.trimToNull(cols[30]));

			if(lat != null) {
				aircraft.setLatitude(lat.floatValue());
				aircraft.setLongitude(lng.floatValue());
			}
			aircraft.setCountryCode(CountryCode.US.getAlpha3());
			aircraft.setAddress(street1);
			aircraft.setCity(city);
			aircraft.setZip(zip);
			aircraft.setContactName(StringUtils.trimToNull(cols[6]));

			String regNumber = StringUtils.trimToNull(cols[0]);
			if(regNumber == null) {
				logger.debug("[FaaImportProcess] - Missing reg number on line [{}]", i);
				continue;
			}
			regNumber = "N" + regNumber;

			aircraft.setRegNumber(regNumber);
			aircraft.setUrl(getDataSource().getUrl() + "aircraftinquiry/NNum_Results.aspx?NNumbertxt=" + regNumber);
			aircraft.setSrlNumber(cols[1].trim());

			String year = StringUtils.trimToNull(cols[4]);
			if(StringUtils.isNumeric(year) && year.matches("^\\d{4}$")) {
				aircraft.setYear(Integer.parseInt(year));
			}

			// make model series code
			String mms = StringUtils.trimToNull(cols[2]);
			int catId = attributeMatcher.getCatIdFromMMS(mms);
			if(catId > -1) {
				aircraft.setCategoryId(catId);
			}
			int mkeId = attributeMatcher.getMkeIdFromMMS(mms);
			if(mkeId > -1) {
				aircraft.setMakeId(mkeId);
			}
			int mdlId = attributeMatcher.getMdlIdFromMMS(mms);
			if(mdlId > -1) {
				aircraft.setModelId(mdlId);
			}
			aircraft.setProductCondition(com.findupon.commons.entity.product.ProductCondition.USED);

			// num of seats
			if(mms != null) {
				int seats = attributeMatcher.getSeatsFromMMS(mms);
				if(seats > -1) {
					aircraft.setNumberOfSeats(seats);
				}
			}

			aircrafts.add(aircraft);

			if(i % (ediRows.size() / 10) == 0 || i == ediRows.size()) {
				logger.debug("[FaaImportProcess] - Aircraft EDI load percent complete [{}]",
						com.findupon.commons.utilities.ConsoleColors.green(String.format("%.2f", ((float)i / ediRows.size()) * 100)));
			}

			// todo, prod term id, there is an faa code for this
		}

		return aircrafts;
	}

	public void updateReferenceData() {
		String url = getDataSource().getUrl() + sourcePath;
		logger.info("Downloading FAA files from: {}...", url);
		try(ZipInputStream zis = new ZipInputStream(new URL(url).openStream())) {
			ZipEntry zipEntry = zis.getNextEntry();
			while(zipEntry != null) {
				if(zipEntry.getName().equals("ACFTREF.txt")) {
					List<String> mmsCodes = jdbcTemplate.queryForList("select mfr_mdl_srs_code from aircraft_faa_acftref", String.class);
					new BufferedReader(new InputStreamReader(zis))
							.lines()
							.skip(0)  // title row
							.parallel()
							.forEach(row -> {

								String[] cols = StringUtils.split(row, ",");

								Integer bcc = StringUtils.trimToNull(cols[6]) == null ? 1 : Integer.valueOf(cols[6].trim());
								if(bcc < 1) {

									String mmsc = cols[0];
									if(!mmsCodes.contains(mmsc)) {

										jdbcTemplate.update("insert into aircraft_faa_acftref values (?,?,?,?,?,?,?,?,?,?,?,?)",
												null,
												mmsc,
												StringUtils.trimToNull(cols[1]),
												StringUtils.trimToNull(cols[2]),
												StringUtils.trimToNull(cols[3]) == null ? null : Integer.valueOf(cols[3].trim()),
												StringUtils.trimToNull(cols[4]) == null ? null : Integer.valueOf(cols[4].trim()),
												StringUtils.trimToNull(cols[5]) == null ? null : Integer.valueOf(cols[5].trim()),
												bcc,
												StringUtils.trimToNull(cols[7]) == null ? null : Integer.valueOf(cols[7].trim()),
												StringUtils.trimToNull(cols[8]) == null ? null : Integer.valueOf(cols[8].trim()),
												StringUtils.trimToNull(cols[9]),
												StringUtils.trimToNull(cols[10]) == null ? null : Integer.valueOf(cols[10].trim()));
									}
								}
							});

					zipEntry = null;
				} else {
					zipEntry = zis.getNextEntry();
				}
			}
		} catch(Exception e) {
			logger.error("updateReferenceData fack", e);
		}
	}
}
