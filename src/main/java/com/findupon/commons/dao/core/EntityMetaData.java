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

package com.findupon.commons.dao.core;

import com.findupon.commons.entity.AbstractEntity;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public class EntityMetaData<A extends AbstractEntity<PK>, PK extends Number> {

	private String tableName;
	private Class<A> clazz;
	private final Map<String, Field> columnFields = new LinkedHashMap<>();

	public String getTableName() {
		return Objects.requireNonNull(tableName);
	}

	void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public Class<A> getClazz() {
		return Objects.requireNonNull(clazz);
	}

	void setClazz(Class<A> clazz) {
		this.clazz = clazz;
	}

	public Map<String, Field> getColumnFields() {
		return columnFields;
	}
}
