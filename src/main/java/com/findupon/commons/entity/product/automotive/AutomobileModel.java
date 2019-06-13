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

package com.findupon.commons.entity.product.automotive;

import com.findupon.utilities.PermutableAttribute;

import java.io.Serializable;


public class AutomobileModel extends PermutableAttribute implements Serializable {
	private static final long serialVersionUID = -3951549666952444901L;
	private static final int defaultMinYear = 1950;
	private static final int defaultMaxYear = 2020;
	private static final int defaultMinPrice = 1000;
	private static final int defaultMaxPrice = 100_000;

	private int minYear;
	private int maxYear;
	private int minPrice;
	private int maxPrice;


	public AutomobileModel(String model, boolean allowDirectChildMatch, boolean allowChildConcatMatch) {
		super(model, allowDirectChildMatch, allowChildConcatMatch);
	}

	public int getMinYear() {
		return minYear;
	}

	public void setMinYear(int minYear) {
		if(minYear <= 0) {
			this.minYear = defaultMinYear;
		} else {
			this.minYear = minYear;
		}
	}

	public int getMaxYear() {
		return maxYear;
	}

	public void setMaxYear(int maxYear) {
		if(maxYear <= 0) {
			this.maxYear = defaultMaxYear;
		} else {
			this.maxYear = maxYear;
		}
	}

	public int getMinPrice() {
		return minPrice;
	}

	public void setMinPrice(int minPrice) {
		if(minPrice <= 0) {
			this.minPrice = defaultMinPrice;
		} else {
			this.minPrice = minPrice;
		}
	}

	public int getMaxPrice() {
		return maxPrice;
	}

	public void setMaxPrice(int maxPrice) {
		if(maxPrice <= 0) {
			this.maxPrice = defaultMaxPrice;
		} else {
			this.maxPrice = maxPrice;
		}
	}
}
