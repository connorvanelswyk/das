
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

package com.findupon.utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class PermutableAttribute {

	private final String attribute;
	private boolean allowDirectChildMatch;
	private boolean allowChildConcatMatch;
	private final List<String> permutations;
	private final Map<Integer, PermutableAttribute> children;


	public PermutableAttribute(String attribute) {
		this.attribute = attribute;
		this.permutations = new ArrayList<>();
		this.children = new HashMap<>();
	}

	public PermutableAttribute(String attribute, boolean allowDirectChildMatch, boolean allowChildConcatMatch) {
		this(attribute);
		this.allowDirectChildMatch = allowDirectChildMatch;
		this.allowChildConcatMatch = allowChildConcatMatch;
	}

	@Override
	public String toString() {
		return attribute;
	}

	public String getAttribute() {
		return attribute;
	}

	public List<String> getPermutations() {
		return permutations;
	}

	public Map<Integer, PermutableAttribute> getChildren() {
		return children;
	}

	public boolean isAllowDirectChildMatch() {
		return allowDirectChildMatch;
	}

	public boolean isAllowChildConcatMatch() {
		return allowChildConcatMatch;
	}
}
