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

package com.findupon.commons.bot.watercraft;

import com.findupon.commons.bot.ListingBot;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.findupon.commons.building.AutoParsingOperations.parsePrice;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class ListingWatercraftBot extends ListingBot<Watercraft> {

	private static final BigDecimal maxPrice = new BigDecimal("100000000");
	private static final BigDecimal minPrice = new BigDecimal("500");


	void printWatercraftSpecs(Watercraft watercraft) {
		if(!logger.isTraceEnabled()) {
			return;
		}
		if(watercraft == null) {
			logger.warn(logPre() + "Null watercraft! Bad Chad!");
			return;
		}
		String log = "\n----------------------------------------------------------------------" + "\n" +
				"Seller name:                  |   " + watercraft.getContactName() + "\n" +
				"Seller phone number:          |   " + watercraft.getContactPhone() + "\n" +
				"Seller city:                  |   " + watercraft.getContactCity() + "\n" +
				"Seller state:                 |   " + watercraft.getContactState() + "\n" +
				"Seller address:               |   " + watercraft.getContactAddress() + "\n" +
				"Seller postal code:           |   " + watercraft.getContactZipcode() + "\n" +
				"Watercraft address:           |   " + watercraft.getAddress() + "\n" +
				"Watercraft country:           |   " + watercraft.getCountry() + "\n" +
				"Watercraft latitude:          |   " + watercraft.getLatitude() + "\n" +
				"Watercraft longitude:         |   " + watercraft.getLongitude() + "\n" +
				"Type:                         |   " + watercraft.getWatercraftType() + "\n" +
				"Manufacturer:                 |   " + watercraft.getManufacturer() + "\n" +
				"Model:                        |   " + watercraft.getModel() + "\n" +
				"Year:                         |   " + watercraft.getYear() + "\n" +
				"Price:                        |   " + watercraft.getPrice() + "\n" +
				"Used?:                        |   " + watercraft.getUsed() + "\n" +
				"Length:                       |   " + watercraft.getLength() + "\n" +
				"Hull material:                |   " + watercraft.getHullType() + "\n" +
				"Fuel type:                    |   " + watercraft.getFuel() + "\n" +
				"Main image url:               |   " + watercraft.getMainImageUrl() + "\n" +
				"----------------------------------------------------------------------" + "\n";

		logger.debug(log);
	}

	public static BigDecimal parseAndValidatePrice(String priceStr) {
		BigDecimal price = parsePrice(priceStr, maxPrice);
		if(price != null && price.compareTo(minPrice) > 0) {
			return price;
		}
		return null;
	}
}
