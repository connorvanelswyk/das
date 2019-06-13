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

package com.findupon.commons.entity.product;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.findupon.commons.entity.AbstractEntity;

import javax.annotation.Nonnull;
import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Objects;


@MappedSuperclass
@JsonFilter("analyticsReport")
public abstract class Product extends AbstractEntity<Long> implements Comparable<Product> {

	// @ManyToOne
	// @JoinColumn(name = "data_source_id")
	// private DataSource dataSource;

	@Column(name = "data_source_id")
	private Long dataSourceId;

	@Column(name = "creation_date")
	private Date creationDate;

	@Column(name = "created_by")
	private String createdBy;

	@Column(name = "modified_date")
	private Date modifiedDate;

	@Column(name = "modified_by")
	private String modifiedBy;

	@Column(name = "visited_date")
	private Date visitedDate;

	@Column(name = "visited_by")
	private String visitedBy;

	@Column(name = "url")
	private String url;

	@Column(name = "main_img_url")
	private String mainImageUrl;

	// TODO: will be removed with join to data_source (see CHLOE-64)
	@Column(name = "source_url")
	private String sourceUrl;

	@Column(name = "listing_id")
	private String listingId;

	@Column(name = "price")
	private BigDecimal price;

	// TODO: location entity (see BUSER-228)
	@Column(name = "latitude", precision = 12, scale = 8)
	private Float latitude;

	@Column(name = "longitude", precision = 12, scale = 8)
	private Float longitude;

	@Transient
	private int dealRating = 0;


	public Product() {
	}

	public Product(String url) {
		this.url = url;
	}

	public abstract String getAggregationColumn();

	@Override
	public int compareTo(@Nonnull Product that) {
		if(getPrice() == null) {
			return -1;
		}
		if(that.getPrice() == null) {
			return 1;
		}
		if(getPrice().equals(that.getPrice())) {
			return 0;
		}
		return getPrice().compareTo(that.getPrice());
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		Product that = (Product)o;
		if(getId() != null && that.getId() != null) {
			return Objects.equals(getId(), that.getId());
		}
		System.err.println("******** Warning! ********\nProduct's equals with null IDs is inherently unsafe. " +
				"Usages should be performed on managed entities. Proceed with caution!");
		return Objects.equals(url, that.url);
	}

	@Override
	public int hashCode() {
		if(getId() != null) {
			return getId().hashCode();
		}
		System.err.println("******** Warning! ********\nProduct's hashCode with null IDs is inherently unsafe. " +
				"Usages should be performed on managed entities. Proceed with caution!");
		return url.hashCode();
	}

	@Override
	public String toString() {
		return "Product{" +
				"creationDate=" + creationDate +
				", createdBy='" + createdBy + '\'' +
				", modifiedDate=" + modifiedDate +
				", modifiedBy='" + modifiedBy + '\'' +
				", visitedDate=" + visitedDate +
				", visitedBy='" + visitedBy + '\'' +
				", url='" + url + '\'' +
				", mainImageUrl='" + mainImageUrl + '\'' +
				", sourceUrl='" + sourceUrl + '\'' +
				", listingId='" + listingId + '\'' +
				", price=" + price +
				", dealRating=" + dealRating +
				'}';
	}

	// public DataSource getDataSource() {
	// 	return dataSource;
	// }
	//
	// public void setDataSource(DataSource dataSource) {
	// 	this.dataSource = dataSource;
	// }

	public Long getDataSourceId() {
		return dataSourceId;
	}

	public void setDataSourceId(Long dataSourceId) {
		this.dataSourceId = dataSourceId;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Date getModifiedDate() {
		return modifiedDate;
	}

	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}

	public String getModifiedBy() {
		return modifiedBy;
	}

	public void setModifiedBy(String modifiedBy) {
		this.modifiedBy = modifiedBy;
	}

	public Date getVisitedDate() {
		return visitedDate;
	}

	public void setVisitedDate(Date visitedDate) {
		this.visitedDate = visitedDate;
	}

	public String getVisitedBy() {
		return visitedBy;
	}

	public void setVisitedBy(String visitedBy) {
		this.visitedBy = visitedBy;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getMainImageUrl() {
		return mainImageUrl;
	}

	public void setMainImageUrl(String mainImageUrl) {
		this.mainImageUrl = mainImageUrl;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getListingId() {
		return listingId;
	}

	public void setListingId(String listingId) {
		this.listingId = listingId;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price != null ? price.setScale(2, RoundingMode.HALF_EVEN) : null;
	}

	public int getDealRating() {
		return dealRating;
	}

	public void setDealRating(int dealRating) {
		this.dealRating = dealRating;
	}

	public Float getLatitude() {
		return latitude;
	}

	public void setLatitude(Float latitude) {
		this.latitude = latitude;
	}

	public Float getLongitude() {
		return longitude;
	}

	public void setLongitude(Float longitude) {
		this.longitude = longitude;
	}
}
