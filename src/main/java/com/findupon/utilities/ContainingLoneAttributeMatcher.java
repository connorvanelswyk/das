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

import com.findupon.commons.building.AttributeOperations;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;

import java.util.Map;

public class ContainingLoneAttributeMatcher extends Evaluator {

    private Map<Integer, AttributeMatch> matches;

    public ContainingLoneAttributeMatcher(Map<Integer, AttributeMatch> matches) {
        this.matches = matches;
    }

    @Override
    public boolean matches(Element root, Element element) {
        if (element.hasText()) {
            String text = element.ownText();
            int len = text.length();
            if (len > 0 && len <= 200) {
                for (Map.Entry<Integer, AttributeMatch> entry : matches.entrySet()) {
                    AttributeMatch match = entry.getValue();
                    for (String attribute : match.getAllowedMatches()) {
                        if (AttributeOperations.containsLoneAttribute(text, attribute)) {
                            match.getMatchingElements().add(element);
                        }
                    }
                }
            }
        }
        return false;
    }

}