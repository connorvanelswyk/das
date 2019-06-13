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

import javax.persistence.*;
import java.io.Serializable;


@Entity
@Table(name = "price_meta")
public class PriceMeta implements Serializable {

	private static final long serialVersionUID = -7039158196609971969L;


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "tag_name")
	private String tagName;

	@Column(name = "element_id")
	private String elementId;

	@Column(name = "element_text")
	private String elementText;

	@Column(name = "parent_tags")
	private String parentTags;

	@Column(name = "outer_html")
	private String outerHtml;

	@Column(name = "url")
	private String url;


	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public String getElementId() {
		return elementId;
	}

	public void setElementId(String elementId) {
		this.elementId = elementId;
	}

	public String getElementText() {
		return elementText;
	}

	public void setElementText(String elementText) {
		this.elementText = elementText;
	}

	public String getParentTags() {
		return parentTags;
	}

	public void setParentTags(String parentTags) {
		this.parentTags = parentTags;
	}

	public String getOuterHtml() {
		return outerHtml;
	}

	public void setOuterHtml(String outerHtml) {
		this.outerHtml = outerHtml;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
