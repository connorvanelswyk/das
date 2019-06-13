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

package com.findupon.commons.dao;

import com.findupon.commons.entity.AbstractEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


interface EntityDao<A extends AbstractEntity<PK>, PK extends Number> {

	<E extends A> void save(E entity);

	<E extends A> void saveAll(Collection<E> entities);

	<E extends A> Optional<E> find(E entity);

	<E extends A> Optional<E> findById(PK id);

	<E extends A> List<E> findAllById(Collection<PK> ids);

	long count();

	<E extends A> void delete(E entity);

	<E extends A> void deleteAll(Collection<E> entities);

	void deleteById(PK id);

	void deleteAllById(Collection<PK> ids);
}
