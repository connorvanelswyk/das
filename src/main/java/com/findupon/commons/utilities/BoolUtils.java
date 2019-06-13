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

import org.apache.commons.lang3.BooleanUtils;
import org.jsoup.nodes.Element;

import java.util.Collection;


/**
 * {@link BooleanUtils} on steroids.
 * <p>
 * Yes I'm extending it on purpose.  This way, we get that ƒality as well.
 * Yes, ƒ, the Dutch florin, for function, function-ality.  You're welcome.
 * <strong>Note</strong> I'd like to export this file to Maven.
 */
public class BoolUtils extends BooleanUtils {

	/**
	 * <p>Checks if a {@code Boolean} value is {@code true},
	 * handling {@code null} by returning {@code false}.</p>
	 * <p>
	 * <pre>
	 *   BoolUtils.isAlive(Boolean.FALSE) = Boolean.FALSE;
	 *   BoolUtils.isAlive(Boolean.TRUE)  = Boolean.TRUE;
	 *   BoolUtils.isAlive(null)          = Boolean.FALSE;
	 *   BoolUtils.isAlive(Collection)    = isEmpty ? Boolean.FALSE : Boolean.TRUE;
	 * </pre>
	 *
	 * @param objects to iterate, may be null
	 * @return {@code boolean} value, {@code false} if {@code null} input
	 */
	public static boolean isAlive(Object... objects) {

		if(objects == null) {
			return false;
		}

		// Loop returns false for any object condition considered not alive ... or dead.
		for(Object o : objects) {

			if(o == null ||
					(o instanceof Collection<?> && ((Collection<?>)o).isEmpty()) ||
					(o instanceof Element && ((Element)o).children().isEmpty()) ||
					(o instanceof String && ((String)o).isEmpty())) {

				return false;
			}
		}
		return true;
	}

	/**
	 * Opposite of {@see BoolUtils.isAlive } ... yeah dingus.
	 *
	 * @param object
	 * @return
	 */
	public static boolean isDead(Object... object) {
		return !isAlive(object);
	}
}
