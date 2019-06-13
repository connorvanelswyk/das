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

package com.findupon.commons.entity.frontier;

import com.findupon.commons.entity.datasource.AssetType;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;


@Entity
@Table(name = "visited_site")
public class VisitedSite implements Serializable {
	private static final long serialVersionUID = -5117738490570705266L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "url")
	private String url;

	@Column(name = "robots_allow")
	private Boolean robotsAllow;

	@Column(name = "asset_type_guess")
	@Enumerated(EnumType.STRING)
	private AssetType assetTypeGuess;

	@Column(name = "visited")
	private Date visited;

	@Column(name = "title")
	private String title;

	@Column(name = "author")
	private String author;

	@Column(name = "keywords")
	private String keywords;

	@Column(name = "description")
	private String description;


	public VisitedSite(String url) {
		this.url = url;
		this.visited = new Date();
	}

	@Override
	public String toString() {
		return "VisitedSite{" +
				"id=" + id +
				", url='" + url + '\'' +
				", robotsAllow=" + robotsAllow +
				", assetTypeGuess=" + assetTypeGuess +
				", visited=" + visited +
				", title='" + title + '\'' +
				'}';
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Boolean getRobotsAllow() {
		return robotsAllow;
	}

	public void setRobotsAllow(Boolean robotsAllow) {
		this.robotsAllow = robotsAllow;
	}

	public AssetType getAssetTypeGuess() {
		return assetTypeGuess;
	}

	public void setAssetTypeGuess(AssetType assetTypeGuess) {
		this.assetTypeGuess = assetTypeGuess;
	}

	public Date getVisited() {
		return visited;
	}

	public void setVisited(Date visited) {
		this.visited = visited;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
