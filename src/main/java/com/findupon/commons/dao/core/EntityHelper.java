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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;

import javax.persistence.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;


public final class EntityHelper<A extends AbstractEntity<PK>, PK extends Number> {
	private static final Logger logger = LoggerFactory.getLogger(EntityHelper.class);

	public static final EntityHelper INSTANCE = new EntityHelper();

	private final String packagesToScan = "com.findupon.commons.entity";
	private final Map<Class<A>, EntityMetaData<A, PK>> entityMetaDataMap = new HashMap<>();


	private EntityHelper() {
		logger.debug("[EntityHelper] - Loading entity meta data...");

		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter((r, f) -> {
			AnnotationMetadata d = r.getAnnotationMetadata();
			return d.hasAnnotation(Table.class.getName()) && d.hasAnnotation(Entity.class.getName());
		});
		for(BeanDefinition bean : provider.findCandidateComponents(packagesToScan)) {
			try {
				Class<?> clazz = Class.forName(bean.getBeanClassName());
				if(!AbstractEntity.class.isAssignableFrom(clazz)) {
					logger.trace("[EntityHelper] - Class [{}] is not an abstract entity", clazz.getSimpleName());
					continue;
				}
				String tableName = null;
				for(Annotation annotation : clazz.getAnnotations()) {
					String name = annotation.annotationType().getName();
					if(Table.class.getName().equals(name)) {
						Class<? extends Annotation> type = annotation.annotationType();
						for(Method method : type.getDeclaredMethods()) {
							if("name".equals(method.getName())) {
								tableName = (String)method.invoke(annotation, (Object[])null);
							}
						}
					}
				}
				if(tableName == null) {
					throw new UnsupportedOperationException("Entity must have table with name");
				}
				Class<A> entityClass = (Class<A>)clazz;
				EntityMetaData<A, PK> metaData = new EntityMetaData<>();
				metaData.setTableName(tableName);
				metaData.setClazz(entityClass);

				Stack<Class<?>> entityHierarchy = new Stack<>(); // keep columns in order
				for(; isDataClass(clazz); clazz = clazz.getSuperclass()) {
					entityHierarchy.push(clazz);
				}
				while(!entityHierarchy.isEmpty()) {
					for(Field f : entityHierarchy.pop().getDeclaredFields()) {
						if(f.isAnnotationPresent(Id.class)) {
							f.setAccessible(true);
							if(metaData.getColumnFields().put("id", f) != null) {
								throw new UnsupportedOperationException("Multiple column field mapping");
							}
						} else if(f.isAnnotationPresent(Column.class)) {
							String name = f.getAnnotation(Column.class).name();
							if(StringUtils.isNotEmpty(name)) {
								f.setAccessible(true);
								if(metaData.getColumnFields().put(name, f) != null) {
									throw new UnsupportedOperationException("Multiple column field mapping");
								}
							} else {
								throw new UnsupportedOperationException("Column annotations must have name value");
							}
						}
					}
					entityMetaDataMap.put(entityClass, metaData);
				}
			} catch(Exception e) {
				throw new RuntimeException(e); // let fatal
			}
		}
		logger.debug("[EntityHelper] - Entity meta data load completed.");
	}

	public EntityMetaData<A, PK> getEntityMetaData(Class<A> clazz) {
		return entityMetaDataMap.get(clazz);
	}

	private boolean isDataClass(Class<?> clazz) {
		if(clazz == null) {
			return false;
		}
		for(Annotation annotation : clazz.getAnnotations()) {
			String name = annotation.annotationType().getName();
			if(MappedSuperclass.class.getName().equals(name)) {
				return true;
			}
			if(Table.class.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}
}
