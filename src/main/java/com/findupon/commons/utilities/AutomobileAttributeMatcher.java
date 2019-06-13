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

import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.entity.product.attribute.Body;
import com.findupon.commons.entity.product.attribute.Drivetrain;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.Transmission;
import com.findupon.commons.entity.product.automotive.AutomobileMake;
import com.findupon.commons.entity.product.automotive.AutomobileModel;
import com.findupon.commons.entity.product.automotive.AutomobileTrim;
import com.findupon.utilities.PermutableAttribute;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class AutomobileAttributeMatcher {
	private static final Map<Integer, PermutableAttribute> fullAttributeMap = new HashMap<>();
	private static final Map<Integer, String> quickMakeMap = new HashMap<>();
	private static final Map<Integer, String> quickModelMap = new HashMap<>();
	private static final Map<Integer, String> quickTrimMap = new HashMap<>();
	private static final Map<Integer, List<Fuel>> modelFuelMap = new HashMap<>();
	private static final Map<Integer, List<Transmission>> modelTransmissionMap = new HashMap<>();
	private static final Map<Integer, List<Drivetrain>> modelDrivetrainMap = new HashMap<>();
	private static final Map<Integer, List<Body>> modelBodyMap = new HashMap<>();

	private static final AtomicBoolean loadLock = new AtomicBoolean();
	private final JdbcTemplate jdbcTemplate;


	@Autowired
	public AutomobileAttributeMatcher(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		checkLoad();
	}

	private void loadAttributes() {
		if(loadLock.get()) {
			return;
		}
		loadLock.set(true);
		jdbcTemplate.query("select mk.id make_id,make,mo.id model_id,model,allow_direct_trim_match dtm," +
				"allow_trim_concat_match acm,price_min,price_max,year_min,year_max,tr.id trim_id,trim" +
				" from automobile_make mk" +
				" join automobile_model mo on mk.id = mo.make_id" +
				" left join automobile_model_trim tr on mo.id = tr.model_id" +
				" left join automobile_model_price_range pr on mo.id = pr.model_id" +
				" left join automobile_model_year_range yr on mo.id = yr.model_id", rs -> {
			int makeId = rs.getInt("make_id");
			int modelId = rs.getInt("model_id");
			int trimId = rs.getInt("trim_id");
			String trim = rs.getString("trim");
			fullAttributeMap.computeIfAbsent(makeId, x -> {
				try {
					String make = rs.getString("make");
					quickMakeMap.putIfAbsent(makeId, make);
					return new AutomobileMake(make);
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}
			}).getChildren().computeIfAbsent(modelId, x -> {
				try {
					AutomobileModel automobileModel = new AutomobileModel(rs.getString("model"), rs.getBoolean("dtm"), rs.getBoolean("acm"));
					automobileModel.setMinYear(rs.getInt("year_min"));
					automobileModel.setMaxYear(rs.getInt("year_max"));
					automobileModel.setMinPrice(rs.getInt("price_min"));
					automobileModel.setMaxPrice(rs.getInt("price_max"));
					quickModelMap.putIfAbsent(modelId, automobileModel.getAttribute());
					return automobileModel;
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}
			}).getChildren().computeIfAbsent(trimId, x -> {
				quickTrimMap.putIfAbsent(trimId, trim);
				return new AutomobileTrim(trim);
			});
		});
		computePermutations(fullAttributeMap);
		jdbcTemplate.query("select model_id, fuel_id from automobile_model_fuel_xref", rs -> {
			modelFuelMap.computeIfAbsent(rs.getInt("model_id"), x -> new ArrayList<>()).add(Fuel.of(rs.getInt("fuel_id")));
		});
		jdbcTemplate.query("select model_id, transmission_id from automobile_model_transmission_xref", rs -> {
			modelTransmissionMap.computeIfAbsent(rs.getInt("model_id"), x -> new ArrayList<>()).add(Transmission.of(rs.getInt("transmission_id")));
		});
		jdbcTemplate.query("select model_id, drivetrain_id from automobile_model_drivetrain_xref", rs -> {
			modelDrivetrainMap.computeIfAbsent(rs.getInt("model_id"), x -> new ArrayList<>()).add(Drivetrain.of(rs.getInt("drivetrain_id")));
		});
		jdbcTemplate.query("select model_id, body_id from automobile_model_body_xref", rs -> {
			modelBodyMap.computeIfAbsent(rs.getInt("model_id"), x -> new ArrayList<>()).add(Body.of(rs.getInt("body_id")));
		});
	}

	private void computePermutations(Map<Integer, PermutableAttribute> attributeMap) {
		for(Map.Entry<Integer, PermutableAttribute> entry : attributeMap.entrySet()) {
			PermutableAttribute a = entry.getValue();
			a.getPermutations().addAll(AttributeVariations.compute(a.getAttribute()));
			if(a.isAllowDirectChildMatch()) {
				a.getChildren().forEach((k, v) ->
						a.getPermutations().addAll(AttributeVariations.compute(v.getAttribute())));
			} else if(a.isAllowChildConcatMatch()) {
				a.getChildren().forEach((k, v) ->
						AttributeVariations.compute(v.getAttribute()).stream()
								.filter(p -> p.length() <= 4 || p.charAt(1) == ' ')
								.forEach(p -> a.getPermutations().add(a.getAttribute() + p)));
			}
			computePermutations(a.getChildren());
		}
	}

	private void checkLoad() {
		if(!loadLock.get()) {
			synchronized(loadLock) {
				loadAttributes();
			}
		}
	}

	public Map<Integer, PermutableAttribute> getFullAttributeMap() {
		checkLoad();
		return fullAttributeMap;
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

	public String getTrim(Integer trimId) {
		if(trimId == null || trimId < 1) {
			return null;
		}
		checkLoad();
		return quickTrimMap.get(trimId);
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

	public int getModelId(Integer makeId, String model) {
		if(makeId == null || makeId < 1 || StringUtils.isEmpty(model)) {
			return -1;
		}
		checkLoad();
		PermutableAttribute make = fullAttributeMap.get(makeId);
		if(make == null) {
			return -1;
		}
		return make.getChildren().entrySet().stream()
				.filter(e -> StringUtils.equalsIgnoreCase(e.getValue().getAttribute(), model))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);
	}

	public int getTrimId(Integer makeId, Integer modelId, String trim) {
		if(makeId == null || makeId < 1 || modelId == null || modelId < 1 || StringUtils.isEmpty(trim)) {
			return -1;
		}
		checkLoad();
		PermutableAttribute make = fullAttributeMap.get(makeId);
		if(make == null) {
			return -1;
		}
		PermutableAttribute model = make.getChildren().get(modelId);
		if(model == null) {
			return -1;
		}
		return model.getChildren().entrySet().stream()
				.filter(e -> StringUtils.equalsIgnoreCase(e.getValue().getAttribute(), trim))
				.findFirst()
				.map(Map.Entry::getKey)
				.orElse(-1);
	}

	public List<Fuel> getModelFuelTypes(Integer modelId) {
		checkLoad();
		if(modelId == null) {
			return new ArrayList<>();
		}
		return modelFuelMap.getOrDefault(modelId, new ArrayList<>());
	}

	public List<Transmission> getModelTransmissionTypes(Integer modelId) {
		checkLoad();
		if(modelId == null) {
			return new ArrayList<>();
		}
		return modelTransmissionMap.getOrDefault(modelId, new ArrayList<>());
	}

	public List<Drivetrain> getModelDrivetrainTypes(Integer modelId) {
		checkLoad();
		if(modelId == null) {
			return new ArrayList<>();
		}
		return modelDrivetrainMap.getOrDefault(modelId, new ArrayList<>());
	}

	public List<Body> getModelBodyTypes(Integer modelId) {
		checkLoad();
		if(modelId == null) {
			return new ArrayList<>();
		}
		return modelBodyMap.getOrDefault(modelId, new ArrayList<>());
	}
}

final class AttributeVariations {
	private static final Set<String> bmwXVariants = new HashSet<>(Arrays.asList("328xi", "325xi", "330xi", "335xi", "535xi", "530xi", "528xi"));
	private static final Set<String> bmwCVariants = new HashSet<>(Arrays.asList("323Ci", "328Ci", "325Ci", "330Ci"));
	private static final Set<String> bmwIVariants = new HashSet<>(Arrays.asList("528i", "525i", "530i", "328i", "325i"));
	private static final Set<String> bmwEVariants = new HashSet<>(Collections.singletonList("740e xDrive"));
	private static final Set<String> bmwXDriveVariants = new HashSet<>(Arrays.asList("650i xDrive", "640i xDrive", "750i xDrive",
			"740i xDrive", "750Li xDrive", "740Li xDrive", "M235i xDrive", "M240i xDrive", "228i xDrive", "230i xDrive",
			"340i xDrive", "428i xDrive", "430i xDrive", "435i xDrive", "428i xDrive", "440i xDrive"
	));
	private static final Set<String> volvoCCVariants = new HashSet<>(Arrays.asList("S60 Cross Country", "V60 Cross Country", "V90 Cross Country"));
	private static final Set<String> ferrariFVariants = new HashSet<>(Collections.singletonList("F355"));
	private static final Pattern tresNumberLetterPattern = Pattern.compile("^[\\d]{3}[a-zA-Z]{1,2}$"); // 328i should match from 328 i

	static List<String> compute(String attribute) {
		final List<String> variants = new ArrayList<>();
		variants.add(attribute);
		if(bmwXVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, "xi", "ix"));
			variants.add(StringUtils.replace(attribute, "xi", "xiT"));
			variants.add(StringUtils.replace(attribute, "xi", "xiC"));
		} else if(bmwCVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, "Ci", "ic"));
			variants.add(StringUtils.replace(attribute, "Ci", "CiT"));
			variants.add(StringUtils.replace(attribute, "Ci", "CiC"));
		} else if(bmwIVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, "i", "iAT"));
			variants.add(StringUtils.replace(attribute, "i", "iA"));
			variants.add(StringUtils.replace(attribute, "i", "iT"));
		} else if(bmwXDriveVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, "i xDrive", "xi"));
		} else if(bmwEVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, "e xDrive", "xe"));
		} else if(volvoCCVariants.contains(attribute)) {
			variants.add(StringUtils.replace(attribute, " Cross Country", "CC"));
		} else if(ferrariFVariants.contains(attribute)) {
			variants.add(StringUtils.remove(attribute, "F"));
		} else if(StringUtils.contains(attribute, " AMG")) {
			variants.add("AMG " + StringUtils.remove(attribute, " AMG"));
		} else if(StringUtils.equals(attribute, "Mercedes-Benz")) {
			variants.add("Mercedes");
		} else if(StringUtils.contains(attribute, "&")) {
			variants.add(StringUtils.replace(attribute, "&", "and"));
		} else if(StringUtils.contains(attribute, "and")) {
			variants.add(StringUtils.replace(attribute, "and", "&"));
		}
		if(attribute != null) {
			Matcher m = tresNumberLetterPattern.matcher(attribute);
			if(m.find()) {
				int position = m.start() + 3;
				variants.add(attribute.substring(0, position) + " " + attribute.substring(position));
			}
		}
		return AttributeOperations.buildSpaceDashSimilarity(variants);
	}
}
