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


public enum DataSourceType {
	/* NEVER CHANGE THESE IDS */
	GENERIC(1),
	LISTING(2),
	IMPORT_PROCESS(3);

	private final Integer id;

	DataSourceType(Integer id) {
		this.id = id;
	}

	public static DataSourceType of(Integer id) {
		if(id == null) {
			return null;
		}
		for(DataSourceType dataSourceType : values()) {
			if(dataSourceType.getId().equals(id)) {
				return dataSourceType;
			}
		}
		return null;
	}

	public Integer getId() {
		return id;
	}

	@Converter(autoApply = true)
	public static class ConverterImpl implements AttributeConverter<DataSourceType, Integer> {

		@Override
		public Integer convertToDatabaseColumn(DataSourceType dataSourceType) {
			return dataSourceType == null ? null : dataSourceType.getId();
		}

		@Override
		public DataSourceType convertToEntityAttribute(Integer dbData) {
			return DataSourceType.of(dbData);
		}
	}
}
