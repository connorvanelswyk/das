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

package com.findupon.commons.entity.product.realestate;

import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.attribute.ProductTerm;
import com.findupon.commons.entity.product.attribute.RealEstateType;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;


@Entity
@Table(name = "real_estate")
public class RealEstate extends Product implements Serializable {
	private static final long serialVersionUID = -4976881244306156131L;

	// TODO: will be removed with join to data_source
	@Column(name = "realtor_url")
	private String realtorUrl;

	@Column(name = "status_id")
	@Convert(converter = ProductTerm.ConverterImpl.class)
	private ProductTerm productTerm;

	// TODO: update to standardized hash, shared from the location entity under BUSER-228
	@AggregationColumn
	@Column(name = "address")
	private String address;

	@Column(name = "city")
	private String city;

	@Column(name = "state_abbr")
	@Convert(converter = State.ConverterImpl.class)
	private State state;

	@Column(name = "zip")
	private String zip;

	@Column(name = "mls_number")
	private String mlsNumber;

	@Column(name = "type_id")
	@Convert(converter = RealEstateType.ConverterImpl.class)
	private RealEstateType realEstateType;

	@Column(name = "hoa_fee_monthy")
	private BigDecimal hoaFeeMonthly;

	@Column(name = "sqft")
	private Integer squareFeet;

	@Column(name = "lot_sqft")
	private Integer lotSquareFeet;

	@Column(name = "beds", precision = 3, scale = 1)
	private Double beds;

	@Column(name = "baths", precision = 3, scale = 1)
	private Double baths;

	@Column(name = "year_built")
	private Integer yearBuilt;

	@Column(name = "agent_name")
	private String agentName;

	@Column(name = "agent_phone")
	private String agentPhone;

	public RealEstate() {
		super();
	}

	public RealEstate(String url) {
		super(url);
	}

	@Override
	public String getAggregationColumn() {
		return address;
	}

	public String getRealtorUrl() {
		return realtorUrl;
	}

	public void setRealtorUrl(String realtorUrl) {
		this.realtorUrl = realtorUrl;
	}

	public ProductTerm getProductTerm() {
		return productTerm;
	}

	public void setProductTerm(ProductTerm productTerm) {
		this.productTerm = productTerm;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getMlsNumber() {
		return mlsNumber;
	}

	public void setMlsNumber(String mlsNumber) {
		this.mlsNumber = mlsNumber;
	}

	public RealEstateType getRealEstateType() {
		return realEstateType;
	}

	public void setRealEstateType(RealEstateType realEstateType) {
		this.realEstateType = realEstateType;
	}

	public BigDecimal getHoaFeeMonthly() {
		return hoaFeeMonthly;
	}

	public void setHoaFeeMonthly(BigDecimal hoaFeeMonthly) {
		this.hoaFeeMonthly = hoaFeeMonthly;
	}

	public Integer getSquareFeet() {
		return squareFeet;
	}

	public void setSquareFeet(Integer squareFeet) {
		this.squareFeet = squareFeet;
	}

	public Integer getLotSquareFeet() {
		return lotSquareFeet;
	}

	public void setLotSquareFeet(Integer lotSquareFeet) {
		this.lotSquareFeet = lotSquareFeet;
	}

	public Double getBeds() {
		return beds;
	}

	public void setBeds(Double beds) {
		this.beds = beds;
	}

	public Double getBaths() {
		return baths;
	}

	public void setBaths(Double baths) {
		this.baths = baths;
	}

	public Integer getYearBuilt() {
		return yearBuilt;
	}

	public void setYearBuilt(Integer yearBuilt) {
		this.yearBuilt = yearBuilt;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public String getAgentPhone() {
		return agentPhone;
	}

	public void setAgentPhone(String agentPhone) {
		this.agentPhone = agentPhone;
	}
}
