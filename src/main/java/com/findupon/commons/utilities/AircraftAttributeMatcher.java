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

import com.findupon.commons.entity.product.aircraft.AircraftCategory;
import com.findupon.commons.entity.product.aircraft.AircraftMake;
import com.findupon.commons.entity.product.aircraft.AircraftModel;
import com.findupon.utilities.PermutableAttribute;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
public class AircraftAttributeMatcher {

	private static final Set<Integer> faaTurbineEngineIdSet = new HashSet<>(Arrays.asList(2, 3, 4, 5, 6));
	private static final Set<Integer> faaPistonEngineIdSet = new HashSet<>(Arrays.asList(1, 7, 8, 11));

	private static final Map<Integer, PermutableAttribute> fullAttributeMap = new HashMap<>();
	private static final Map<Integer, String> quickCatMap = new HashMap<>();
	private static final Map<Integer, String> quickMakeMap = new HashMap<>();
	private static final Map<Integer, String> quickModelMap = new HashMap<>();
	private static final Map<String, Integer> mmsCatMap = new HashMap<>();
	private static final Map<String, String> mmsMkeMap = new HashMap<>();
	private static final Map<String, String> mmsMdlMap = new HashMap<>();
	private static final Map<String, Integer> mmsSeatMap = new HashMap<>();
	private static final Set<String> regNumPrefixSet = new HashSet<>();

	private static final AtomicBoolean loadLock = new AtomicBoolean();
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	public AircraftAttributeMatcher(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		checkLoad();
	}

	private void checkLoad() {
		if(!loadLock.get()) {
			synchronized(loadLock) {
				loadAttributes();
			}
		}
	}

	private void loadAttributes() {
		if(loadLock.get()) {
			return;
		}
		loadLock.set(true);
		String sql =
				"select cat.id category_id, m.id make_id, mo.id model_id, cat.category category, m.make make, mo.model model" +
						" from aircraft_category_make_model xref" +
						" left join aircraft_category cat on cat.id = xref.category_id" +
						" left join aircraft_make m on m.id = xref.make_id" +
						" left join aircraft_model mo on mo.id = xref.model_id";
		jdbcTemplate.query(sql, rs -> {
			int catId = rs.getInt("category_id");
			int makeId = rs.getInt("make_id");
			int modelId = rs.getInt("model_id");
			fullAttributeMap.computeIfAbsent(catId, x -> {
				try {
					String cat = rs.getString("category");
					quickCatMap.putIfAbsent(catId, cat);
					return new AircraftCategory(cat);
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}
			}).getChildren().computeIfAbsent(makeId, x -> {
				try {
					String make = rs.getString("make");
					quickMakeMap.putIfAbsent(makeId, make);
					return new AircraftMake(make);
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}
			}).getChildren().computeIfAbsent(modelId, x -> {
				try {
					String model = rs.getString("model");
					quickModelMap.putIfAbsent(modelId, model);
					return new AircraftModel(model);
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}
			});
		});

		jdbcTemplate
				.queryForList("select registration_prefix from aircraft_registration_prefixes", String.class)
				.forEach(s -> regNumPrefixSet.add(s));

		String s = "select mfr_mdl_srs_code, mfr_name, mdl_name, seat_count, acft_type_id, eng_type_id from aircraft_faa_acftref";

		jdbcTemplate.query(s, rs -> {

			String code = rs.getString("mfr_mdl_srs_code");

			mmsMkeMap.put(code, rs.getString("mfr_name"));
			mmsMdlMap.put(code, rs.getString("mdl_name"));

			int catId = -1;
			Integer engType = rs.getInt("eng_type_id");
			Integer airType = rs.getInt("acft_type_id");
			if(engType.equals(2)) {
				catId = 3;
			} else if(faaTurbineEngineIdSet.contains(engType)) {
				catId = airType.equals(6) ? 4 : 0;
			} else if(faaPistonEngineIdSet.contains(engType)) {
				catId = airType.equals(6) ? 5 : airType.equals(5) ? 2 : 1;
			}
			if(catId != -1) {
				mmsCatMap.put(code, catId);
			}

			Integer seats = rs.getInt("seat_count");
			if(seats > 0) {
				mmsSeatMap.put(code, seats);
			}
		});
	}

	public boolean containsValidRegistrationPrefix(String regNum) {
		return regNumPrefixSet.stream().anyMatch(regNumPrefix -> StringUtils.startsWith(regNum, regNumPrefix));
	}

	public int getCatIdFromMMS(String mms) {
		return mmsCatMap.getOrDefault(mms, -1);
	}

	public int getMkeIdFromMMS(String mms) {
		int result = -1;
		String make = mmsMkeMap.getOrDefault(mms, null);
		if(StringUtils.isEmpty(make)) {
			return result;
		}
		checkLoad();
		result = quickMakeMap.entrySet().stream()
				.filter(e -> StringUtils.containsIgnoreCase(make, e.getValue()))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);

		if(result == -1) {
			result = quickMakeMap.entrySet().stream()
					.filter(e -> new JaroWinklerDistance().apply(make, e.getValue()) >= .9)
					.findFirst()
					.map(Map.Entry::getKey)
					.orElse(-1);
		}
		return result;
	}

	public int getMdlIdFromMMS(String mms) {
		int result = -1;
		String model = mmsMdlMap.getOrDefault(mms, null);
		if(StringUtils.isEmpty(model)) {
			return result;
		}
		checkLoad();
		result = quickModelMap.entrySet().stream()
				.filter(e -> StringUtils.containsIgnoreCase(model, e.getValue()))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);

		if(result == -1) {
			result = quickModelMap.entrySet().stream()
					.filter(Objects::nonNull)
					.filter(e -> Objects.nonNull(e.getValue()))
					.filter(e -> new JaroWinklerDistance().apply(model, e.getValue()) >= .9)
					.findFirst()
					.map(Map.Entry::getKey)
					.orElse(-1);
		}
		return result;
	}

	public int getSeatsFromMMS(String mms) {
		if(StringUtils.isEmpty(mms)) {
			return -1;
		}
		checkLoad();
		return mmsSeatMap.getOrDefault(mms, -1);
	}

	public int getMakeId(String make) {
		if(StringUtils.isEmpty(make)) {
			return -1;
		}
		checkLoad();
		return quickMakeMap.entrySet().stream()
				.filter(e -> StringUtils.equalsIgnoreCase(e.getValue(), make))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);
	}

	public int getModelId(String model) {
		if(StringUtils.isEmpty(model)) {
			return -1;
		}
		checkLoad();
		return quickModelMap.entrySet().stream()
				.filter(e -> StringUtils.equalsIgnoreCase(e.getValue(), model))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);
	}

	public String getMake(Integer makeId) {
		if(makeId == null || makeId < 1) {
			return null;
		}
		checkLoad();
		return quickMakeMap.get(makeId);
	}

	public String getModel(Integer modelId) {
		if(modelId == null || modelId < 1) {
			return null;
		}
		checkLoad();
		return quickModelMap.get(modelId);
	}

	public String getCategory(Integer categoryId) {
		if(categoryId == null || categoryId < 1) {
			return null;
		}
		checkLoad();
		return quickCatMap.get(categoryId);
	}

	public Map<Integer, PermutableAttribute> getFullAttributeMap() {
		checkLoad();
		return fullAttributeMap;
	}
}
