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

package com.findupon.commons.entity.building;

import org.apache.commons.lang3.StringUtils;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public enum State {
	ALABAMA("Alabama", "AL"),
	ALASKA("Alaska", "AK"),
	ARIZONA("Arizona", "AZ"),
	ARKANSAS("Arkansas", "AR"),
	CALIFORNIA("California", "CA"),
	COLORADO("Colorado", "CO"),
	CONNECTICUT("Connecticut", "CT"),
	DELAWARE("Delaware", "DE"),
	DISTRICT_OF_COLUMBIA("District of Columbia", "DC"),
	FLORIDA("Florida", "FL"),
	GEORGIA("Georgia", "GA"),
	HAWAII("Hawaii", "HI"),
	IDAHO("Idaho", "ID"),
	ILLINOIS("Illinois", "IL"),
	INDIANA("Indiana", "IN"),
	IOWA("Iowa", "IA"),
	KANSAS("Kansas", "KS"),
	KENTUCKY("Kentucky", "KY"),
	LOUISIANA("Louisiana", "LA"),
	MAINE("Maine", "ME"),
	MARYLAND("Maryland", "MD"),
	MASSACHUSETTS("Massachusetts", "MA"),
	MICHIGAN("Michigan", "MI"),
	MINNESOTA("Minnesota", "MN"),
	MISSISSIPPI("Mississippi", "MS"),
	MISSOURI("Missouri", "MO"),
	MONTANA("Montana", "MT"),
	NEBRASKA("Nebraska", "NE"),
	NEVADA("Nevada", "NV"),
	NEW_HAMPSHIRE("New Hampshire", "NH"),
	NEW_JERSEY("New Jersey", "NJ"),
	NEW_MEXICO("New Mexico", "NM"),
	NEW_YORK("New York", "NY"),
	NORTH_CAROLINA("North Carolina", "NC"),
	NORTH_DAKOTA("North Dakota", "ND"),
	OHIO("Ohio", "OH"),
	OKLAHOMA("Oklahoma", "OK"),
	OREGON("Oregon", "OR"),
	PENNSYLVANIA("Pennsylvania", "PA"),
	RHODE_ISLAND("Rhode Island", "RI"),
	SOUTH_CAROLINA("South Carolina", "SC"),
	SOUTH_DAKOTA("South Dakota", "SD"),
	TENNESSEE("Tennessee", "TN"),
	TEXAS("Texas", "TX"),
	UTAH("Utah", "UT"),
	VERMONT("Vermont", "VT"),
	VIRGINIA("Virginia", "VA"),
	WASHINGTON("Washington", "WA"),
	WEST_VIRGINIA("West Virginia", "WV"),
	WISCONSIN("Wisconsin", "WI"),
	WYOMING("Wyoming", "WY");

	private final String name;
	private final String abbreviation;

	public static final Map<String, State> abbreviationsToState = new HashMap<>();
	public static final Set<String> stateNames = new HashSet<>();
	public static final Set<String> stateAbbreviations = new HashSet<>();
	public static final Set<String> paddedStateAbbreviations = new HashSet<>();

	static {
		for(State state : values()) {
			abbreviationsToState.put(state.getAbbreviation(), state);
			stateNames.add(state.getName());
			stateAbbreviations.add(state.getAbbreviation());
			paddedStateAbbreviations.add(" " + state.getAbbreviation() + " ");
		}
	}

	State(String name, String abbreviation) {
		this.name = name;
		this.abbreviation = abbreviation;
	}

	@Override
	public String toString() {
		return abbreviation;
	}

	public String getAbbreviation() {
		return abbreviation;
	}

	public String getName() {
		return name;
	}

	public static State valueOfAbbreviation(String abbreviation) {
		abbreviation = StringUtils.trimToNull(abbreviation);
		if(abbreviation == null) {
			return null;
		}
		abbreviation = abbreviation.toUpperCase();
		State state = abbreviationsToState.get(abbreviation);
		if(state != null) {
			return state;
		} else {
			return null;
		}
	}

	public static State valueOfName(String name) {
		name = StringUtils.trimToNull(name);
		if(name == null) {
			return null;
		}
		String enumName = name.toUpperCase().replaceAll(" ", "_");
		try {
			return valueOf(enumName);
		} catch(IllegalArgumentException e) {
			return null;
		}
	}

	@Converter(autoApply = true)
	public static class ConverterImpl implements AttributeConverter<State, String> {

		@Override
		public String convertToDatabaseColumn(State attribute) {
			return attribute == null ? null : attribute.abbreviation;
		}

		@Override
		public State convertToEntityAttribute(String dbData) {
			return State.valueOfAbbreviation(dbData);
		}
	}
}
