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

package com.findupon.commons.entity.datasource;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;


public enum AssetType {
	/* NEVER CHANGE THESE IDS */
	AUTOMOBILE(1),
	REAL_ESTATE(2),
	WATERCRAFT(3),
	AIRCRAFT(4),
	RETAIL(5),
	COMMERCIAL(6);

	private final Integer id;

	AssetType(Integer id) {
		this.id = id;
	}

	public static AssetType of(Integer id) {
		if(id == null) {
			return null;
		}
		for(AssetType assetType : values()) {
			if(assetType.getId().equals(id)) {
				return assetType;
			}
		}
		return null;
	}

	public Integer getId() {
		return id;
	}

	@Converter(autoApply = true)
	public static class ConverterImpl implements AttributeConverter<AssetType, Integer> {

		@Override
		public Integer convertToDatabaseColumn(AssetType assetType) {
			return assetType == null ? null : assetType.getId();
		}

		@Override
		public AssetType convertToEntityAttribute(Integer dbData) {
			return AssetType.of(dbData);
		}
	}
}
