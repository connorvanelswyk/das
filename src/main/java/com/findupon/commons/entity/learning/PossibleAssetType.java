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

package com.findupon.commons.entity.learning;

import com.findupon.commons.entity.datasource.AssetType;

import java.io.Serializable;


public class PossibleAssetType implements Serializable {
	private static final long serialVersionUID = -1714101262029305589L;

	private AssetType assetType;
	private boolean guess;
	private double relativeProbability;


	@Override
	public String toString() {
		return "PossibleAssetType{" +
				"assetType=" + assetType +
				", guess=" + guess +
				", relativeProbability=" + relativeProbability +
				'}';
	}

	public AssetType getAssetType() {
		return assetType;
	}

	public void setAssetType(AssetType assetType) {
		this.assetType = assetType;
	}

	public boolean isGuess() {
		return guess;
	}

	public void setGuess(boolean guess) {
		this.guess = guess;
	}

	public double getRelativeProbability() {
		return relativeProbability;
	}

	public void setRelativeProbability(double relativeProbability) {
		this.relativeProbability = relativeProbability;
	}
}
