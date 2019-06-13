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

package com.findupon.parsing;

import com.google.common.base.Stopwatch;
import com.plainviewrd.commons.entity.product.automotive.Automobile;
import com.plainviewrd.commons.searchparty.AutomotiveGatherer;
import com.plainviewrd.commons.searchparty.ScoutServices;
import com.plainviewrd.commons.utilities.AutomobileAttributeMatcher;
import com.plainviewrd.commons.utilities.ConsoleColors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/test-context.xml"})
public class AutomobileParsingTest {
	private static final Logger logger = LoggerFactory.getLogger(AutomobileParsingTest.class);

	private @Autowired AutomotiveGatherer automotiveGatherer;
	private @Autowired AutomobileAttributeMatcher attributeMatcher;


	@Test
	public void testMakeModelTrimParsing() {
		List<AutomobileTestFile> testFiles = loadTestFiles(null, null);
		logger.info("Testing parsing for [{}] automobiles", testFiles.size());

		Set<String> soldVins = new HashSet<>(Arrays.asList("WUARU78E87N901587", "WBABE632XSJC17451", "WBABF8326WEH61520", "WBABW33475PX83241", "WBAEU33482PH85551", "WBADX1C54BE570449", "WBAPM7G50ANL88975", "WBAVD53537A008688", "WBANV13538CZ54320", "WBALZ5C59CC619848", "WBAGL63442DP54938", "WBAGK2323WDH69305", "WBAEG2318MCB72105", "WBA3F9C5XDF484458", "SCBCC41N59CX13786", "3G5DB03E92S543062", "1G6AH5SX8E0113127", "1G6VR3386NU100153", "1G6RM1E48EU600734", "1G6DW69Y8G9730940", "1C3XV66R4ND779206", "1B3XC5631MD273467", "1B3BZ18E1FD267767", "3C3CFFFH3DT620303", "ZFFAA02A4C0037937", "ZFFXA19A8K0080682", "ZFFFG36A1L0086593", "ZFFXR48AXV0106408", "ZFFWP44A6X0114340", "ZFFLG40A6N0091549", "ZFFFC60A970153145", "ZFF74UFA5E0198979", "ZFFRK33AXN0091519", "1FDAE55S93HA80686", "4S6DM58WXY4415779", "JN1EY1AP8CM910883", "1J4RG4GK0AC113622", "SALAE24497A421260", "JTHKD5BH2D2159219", "5LTEW05A02KJ01100", "2LMHJ5FR0BBJ54549", "1LNLM91V2TY725200", "SCCDC0822XHA15658", "JM3ER293670149468", "SBM14DCA9JW000671", "SBM11AAA8CW000304", "WDDYJ7JA5GA001476", "WDDGJ4HB5DG009885", "WDDGF5HB1CR202092", "WDDLJ7DB0EA093950", "WDDLJ7GBXEA106019", "WDDZF4KB3HA053136", "WDCYR49E63X141076", "WDCYC7DF4GX244666", "4JGDF7EE6EA395330", "WDDTG5CB8FJ051094", "4JGBF7BEXBA690275", "4JGDA5JB6EA320304", "4JGBB7CB3BA694331", "4JGDA7EB2EA289865", "WDBSK70F29F147252", "4M2CN8B74AKJ24411", "1MEFM43135G611504", "1G3GR64H814107119", "1G3AM54N6L6365098", "1G2BT69Y1GX246881", "WP0CA2A11FS800381", "WP0CA296XSS840193", "5YJRE1A15A1000758", "JTDDR32T310092707", "JT3AC14R5S1193922", "WVWBK03D668005053", "YV1LW5546T2195867", "YV1MK672492145317", "YV1MS390582374145"));
		LongAdder buildMillis = new LongAdder();

		LongAdder prices = new LongAdder();
		LongAdder fuels = new LongAdder();
		LongAdder drives = new LongAdder();
		LongAdder bodies = new LongAdder();
		LongAdder trans = new LongAdder();
		LongAdder extColors = new LongAdder();
		LongAdder intColors = new LongAdder();

		for(AutomobileTestFile testFile : testFiles) {
			Stopwatch buildTimer = Stopwatch.createStarted();
			Automobile automobile = automotiveGatherer.buildProduct(testFile.document);
			buildMillis.add(buildTimer.elapsed(TimeUnit.MILLISECONDS));

			if(soldVins.contains(testFile.vin)) {
				Assert.assertNull(automobile);
				continue;
			} else {
				Assert.assertNotNull(automobile);
			}
			Assert.assertTrue("Wrong make for file " + testFile.fileName + " - expected " + testFile.make + " actual " + automobile.getMake(),
					Objects.equals(automobile.getMakeId(), attributeMatcher.getMakeId(testFile.make)));
			Assert.assertTrue("Wrong model for file " + testFile.fileName + " - expected " + testFile.model + " actual " + automobile.getModel(),
					Objects.equals(automobile.getModelId(), attributeMatcher.getModelId(automobile.getMakeId(), testFile.model)));

			if(automobile.getPrice() != null) {
				prices.increment();
			}
			if(automobile.getFuel() != null) {
				fuels.increment();
			}
			if(automobile.getDrivetrain() != null) {
				drives.increment();
			}
			if(automobile.getBody() != null) {
				bodies.increment();
			}
			if(automobile.getTransmission() != null) {
				trans.increment();
			}
			if(automobile.getExteriorColor() != null) {
				extColors.increment();
			}
			if(automobile.getInteriorColor() != null) {
				intColors.increment();
			}
			// Assert.assertEquals(automobile.getYear(), testFile.year);
			// Assert.assertEquals(automobile.getMileage(), testFile.miles);
			// Assert.assertEquals(automobile.getPrice(), testFile.price);
		}
		float totalDiv = (float)testFiles.size() / 100;
		logger.info(ConsoleColors.green("\n\nFound prices [{}] missing [{}] found rate [{}]"),
				prices.longValue(),
				testFiles.size() - prices.longValue(),
				String.format("%.3f%%", prices.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound fuels [{}] missing [{}] found rate [{}]"),
				fuels.longValue(),
				testFiles.size() - fuels.longValue(),
				String.format("%.3f%%", fuels.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound drivetrains [{}] missing [{}] found rate [{}]"),
				drives.longValue(),
				testFiles.size() - drives.longValue(),
				String.format("%.3f%%", drives.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound body types [{}] missing [{}] found rate [{}]"),
				bodies.longValue(),
				testFiles.size() - bodies.longValue(),
				String.format("%.3f%%", bodies.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound transmissions [{}] missing [{}] found rate [{}]"),
				trans.longValue(),
				testFiles.size() - trans.longValue(),
				String.format("%.3f%%", trans.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound exterior colors [{}] missing [{}] found rate [{}]"),
				extColors.longValue(),
				testFiles.size() - extColors.longValue(),
				String.format("%.3f%%", extColors.longValue() / totalDiv));
		logger.info(ConsoleColors.green("\nFound interior colors [{}] missing [{}] found rate [{}]"),
				intColors.longValue(),
				testFiles.size() - intColors.longValue(),
				String.format("%.3f%%", intColors.longValue() / totalDiv));

		double avgTimeSec = buildMillis.doubleValue() / 1000D / (double)testFiles.size();
		double carsPerSec = testFiles.size() / (buildMillis.doubleValue() / 1000);
		logger.info(ConsoleColors.green("Test complete. Total run [{}] Avg. Build Time [{}] Cars per sec [{}]\n\n\n"),
				testFiles.size(),
				String.format("%.3f", avgTimeSec),
				String.format("%.3f", carsPerSec));
	}

	private List<AutomobileTestFile> loadTestFiles(String make, String model) {
		List<AutomobileTestFile> testFiles = new ArrayList<>();
		try {
			for(Resource resource : new PathMatchingResourcePatternResolver().getResources("/parsing/**")) {
				try(InputStream is = resource.getInputStream()) {
					String html = IOUtils.toString(is, StandardCharsets.UTF_8);
					html = ScoutServices.normalize(html, false);
					AutomobileTestFile testFile = new AutomobileTestFile();
					testFile.fileName = resource.getFilename();
					String headerText = StringUtils.substringBetween(html, "<!--", "-->")
							.replace("<!--", "")
							.replace("-->", "")
							.trim();
					String[] header = headerText.split("\n");
					testFile.url = getValue(header, 0);
					testFile.vin = getValue(header, 1);
					testFile.make = getValue(header, 2);
					if(make != null && !StringUtils.equalsIgnoreCase(testFile.make, make)) {
						continue;
					}
					testFile.model = getValue(header, 3);
					if(model != null && !StringUtils.equalsIgnoreCase(testFile.model, model)) {
						continue;
					}
					String year = getValue(header, 4);
					if(NumberUtils.isDigits(year)) {
						testFile.year = Integer.parseInt(year);
					}
					String miles = getValue(header, 5);
					if(NumberUtils.isDigits(miles)) {
						testFile.miles = Integer.parseInt(miles);
					}
					String price = getValue(header, 6);
					if(price != null) {
						testFile.price = new BigDecimal(price);
					}
					testFile.document = Jsoup.parse(StringUtils.remove(html, headerText), testFile.url);
					testFiles.add(testFile);
				} catch(IOException e) {
					logger.warn("Could not get input stream for file [{}]", resource.getFilename(), e);
				}
			}
		} catch(IOException e) {
			logger.warn("Could not resolve parsing directory!", e);
		}
		return testFiles;
	}

	private String getValue(String[] arr, int index) {
		if(index >= arr.length) {
			return null;
		}
		String value = arr[index];
		if(StringUtils.isBlank(value)) {
			return null;
		}
		value = StringUtils.trimToNull(StringUtils.substringAfter(value, ":"));
		if(value == null) {
			return null;
		}
		if(StringUtils.equalsIgnoreCase(value, "null")) {
			return null;
		}
		return value;
	}

	private class AutomobileTestFile {
		Document document;
		String fileName;
		String make;
		String model;
		String url;
		String vin;
		Integer year;
		Integer miles;
		BigDecimal price;
	}
}
