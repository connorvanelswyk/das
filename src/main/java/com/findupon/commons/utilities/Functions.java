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

import java.util.Comparator;
import java.util.Objects;
import java.util.function.*;
import java.util.stream.Collector;


public final class Functions {

	@FunctionalInterface
	public interface ThrowingConsumer<T, E extends Exception> {

		void accept(T t) throws E;

		default ThrowingConsumer<T, E> andThen(Consumer<? super T> after) {
			Objects.requireNonNull(after);
			return (T t) -> {
				accept(t);
				after.accept(t);
			};
		}
	}

	@FunctionalInterface
	public interface TriFunction<X, Y, Z, R> {

		R apply(X x, Y y, Z z);

		default <V> TriFunction<X, Y, Z, V> andThen(Function<? super R, ? extends V> after) {
			Objects.requireNonNull(after);
			return (X x, Y y, Z z) -> after.apply(apply(x, y, z));
		}
	}

	public static <T, A, D> Collector<T, ?, D> allMax(Comparator<? super T> comparator,
	                                                  Collector<? super T, A, D> downstream) {
		Supplier<A> downstreamSupplier = downstream.supplier();
		BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
		BinaryOperator<A> downstreamCombiner = downstream.combiner();
		return Collector.of(() -> new DownstreamContainer<A, T>(downstreamSupplier.get()), (acc, t) -> {
			if(!acc.hasAny) {
				downstreamAccumulator.accept(acc.acc, t);
				acc.obj = t;
				acc.hasAny = true;
			} else {
				int cmp = comparator.compare(t, acc.obj);
				if(cmp > 0) {
					acc.acc = downstreamSupplier.get();
					acc.obj = t;
				}
				if(cmp >= 0) {
					downstreamAccumulator.accept(acc.acc, t);
				}
			}
		}, (acc1, acc2) -> {
			if(!acc2.hasAny) {
				return acc1;
			}
			if(!acc1.hasAny) {
				return acc2;
			}
			int cmp = comparator.compare(acc1.obj, acc2.obj);
			if(cmp > 0) {
				return acc1;
			}
			if(cmp < 0) {
				return acc2;
			}
			acc1.acc = downstreamCombiner.apply(acc1.acc, acc2.acc);
			return acc1;
		}, acc -> downstream.finisher().apply(acc.acc));
	}

	private static final class DownstreamContainer<A, T> {
		private A acc;
		private T obj;
		private boolean hasAny;

		private DownstreamContainer(A acc) {
			this.acc = acc;
		}
	}
}
