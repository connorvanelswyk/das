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

package com.findupon.commons.utilities;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Methods of this class are for use when exceptions aren't an option, but neither is sacrificing
 * intelligence. As it grows, it will illustrate our attitude towards resolving these issues.
 *
 * @author connorvanelswyk
 */
public class NumUtils extends NumberUtils {

	private static final ImmutableSet<String> wordRemovalDictionary = ImmutableSet.of("mileage", "miles", ",", "$");
	private static final ImmutableSet<String> priceOnRequestKeywords = ImmutableSet.of("request", "p", "call for");

	private static final int NONE = 0;
	private static final int ERROR = -1;
	private static final int POR = -2;

	private static final String dollarSymbol = "$";
	private static final String decimal = ".";
	private static final String comma = ",";

	/**
	 * Attempt to return a meaningful Integer and avoid an {@see #IllegalFormatException} by removing all
	 * commas and decimal places prior to returning {@see Integer.valueOf()}.
	 *
	 * @param str <code>String</code> to parse
	 * @return <code>null</code> ? -1 : a decent <code>int</code>
	 */
	public static int intFrom(String str) {

        /*
         * Basically, if it's non sense...
         */
		if(isNotParsable(str)) {
			return ERROR;
		}

        /*
         * The current use case is for floating point (decimal) numbers. We need to make sure there
         * is only 1 decimal in an array of characters.
         */
		if(StringUtils.countMatches(str, decimal) == 1) {
			return Integer.valueOf(StringUtils.substringAfter(str, decimal));
		}
        
        /*
         * I found this block during implementation. It was happening prior to the lines of an area
         * that was undergoing improvement so, I'll lop it in. I say that because it kinda blindly
         * removes all commas. That could have some frown sides.
         */
		if(str.contains(comma)) {
			str = StringUtils.remove(str, comma);
		}

		return ERROR;
	}

	/**
	 * Removes characters commonly found in a currency String and attempts to convert the result
	 * into an Integer.
	 *
	 * @param str
	 * @return
	 */
	public static int fromCurrencyString(String str) {
		int errorCode = hasErrors(str);
		if(errorCode != NONE) {
			return errorCode;
		}
		str = StringUtils.remove(str, dollarSymbol);
		str = StringUtils.remove(str, comma);
		return toInt(StringUtils.trimToEmpty(str), -1);
	}

	/**
	 * Takes in a String formatted as 2137 km (1328 mi) and returns 1328.
	 *
	 * @param str
	 * @return
	 */
	public static int fromMileageString(String str) {

		int errorCode = hasErrors(str);
		if(errorCode != NONE) {
			return errorCode;
		}

		// Dupont Registry Bot
		for(String word : wordRemovalDictionary) {
			str = StringUtils.remove(str, word);
		}

		// James Edition Bot
		if(str.contains("(")) {
			str = StringUtils.substringAfter(str, "(");
			str = StringUtils.substringBefore(str, " mi");
		}

		// CarmaxAutomobile
		if(StringUtils.endsWith(str, "K") || StringUtils.endsWith(str, "k")) {
			str = StringUtils.replace(str, "K", "000");
			str = StringUtils.replace(str, "k", "000");
		}

		if(NumberUtils.isDigits(str)) {
			return Integer.valueOf(
					StringUtils.defaultString(
							StringUtils.trimToEmpty(str),
							"-1"));
		} else {
			return -1;
		}
	}

	/**
	 * Attempt to return a meaningful Integer exactly 4 digits in length. and a
	 * {@see #IllegalFormatException} by removing all commas and decimal places prior to returning
	 * {@see Integer.valueOf()}.
	 */
	public static int fourDigitIntFrom(String str) {

		int errorCode = hasErrors(str);
		if(errorCode != NONE) {
			return errorCode;
		}

		if(intFrom(str) == 4) {
			return intFrom(str);
		}

        /*
         * We can make this smarter, make it more directed towards years etc.
         */
		if(intFrom(str) > 4) {
			return Integer.valueOf(str.substring(0, 5));
		}
		return Integer.valueOf(str);
	}

	/**
	 * General error checking method.
	 *
	 * @param str
	 * @return
	 */
	private static int hasErrors(String str) {
		if(StringUtils.isBlank(str)) {
			return ERROR;
		}
		if(priceOnRequestKeywords.contains(str.toLowerCase())) {
			return POR;
		}
		return NONE;
	}


	public static int toInt(Integer i) {
		return i == null ? ERROR : i;
	}

	public static String toString(Integer i) {
		return toString(i, "0");
	}

	public static String toString(Integer i, String defaultStr) {
		return i == null ? defaultStr : i.toString();
	}

	public static boolean isNotParsable(String str) {
		return !isParsable(str);
	}

}
