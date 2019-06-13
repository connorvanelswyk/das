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

package com.findupon.datasource.bot.automotive;

import com.google.common.base.Stopwatch;
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.product.attribute.*;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.datasource.bot.AbstractImportProcess;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.net.ftp.*;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Component
public class CarsDirectProcess extends AbstractImportProcess<com.findupon.commons.entity.product.automotive.Automobile> {
	private final Map<String, Pair<String, Address>> dealerMap1 = new HashMap<>();
	private List<String> dealerFileData1;
	private List<String> inventoryFileData1;

	private final Map<String, Pair<String, Address>> dealerMap2 = new HashMap<>();
	private List<String> dealerFileData2;
	private List<String> inventoryFileData2;

	public static final String partnerId = "1080279";
	public static final String cdcSourceUrl = "https://www.carsdirect.com/";

	@Value("${production}") private Boolean production;
	@Value("${cdc.ftp-server}") private String ftpServer;
	@Value("${cdc.ftp-user}") private String ftpUser;
	@Value("${cdc.ftp-pass}") private String ftpPass;

	@Autowired private com.findupon.commons.searchparty.AutomotiveGatherer automotiveGatherer;
	@Autowired private com.findupon.commons.dao.AutomobileDao automobileDao;


	@Override
	public void init() {
		Stopwatch stopwatch = Stopwatch.createStarted();
		List<Automobile> automobiles = new ArrayList<>();

		loadFilesFromFTP();

		// file set 1
		if(CollectionUtils.isNotEmpty(dealerFileData1) && CollectionUtils.isNotEmpty(inventoryFileData1)) {
			loadDealerFile(dealerFileData1, dealerMap1);
			if(!dealerMap1.isEmpty()) {
				automobiles.addAll(parseAutomobileEdi(inventoryFileData1, dealerMap1, "finduponused"));
			} else {
				logger.error("[CarsDirectProcess] - No dealers parsed from dealer file 1!");
			}
		} else {
			logger.error("[CarsDirectProcess] - Error loading file set 1, missing inventory or dealer data");
		}
		dealerFileData1 = null;
		inventoryFileData1 = null;
		dealerMap1.clear();

		// file set 2
		if(CollectionUtils.isNotEmpty(dealerFileData2) && CollectionUtils.isNotEmpty(inventoryFileData2)) {
			loadDealerFile(dealerFileData2, dealerMap2);
			if(!dealerMap2.isEmpty()) {
				automobiles.addAll(parseAutomobileEdi(inventoryFileData2, dealerMap2, "usedfindupon"));
			} else {
				logger.error("[CarsDirectProcess] - No dealers parsed from dealer file 2!");
			}
		} else {
			logger.error("[CarsDirectProcess] - Error loading file set 2, missing inventory or dealer data");
		}
		dealerFileData2 = null;
		inventoryFileData2 = null;
		dealerMap2.clear();

		if(automobiles.isEmpty()) {
			// load failure, already reported
			return;
		}

		setMeta(automobiles);
		logger.debug("[CarsDirectProcess] - Automobile meta setting complete, getting IDs we did not pick up");

		List<Long> idsToDelete = idsWeDidNotPickup(automobiles);
		logger.debug("[CarsDirectProcess] - IDs queued for deletion ({}), moving on to persistence", idsToDelete.size());

		int size = automobiles.size();
		automobileDao.saveAll(automobiles);
		automobiles.clear();
		logger.debug("[CarsDirectProcess] - Persistence process complete, removing [{}] old automobiles", idsToDelete.size());

		automobileDao.deleteAllById(idsToDelete);

		slackMessenger.sendMessageWithArgs("CarsDirect load process complete. %n```" +
						"Created/ Updated:  [%s] %n" +
						"Removed:           [%s] %n" +
						"Total time taken:  [%s] ```",
				String.format("%,d", size), String.format("%,d", idsToDelete.size()), com.findupon.commons.utilities.TimeUtils.format(stopwatch));
	}

	private void loadFilesFromFTP() {
		FTPClient ftp = new FTPClient();
		FTPClientConfig config = new FTPClientConfig();
		ftp.configure(config);

		try {
			if(!production) {
				ftpServer = "stg-ftp.carsdirect.com";
				ftpUser = "finduponused";
				ftpPass = "Vuym7t7VVdVx33sn";
			}
			ftp.connect(ftpServer);
			ftp.login(ftpUser, ftpPass);
			ftp.setFileType(FTP.BINARY_FILE_TYPE);

			String replyStr = ftp.getReplyString();
			int replyCode = ftp.getReplyCode();
			boolean isSuccessfulReply = FTPReply.isPositiveCompletion(replyCode);

			logger.debug("[CarsDirectProcess] - Connection to FTP server [{}] Success [{}] Reply code [{}] Reply message: {}",
					ftpServer, isSuccessfulReply, replyCode, replyStr);

			if(isSuccessfulReply) {
				ftp.enterLocalPassiveMode();
				logger.debug("[CarsDirectProcess] - Getting all files in directory [{}]", ftp.printWorkingDirectory());
				FTPFile[] ftpFiles = ftp.listFiles();

				if(ftpFiles != null) {
					for(FTPFile file : ftpFiles) {
						if(!file.isFile()) {
							continue;
						}
						logger.debug("[CarsDirectProcess] - Downloading file [{}]", file.getName());

						String data = downloadFileData(ftp, file.getName());
						if(StringUtils.isBlank(data)) {
							logger.error("[CarsDirectProcess] - Blank file returned by download! [{}]", file.getName());
							continue;
						}
						switch(file.getName()) {
							case "finduponused_used_dealers.txt":
								dealerFileData1 = Arrays.asList(data.split("\n"));
								break;
							case "finduponused_used_inventory.zip":
								inventoryFileData1 = Arrays.asList(data.split("\n"));
								break;
							case "usedfindupon_used_dealers.txt":
								dealerFileData2 = Arrays.asList(data.split("\n"));
								break;
							case "usedfindupon_used_inventory.zip":
								inventoryFileData2 = Arrays.asList(data.split("\n"));
								break;
							default:
								logger.debug("[CarsDirectProcess] - Other file found [{}]", file.getName());
								continue;
						}
						logger.debug("[CarsDirectProcess] - Data file [{}] downloaded successfully", file.getName());
					}
				} else {
					logger.error("[CarsDirectProcess] - No files found on FTP!");
				}
				logger.debug("[CarsDirectProcess] - FTP file transfer completed, logging out");
				ftp.logout();
			} else {
				ftp.disconnect();
				logger.error("[CarsDirectProcess] - FTP server refused connection");
			}
		} catch(IOException e) {
			logger.error("[CarsDirectProcess] - FTP connection error!", e);
		} finally {
			logger.debug("[CarsDirectProcess] - Disconnecting from FTP server [{}]", ftpServer);
			if(ftp.isConnected()) {
				try {
					ftp.disconnect();
				} catch(IOException e) {
					logger.warn("[CarsDirectProcess] - Error disconnecting from FTP server [{}]", ftpServer, e);
				}
			}
		}
	}

	private String downloadFileData(FTPClient ftp, String fileName) throws IOException {
		StringBuilder dataBuilder = new StringBuilder();
		if(StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), "zip")) {
			try(ZipInputStream zipInputStream = new ZipInputStream(ftp.retrieveFileStream(fileName))) {
				ZipEntry entry;
				int numEntries = 0;
				while((entry = zipInputStream.getNextEntry()) != null) {
					logger.debug("[CarsDirectProcess] - Reading zip entry [{}]", entry.getName());
					if(++numEntries > 1) {
						logger.error("[CarsDirectProcess] - More than one entry found in the zip file!");
					} else {
						BufferedReader br = new BufferedReader(new InputStreamReader(zipInputStream));
						String line;
						while((line = br.readLine()) != null) {
							dataBuilder.append(line).append("\n");
						}
						// data = new BufferedReader(new InputStreamReader(zipInputStream))
						// 		.lines()
						// 		.parallel()
						// 		.collect(Collectors.joining("\n"));
					}
				}
			} catch(Exception e) {
				logger.warn("[CarsDirectProcess] - Error reading zip file [{}]", fileName, e);
			} finally {
				if(!ftp.completePendingCommand()) {
					dataBuilder = new StringBuilder();
				}
			}
		} else {
			try(InputStream inputStream = ftp.retrieveFileStream(fileName)) {
				dataBuilder = new StringBuilder(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
			} catch(Exception e) {
				logger.warn("[CarsDirectProcess] - Error reading txt file [{}]", fileName, e);
			} finally {
				if(!ftp.completePendingCommand()) {
					dataBuilder = new StringBuilder();
				}
			}
		}
		return dataBuilder.toString();
	}

	private List<Long> idsWeDidNotPickup(List<Automobile> automobiles) {
		Set<String> newListingIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		List<Long> idsToDelete = new ArrayList<>();
		automobiles.stream().map(com.findupon.commons.entity.product.automotive.Automobile::getListingId).forEach(newListingIds::add);
		List<Automobile> existingAutos = automobileDao.findAllByDataSourceId(getDataSource().getId());
		logger.debug("[CarsDirectProcess] - Existing autos size [{}]", existingAutos.size());
		existingAutos.forEach(a -> {
			if(!newListingIds.contains(a.getListingId())) {
				idsToDelete.add(a.getId());
			}
		});
		existingAutos.clear();
		return idsToDelete;
	}

	private void loadDealerFile(List<String> lines, Map<String, Pair<String, Address>> dealerMap) {
		int lineNum = 0, lineSize = lines.size();
		for(String dealerLine : lines) {
			dealerLine = com.findupon.commons.searchparty.ScoutServices.normalize(dealerLine, false);
			if(lineNum++ == 0) {
				// first line is always the header
				continue;
			}
			String[] attributes = dealerLine.split("\\|~");
			if(lineNum % (lineSize / 10) == 0 || lineNum == lineSize) {
				logger.debug("[CarsDirectProcess] - Dealer load percent complete [{}]", com.findupon.commons.utilities.ConsoleColors.green(String.format("%.2f", ((float)lineNum / lineSize) * 100)));
			}

			String franchiseCode = safeLookup(attributes, 0);
			if(franchiseCode == null) {
				logger.warn("[CarsDirectProcess] - Missing franchise code for dealer. Raw data line: \n{}", dealerLine);
				continue;
			}
			String name = safeLookup(attributes, 1);
			String streetAddress = safeLookup(attributes, 2);
			String city = safeLookup(attributes, 3);
			String state = safeLookup(attributes, 4);
			String zip = safeLookup(attributes, 5);

			com.findupon.commons.entity.building.Address address = new com.findupon.commons.entity.building.Address();
			if(zip != null) {
				com.findupon.commons.building.AddressOperations.setAddressFromZip(address, zip);
			}
			if(address.getZip() == null && streetAddress != null) {
				String fullAddress = streetAddress;
				if(city != null) {
					fullAddress += StringUtils.SPACE + city;
				}
				if(state != null) {
					fullAddress += StringUtils.SPACE + state;
				}
				if(zip != null) {
					fullAddress += StringUtils.SPACE + zip;
				}
				Optional<Address> addressOpt = com.findupon.commons.building.AddressOperations.getAddress(fullAddress);
				if(addressOpt.isPresent()) {
					address = addressOpt.get();
				}
			}
			if(address.getZip() != null) {
				dealerMap.putIfAbsent(franchiseCode, Pair.of(name, address));
			}
		}
	}

	private List<Automobile> parseAutomobileEdi(List<String> lines, Map<String, Pair<String, Address>> dealerMap, String partnerSourceName) {
		List<Automobile> automobiles = new com.findupon.commons.entity.product.ProductList<>();
		int lineNum = 0, lineSize = lines.size(), invalidVins = 0, notEnoughAttributes = 0;

		for(String vehicleLine : lines) {
			vehicleLine = com.findupon.commons.searchparty.ScoutServices.normalize(vehicleLine, false);

			if(lineNum++ == 0) {
				// first line is always the header
				continue;
			}
			String[] attributes = vehicleLine.split("\\|~");
			if(lineNum % (lineSize / 10) == 0 || lineNum == lineSize) {
				logger.debug("[CarsDirectProcess] - Automobile load percent complete [{}]", com.findupon.commons.utilities.ConsoleColors.green(String.format("%.2f", ((float)lineNum / lineSize) * 100)));
			}

			// integral attributes
			String franchiseId = safeLookup(attributes, 0); // aka dealer id
			String vin = safeLookup(attributes, 1);
			String price = safeLookup(attributes, 2);
			String year = safeLookup(attributes, 3);
			String make = safeLookup(attributes, 4);
			String model = safeLookup(attributes, 5);
			String trim = safeLookup(attributes, 6);
			String sku = safeLookup(attributes, 7); // listing id

			String bodyType = safeLookup(attributes, 8);
			// String driveType = safeLookup(attributes, 9); // UNUSED
			String engine = safeLookup(attributes, 10);
			String fuel = safeLookup(attributes, 11);
			String transmission = safeLookup(attributes, 12);
			String mileage = safeLookup(attributes, 13);
			String numCylinders = safeLookup(attributes, 14);
			String doors = safeLookup(attributes, 15);
			String exteriorColor = safeLookup(attributes, 16);
			String interiorColor = safeLookup(attributes, 17);
			// String interiorType = safeLookup(attributes, 18); // UNUSED
			// String referenceDate = safeLookup(attributes, 19); // UNUSED
			String optionsDesc = safeLookup(attributes, 20);
			String audioDesc = safeLookup(attributes, 21);
			String sunRoof = safeLookup(attributes, 22); // 1/0 bit
			String alarmSystem = safeLookup(attributes, 23); // 1/0 bit
			String powerWindows = safeLookup(attributes, 24); // 1/0 bit
			String powerLocks = safeLookup(attributes, 25); // 1/0 bit
			String airbags = safeLookup(attributes, 26); // 1/0 bit
			String airConditioning = safeLookup(attributes, 27); // 1/0 bit
			String cruiseControl = safeLookup(attributes, 28); // 1/0 bit
			String tiltSteering = safeLookup(attributes, 29); // 1/0 bit
			String powerSteering = safeLookup(attributes, 30); // 1/0 bit
			String powerSeats = safeLookup(attributes, 31); // 1/0 bit
			String promotionalText = safeLookup(attributes, 32); // free-form text desc of the vehicle
			// String oemCertified = safeLookup(attributes, 33); // UNUSED
			// String warrantyDesc = safeLookup(attributes, 34); // UNUSED
			String photoAvailableFlag = safeLookup(attributes, 35); // 1/0 bit
			String photoUrl = safeLookup(attributes, 36);

			if(sku == null || vin == null || make == null || model == null || year == null) {
				logger.trace("[CarsDirectProcess] - Not enough data to parse automobile. Raw data line: \n{}", vehicleLine);
				notEnoughAttributes++;
				continue;
			}
			com.findupon.commons.entity.product.automotive.Automobile automobile = new com.findupon.commons.entity.product.automotive.Automobile(getDataSource().getUrl() + "used_cars/vehicle-detail/ul"
					+ sku
					// + "/" + make.toLowerCase(Locale.ENGLISH).replace(" ", "-")
					// + "/" + model.toLowerCase(Locale.ENGLISH).replace(" ", "-")
					+ "?src=" + partnerId);

			if(com.findupon.commons.building.AutoParsingOperations.vinRecognizer().test(vin)) {
				automobile.setVin(vin);
			} else {
				logger.trace("[CarsDirectProcess] - Invalid VIN found, not persisting [{}]", vin);
				invalidVins++;
				continue;
			}
			if(price != null) {
				try {
					BigDecimal priceValue = new BigDecimal(price);
					if(priceValue.compareTo(new BigDecimal(1000)) >= 0) {
						automobile.setPrice(priceValue);
					}
				} catch(NumberFormatException e) {
					logger.trace("[CarsDirectProcess] - Could not parse price. Listing ID [{}]", sku);
				}
			}
			String mmyText = make + " " + model + " " + StringUtils.defaultString(trim) + " " + year;
			if(!automotiveGatherer.setMakeModelTrimYear(automobile, mmyText)) {
				logger.debug("[CarsDirectProcess] - Could not parse MMY from text [{}]", mmyText);
				notEnoughAttributes++;
				continue;
			}
			automobile.setListingId(sku);
			automobile.setStockNumber(partnerSourceName);

			automobile.setBody(com.findupon.commons.entity.product.attribute.Body.of(bodyType));
			automobile.setTransmission(com.findupon.commons.entity.product.attribute.Transmission.of(transmission));
			automobile.setFuel(com.findupon.commons.entity.product.attribute.Fuel.of(fuel));
			if(NumberUtils.isDigits(mileage)) {
				automobile.setMileage(Integer.parseInt(mileage));
			}
			if(NumberUtils.isDigits(doors)) {
				automobile.setDoors(Integer.parseInt(doors));
			}
			automobile.setExteriorColor(com.findupon.commons.entity.product.attribute.ExteriorColor.of(exteriorColor));
			automobile.setInteriorColor(com.findupon.commons.entity.product.attribute.InteriorColor.of(interiorColor));

			if(photoUrl != null && photoUrl.contains(",")) {
				photoUrl = photoUrl.split(",")[0];
				if(!UrlValidator.getInstance().isValid(photoUrl)) {
					photoUrl = null;
				}
			}
			if(!UrlValidator.getInstance().isValid(photoUrl)) {
				photoUrl = null;
			}
			automobile.setMainImageUrl(photoUrl);

			Pair<String, com.findupon.commons.entity.building.Address> dealer = dealerMap.get(franchiseId);
			if(dealer != null) {
				automobile.setDealerName(dealer.getLeft());
				dealer.getRight().setAutomobileAddress(automobile);
			}
			automobiles.add(automobile);
		}
		logger.debug("[CarsDirectProcess] - Automobile EDI parsing complete. Valid [{}] Invalid VINs [{}] Not enough data [{}]",
				com.findupon.commons.utilities.ConsoleColors.green(String.valueOf(automobiles.size())),
				com.findupon.commons.utilities.ConsoleColors.red(String.valueOf(invalidVins)),
				com.findupon.commons.utilities.ConsoleColors.red(String.valueOf(notEnoughAttributes)));
		return automobiles;
	}

	private String safeLookup(String[] arr, int pos) {
		if(arr.length >= pos + 1) {
			return StringUtils.trimToNull(arr[pos]);
		}
		return null;
	}
}
