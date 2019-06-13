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

package com.findupon.commons.bot;

import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.entity.datasource.PageMeta;
import com.findupon.commons.netops.ConnectionAgent;
import com.findupon.commons.repository.datasource.DataSourceRepo;
import com.findupon.commons.repository.datasource.PageMetaRepo;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.findupon.commons.utilities.ConsoleColors.red;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PageMetaBot {
	private static final Logger logger = LoggerFactory.getLogger(PageMetaBot.class);

	@Autowired private PageMetaRepo pageMetaRepo;
	@Autowired private DataSourceRepo dataSourceRepo;


	public void deployOne(Document document, DataSource dataSource) {
		if(document == null || dataSource == null) {
			logger.warn("[PageMetaBot] - Datasource or document cannot be null");
			return;
		}
		try {
			if(dataSourceRepo.searchByUrl(dataSource.getUrl()) == null) {
				logger.warn("[PageMetaBot] - Data source not found in the database by URL. " +
						"Page meta will not be persisted. URL [{}]", dataSource.getUrl());
				return;
			}
			PageMeta pageMeta = buildPageMeta(document);
			pageMeta.setDataSource(dataSource);
			PageMeta existingPageMeta = pageMetaRepo.findByDataSourceId(dataSource.getId());
			if(existingPageMeta != null) {
				pageMeta.setId(existingPageMeta.getId());
			}
			pageMetaRepo.save(pageMeta);
		} catch(Exception e) {
			logger.error("[PageMetaBot] - Error persisting page meta for datasource [{}]", dataSource.getUrl(), e);
		}
	}

	public void deployOne(String dataSourceUrl) {
		List<DataSource> dataSources = dataSourceRepo.searchByUrl(dataSourceUrl);
		if(!dataSources.isEmpty()) {
			if(dataSources.size() > 1) {
				logger.warn(red("[PageMetaBot] - More than one data source found for URL [{}], deploying first result"), dataSourceUrl);
			}
			deployOne(dataSources.get(0));
		} else {
			logger.warn("[PageMetaBot] - No datasource found for URL [{}]", dataSourceUrl);
		}
	}

	public void deployOne(DataSource dataSource) {
		Document document = ConnectionAgent.INSTANCE.download(dataSource.getUrl(), dataSource).getDocument();
		if(document != null) {
			deployOne(document, dataSource);
		} else {
			logger.warn("[PageMetaBot] - Document came back null from deploy for datasource [{}]", dataSource.getUrl());
		}
	}

	public void deployAll() {
		dataSourceRepo.findAll().forEach(this::deployOne);
	}

	public static PageMeta buildPageMeta(Document document) {
		PageMeta pageMeta = new PageMeta();
		Document strippedDocument = document.clone();
		strippedDocument.getElementsByTag("script").remove();
		strippedDocument.getElementsByTag("link").remove();
		strippedDocument.getElementsByTag("style").remove();

		pageMeta.setTitle(strippedDocument.title());
		pageMeta.setAuthor(getMetaTagContent(strippedDocument, "author"));
		pageMeta.setKeywords(getMetaTagContent(strippedDocument, "keywords"));
		pageMeta.setDescription(getMetaTagContent(strippedDocument, "description"));
		pageMeta.setHeadTag(strippedDocument.getElementsByTag("head").outerHtml());
		return pageMeta;
	}

	private static String getMetaTagContent(Document document, String name) {
		List<Element> metaTags = document.select("meta[name=" + name + "]");
		if(metaTags.isEmpty() || !metaTags.get(0).hasAttr("content")) {
			return null;
		}
		return StringUtils.trimToNull(metaTags.get(0).attr("content"));
	}
}
