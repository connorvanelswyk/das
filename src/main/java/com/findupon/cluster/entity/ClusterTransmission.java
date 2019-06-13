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

package com.findupon.cluster.entity;

import com.findupon.cluster.entity.master.MasterMessage;
import com.findupon.cluster.entity.worker.NodeMessage;
import com.findupon.commons.entity.datasource.*;
import com.findupon.utilities.EncryptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.findupon.commons.utilities.JsonUtils.*;


public class ClusterTransmission {

	private Long nodeId;
	private ClusterMessage message;
	private com.findupon.commons.entity.datasource.DataSource dataSource;
	private List<String> urlsToWork;
	private String details;


	public ClusterTransmission() {
	}

	public ClusterTransmission(Long nodeId) {
		this.nodeId = nodeId;
	}

	public ClusterTransmission(Long nodeId, ClusterMessage message) {
		this.nodeId = nodeId;
		this.message = message;
	}

	public ClusterTransmission(Long nodeId, com.findupon.commons.entity.datasource.DataSource dataSource) {
		this.nodeId = nodeId;
		this.dataSource = dataSource;
	}

	public String toEncryptedJsonString() {
		JSONObject jo = new JSONObject();
		putObject(jo, "node_id", nodeId);
		putObject(jo, "directive", message.getDirective());
		putObject(jo, "urls", new JSONArray(urlsToWork));
		putObject(jo, "details", details);

		if(dataSource != null) {
			JSONObject sourceJo = new JSONObject();

			putObject(sourceJo, "id", dataSource.getId());
			putObject(sourceJo, "url", dataSource.getUrl());

			putObject(sourceJo, "asset_type_id", dataSource.getAssetType().getId());
			putObject(sourceJo, "data_source_type_id", dataSource.getDataSourceType().getId());

			putObject(sourceJo, "proxy_mode", dataSource.getProxyMode().toString());
			putObject(sourceJo, "agent_mode", dataSource.getAgentMode().toString());
			putObject(sourceJo, "crawl_rate", dataSource.getCrawlRate());
			putObject(sourceJo, "status", dataSource.getStatus() == null ? null : dataSource.getStatus().name());
			putObject(sourceJo, "status_reason", dataSource.getStatusReason() == null ? null : dataSource.getStatusReason().name());
			putObject(sourceJo, "datasource_details", dataSource.getDetails());
			putObject(sourceJo, "bot_class", dataSource.getBotClass());
			putObject(sourceJo, "index_only", dataSource.getIndexOnly());
			putObject(sourceJo, "index_del_size", dataSource.getIndexDelegationSize());
			putObject(sourceJo, "created", dataSource.getCreated().getTime());
			putObject(sourceJo, "days_between_runs", dataSource.getDaysBetweenRuns());
			putObject(sourceJo, "failed_attempts", dataSource.getFailedAttempts());

			/* stats */
			putObject(sourceJo, "total_runs", dataSource.getTotalRuns());
			putObject(sourceJo, "last_time", dataSource.getLastTime());
			putObject(sourceJo, "total_time", dataSource.getTotalTime());
			putObject(sourceJo, "last_product_count", dataSource.getLastProductCount());
			putObject(sourceJo, "total_product_count", dataSource.getTotalProductCount());
			putObject(sourceJo, "last_visited_urls", dataSource.getLastVisitedUrls());
			putObject(sourceJo, "total_visited_urls", dataSource.getTotalVisitedUrls());
			putObject(sourceJo, "last_analyzed_urls", dataSource.getLastAnalyzedUrls());
			putObject(sourceJo, "total_analyzed_urls", dataSource.getTotalAnalyzedUrls());
			putObject(sourceJo, "last_download_mb", dataSource.getLastDownloadMb());
			putObject(sourceJo, "total_download_mb", dataSource.getTotalDownloadMb());

			putObject(jo, "data_source", sourceJo);
		}
		return EncryptionUtils.encrypt(jo.toString());
	}

	public static ClusterTransmission fromEncryptedJsonString(String encryptedJson, Class<? extends ClusterMessage> messageClass) {
		String json = EncryptionUtils.decrypt(encryptedJson);
		if(json == null) {
			return null;
		}
		ClusterTransmission transmission;
		try {
			JSONObject jo = new JSONObject(json);

			/* non-null values */
			transmission = new ClusterTransmission(getLong(jo, "node_id"));
			String directive = getString(jo, "directive");
			if(messageClass.equals(MasterMessage.class)) {
				transmission.setMessage(MasterMessage.valueOf(directive));
			} else if(messageClass.equals(NodeMessage.class)) {
				transmission.setMessage(NodeMessage.valueOf(directive));
			} else {
				throw new JSONException("Invalid transmission class! Directive: " + directive);
			}

			/* optional values */
			transmission.setDetails(optString(jo, "details"));
			if(jo.has("urls")) {
				JSONArray jsonArray = jo.getJSONArray("urls");
				List<String> urlList = new ArrayList<>();
				for(int x = 0; x < jsonArray.length(); x++) {
					urlList.add(jsonArray.getString(x));
				}
				transmission.setUrlsToWork(urlList);
			}

			if(jo.has("data_source")) {
				JSONObject sourceJo = jo.getJSONObject("data_source");
				com.findupon.commons.entity.datasource.DataSource dataSource = new com.findupon.commons.entity.datasource.DataSource();

				dataSource.setId(getLong(sourceJo, "id"));
				dataSource.setUrl(getString(sourceJo, "url"));

				dataSource.setAssetType(com.findupon.commons.entity.datasource.AssetType.of(getInteger(sourceJo, "asset_type_id")));
				dataSource.setDataSourceType(com.findupon.commons.entity.datasource.DataSourceType.of(getInteger(sourceJo, "data_source_type_id")));
				Objects.requireNonNull(dataSource.getAssetType(), "Null asset type not allowed. DS ID: " + dataSource.getId());
				Objects.requireNonNull(dataSource.getDataSourceType(), "Null data source type not allowed. DS ID: " + dataSource.getId());

				dataSource.setProxyMode(com.findupon.commons.netops.entity.ProxyMode.valueOf(getString(sourceJo, "proxy_mode")));
				dataSource.setAgentMode(com.findupon.commons.netops.entity.AgentMode.valueOf(getString(sourceJo, "agent_mode")));
				dataSource.setCreated(new Date(getLong(sourceJo, "created")));
				dataSource.setDaysBetweenRuns(getInteger(sourceJo, "days_between_runs"));

				/* null-able data source values */
				dataSource.setFailedAttempts(optInteger(sourceJo, "failed_attempts"));
				dataSource.setCrawlRate(optLong(sourceJo, "crawl_rate"));
				String status = optString(sourceJo, "status");
				dataSource.setStatus(status == null ? null : com.findupon.commons.entity.datasource.DataSourceStatus.valueOf(status));
				String statusReason = optString(sourceJo, "status_reason");
				dataSource.setStatusReason(statusReason == null ? null : com.findupon.commons.entity.datasource.DataSourceStatusReason.valueOf(statusReason));
				dataSource.setDetails(optString(sourceJo, "datasource_details"));
				dataSource.setBotClass(optString(sourceJo, "bot_class"));
				dataSource.setIndexOnly(optBoolean(sourceJo, "index_only"));
				dataSource.setIndexDelegationSize(optInteger(sourceJo, "index_del_size"));

				/* stats */
				dataSource.setTotalRuns(optInteger(sourceJo, "total_runs"));
				dataSource.setLastTime(optLong(sourceJo, "last_time"));
				dataSource.setTotalTime(optLong(sourceJo, "total_time"));
				dataSource.setLastProductCount(optInteger(sourceJo, "last_product_count"));
				dataSource.setTotalProductCount(optLong(sourceJo, "total_product_count"));
				dataSource.setLastVisitedUrls(optLong(sourceJo, "last_visited_urls"));
				dataSource.setTotalVisitedUrls(optLong(sourceJo, "total_visited_urls"));
				dataSource.setLastAnalyzedUrls(optLong(sourceJo, "last_analyzed_urls"));
				dataSource.setTotalAnalyzedUrls(optLong(sourceJo, "total_analyzed_urls"));
				dataSource.setLastDownloadMb(sourceJo.optBigDecimal("last_download_mb", null));
				dataSource.setTotalDownloadMb(sourceJo.optBigDecimal("total_download_mb", null));

				transmission.setDataSource(dataSource);
			}
		} catch(Exception e) {
			LoggerFactory.getLogger(ClusterTransmission.class).error("Fatal error: [{}] Raw json:\n{}", e.getMessage(), json);
			return null;
		}
		return transmission;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		ClusterTransmission that = (ClusterTransmission)o;
		return (nodeId != null ? nodeId.equals(that.nodeId) : that.nodeId == null)
				&& message.getType().equals(that.message.getType())
				&& (dataSource != null ? dataSource.equals(that.dataSource) : that.dataSource == null);
	}

	@Override
	public int hashCode() {
		int result = nodeId != null ? nodeId.hashCode() : 0;
		result = 31 * result + message.getType().hashCode();
		result = 31 * result + (dataSource != null ? dataSource.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "ClusterTransmission{" +
				"nodeId=" + nodeId +
				", message=" + message.getDirective() +
				", dataSource=" + dataSource +
				", details='" + details + '\'' +
				'}';
	}

	public Long getNodeId() {
		return nodeId;
	}

	public void setNodeId(Long nodeId) {
		this.nodeId = nodeId;
	}

	public ClusterMessage getMessage() {
		return message;
	}

	public void setMessage(ClusterMessage clusterMessage) {
		this.message = clusterMessage;
	}

	public com.findupon.commons.entity.datasource.DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(com.findupon.commons.entity.datasource.DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public List<String> getUrlsToWork() {
		return urlsToWork;
	}

	public void setUrlsToWork(List<String> urlsToWork) {
		this.urlsToWork = urlsToWork;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}
}
