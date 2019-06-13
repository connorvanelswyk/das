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

package com.findupon.commons.entity.datasource;

import com.findupon.commons.netops.entity.AgentMode;
import com.findupon.commons.netops.entity.ProxyMode;

import javax.annotation.Nonnull;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Objects;


@Entity
@Table(name = "data_source")
public class DataSource implements Serializable, Comparable<DataSource> {
	private static final long serialVersionUID = -5501386421381389871L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@Column(name = "url")
	private String url;

	@Column(name = "asset_type_id")
	@Convert(converter = AssetType.ConverterImpl.class)
	private AssetType assetType;

	@Column(name = "data_source_type_id")
	@Convert(converter = DataSourceType.ConverterImpl.class)
	private DataSourceType dataSourceType;

	@OneToOne(mappedBy = "dataSource", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private PageMeta pageMeta;

	// @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	// private List<Product> products;

	@Column(name = "staged")
	private Boolean staged;

	@Column(name = "running")
	private Boolean running;

	@Column(name = "temp_disabled")
	private Boolean temporaryDisable;

	@Column(name = "perm_disabled")
	private Boolean permanentDisable;

	@Column(name = "proxy_mode")
	@Convert(converter = ProxyMode.ConverterImpl.class)
	private ProxyMode proxyMode;

	@Column(name = "agent_mode")
	@Convert(converter = AgentMode.ConverterImpl.class)
	private AgentMode agentMode;

	// millisecond delay between requests
	@Column(name = "crawl_rate")
	private Long crawlRate;

	// add if the bot uses a specific class to run, null if generic
	@Column(name = "bot_class")
	private String botClass;

	// used for listing runs, signals the runner that this bot gets all the data it needs from the serp so the build process never needs to run
	// this style handles removal based on the cars that were not visited from the last index
	@Column(name = "index_only")
	private Boolean indexOnly;

	// used for index only listing runs, controls how many URLs a node is allocated per order
	@Column(name = "index_del_size")
	private Integer indexDelegationSize;

	@Column(name = "max_queued_orders")
	private Integer maxQueuedOrders;

	@Column(name = "days_between_runs")
	private Integer daysBetweenRuns;

	@Column(name = "last_run")
	private Date lastRun;

	@Column(name = "status")
	@Enumerated(EnumType.STRING)
	private DataSourceStatus status;

	@Column(name = "status_reason")
	@Enumerated(EnumType.STRING)
	private DataSourceStatusReason statusReason;

	@Column(name = "details")
	private String details;

	@Column(name = "failed_attempts")
	private Integer failedAttempts;

	@Column(name = "total_runs")
	private Integer totalRuns;

	@Column(name = "last_time")
	private Long lastTime; // seconds

	@Column(name = "total_time")
	private Long totalTime; // seconds

	@Column(name = "last_product_count")
	private Integer lastProductCount;

	@Column(name = "total_product_count")
	private Long totalProductCount;

	@Column(name = "last_visited_urls")
	private Long lastVisitedUrls;

	@Column(name = "total_visited_urls")
	private Long totalVisitedUrls;

	@Column(name = "last_analyzed_urls")
	private Long lastAnalyzedUrls;

	@Column(name = "total_analyzed_urls")
	private Long totalAnalyzedUrls;

	@Column(name = "last_download_mb", precision = 32, scale = 2)
	private BigDecimal lastDownloadMb;

	@Column(name = "total_download_mb", precision = 64, scale = 2)
	private BigDecimal totalDownloadMb;

	@Column(name = "created")
	private Date created;


	public static DataSource createNew(String url, AssetType assetType, DataSourceType dataSourceType) {
		Objects.requireNonNull(url, "URL is required to create a new data source");
		Objects.requireNonNull(assetType, "Asset type is required to create a new data source");
		Objects.requireNonNull(dataSourceType, "Data source type is required to create a new data source");

		DataSource dataSource = new DataSource();
		dataSource.setCreated(new Date());
		dataSource.setUrl(url);
		dataSource.setAssetType(assetType);
		dataSource.setTotalRuns(0);
		dataSource.setStaged(false);
		dataSource.setRunning(false);
		dataSource.setIndexOnly(false);

		dataSource.setDataSourceType(dataSourceType);
		dataSource.setTemporaryDisable(false);
		dataSource.setPermanentDisable(false);
		dataSource.setDaysBetweenRuns(2);
		dataSource.setCrawlRate(0L);
		dataSource.setProxyMode(ProxyMode.PUBLIC);
		dataSource.setAgentMode(AgentMode.PUBLIC);

		if(DataSourceType.LISTING.equals(dataSourceType)) {
			dataSource.setTemporaryDisable(true); // turned on manually for prod
			dataSource.setPermanentDisable(true); // turned on manually for prod
			dataSource.setDaysBetweenRuns(3);
			dataSource.setMaxQueuedOrders(12);
			dataSource.setCrawlRate(4000L);
			dataSource.setProxyMode(ProxyMode.ROTATE_LOCATION);
			dataSource.setAgentMode(AgentMode.ROTATE);
		}
		return dataSource;
	}

	public boolean isGeneric() {
		return DataSourceType.GENERIC.equals(Objects.requireNonNull(dataSourceType));
	}

	@Override
	public int compareTo(@Nonnull DataSource o) {
		if(this.getLastRun() == null && o.getLastRun() == null) {
			return 0;
		}
		if(this.getLastRun() == null) {
			return -1;
		}
		if(o.getLastRun() == null) {
			return 1;
		}
		return this.getLastRun().compareTo(o.getLastRun());
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		DataSource that = (DataSource)o;
		return Objects.equals(id, that.id) && assetType == that.assetType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, assetType);
	}

	@Override
	public String toString() {
		return "DataSource{" +
				"id=" + id +
				", url='" + url + '\'' +
				", assetType=" + assetType +
				", running=" + running +
				", temporaryDisable=" + temporaryDisable +
				", permanentDisable=" + permanentDisable +
				", crawlRate=" + crawlRate +
				", lastRun=" + lastRun +
				", status=" + status +
				", statusReason=" + statusReason +
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

	public AssetType getAssetType() {
		return assetType;
	}

	public void setAssetType(AssetType assetType) {
		this.assetType = assetType;
	}

	public DataSourceType getDataSourceType() {
		return dataSourceType;
	}

	public void setDataSourceType(DataSourceType dataSourceType) {
		this.dataSourceType = dataSourceType;
	}

	public PageMeta getPageMeta() {
		return pageMeta;
	}

	public void setPageMeta(PageMeta pageMeta) {
		this.pageMeta = pageMeta;
	}

	public Boolean getStaged() {
		return staged;
	}

	public void setStaged(Boolean staged) {
		this.staged = staged;
	}

	public Boolean getRunning() {
		return running;
	}

	public void setRunning(Boolean running) {
		this.running = running;
	}

	public Boolean getTemporaryDisable() {
		return temporaryDisable;
	}

	public void setTemporaryDisable(Boolean temporaryDisable) {
		this.temporaryDisable = temporaryDisable;
	}

	public Boolean getPermanentDisable() {
		return permanentDisable;
	}

	public void setPermanentDisable(Boolean permanentDisable) {
		this.permanentDisable = permanentDisable;
	}

	public ProxyMode getProxyMode() {
		return proxyMode;
	}

	public void setProxyMode(ProxyMode proxyMode) {
		this.proxyMode = proxyMode;
	}

	public AgentMode getAgentMode() {
		return agentMode;
	}

	public void setAgentMode(AgentMode agentMode) {
		this.agentMode = agentMode;
	}

	public Long getCrawlRate() {
		return crawlRate;
	}

	public void setCrawlRate(Long crawlRate) {
		this.crawlRate = crawlRate;
	}

	public String getBotClass() {
		return botClass;
	}

	public void setBotClass(String botClass) {
		this.botClass = botClass;
	}

	public Boolean getIndexOnly() {
		return indexOnly;
	}

	public void setIndexOnly(Boolean indexOnly) {
		this.indexOnly = indexOnly;
	}

	public Integer getIndexDelegationSize() {
		return indexDelegationSize;
	}

	public void setIndexDelegationSize(Integer indexDelegationSize) {
		this.indexDelegationSize = indexDelegationSize;
	}

	public Integer getMaxQueuedOrders() {
		return maxQueuedOrders;
	}

	public void setMaxQueuedOrders(Integer maxQueuedOrders) {
		this.maxQueuedOrders = maxQueuedOrders;
	}

	public Integer getDaysBetweenRuns() {
		return daysBetweenRuns;
	}

	public void setDaysBetweenRuns(Integer daysBetweenRuns) {
		this.daysBetweenRuns = daysBetweenRuns;
	}

	public Date getLastRun() {
		return lastRun;
	}

	public void setLastRun(Date lastRun) {
		this.lastRun = lastRun;
	}

	public DataSourceStatus getStatus() {
		return status;
	}

	public void setStatus(DataSourceStatus status) {
		this.status = status;
	}

	public DataSourceStatusReason getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(DataSourceStatusReason statusReason) {
		this.statusReason = statusReason;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public Integer getFailedAttempts() {
		return failedAttempts;
	}

	public void setFailedAttempts(Integer failedAttempts) {
		this.failedAttempts = failedAttempts;
	}

	public Integer getTotalRuns() {
		return totalRuns;
	}

	public void setTotalRuns(Integer totalRuns) {
		this.totalRuns = totalRuns;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
	}

	public Long getTotalTime() {
		return totalTime;
	}

	public void setTotalTime(Long totalTime) {
		this.totalTime = totalTime;
	}

	public Integer getLastProductCount() {
		return lastProductCount;
	}

	public void setLastProductCount(Integer lastProductCount) {
		this.lastProductCount = lastProductCount;
	}

	public Long getTotalProductCount() {
		return totalProductCount;
	}

	public void setTotalProductCount(Long totalProductCount) {
		this.totalProductCount = totalProductCount;
	}

	public Long getLastVisitedUrls() {
		return lastVisitedUrls;
	}

	public void setLastVisitedUrls(Long lastVisitedUrls) {
		this.lastVisitedUrls = lastVisitedUrls;
	}

	public Long getTotalVisitedUrls() {
		return totalVisitedUrls;
	}

	public void setTotalVisitedUrls(Long totalVisitedUrls) {
		this.totalVisitedUrls = totalVisitedUrls;
	}

	public Long getLastAnalyzedUrls() {
		return lastAnalyzedUrls;
	}

	public void setLastAnalyzedUrls(Long lastAnalyzedUrls) {
		this.lastAnalyzedUrls = lastAnalyzedUrls;
	}

	public Long getTotalAnalyzedUrls() {
		return totalAnalyzedUrls;
	}

	public void setTotalAnalyzedUrls(Long totalAnalyzedUrls) {
		this.totalAnalyzedUrls = totalAnalyzedUrls;
	}

	public BigDecimal getLastDownloadMb() {
		return lastDownloadMb;
	}

	public void setLastDownloadMb(BigDecimal lastDownloadMb) {
		this.lastDownloadMb = lastDownloadMb != null ? lastDownloadMb.setScale(2, RoundingMode.HALF_EVEN) : null;
	}

	public BigDecimal getTotalDownloadMb() {
		return totalDownloadMb;
	}

	public void setTotalDownloadMb(BigDecimal totalDownloadMb) {
		this.totalDownloadMb = totalDownloadMb != null ? totalDownloadMb.setScale(2, RoundingMode.HALF_EVEN) : null;
	}

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}
}
