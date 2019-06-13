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

package com.findupon.commons.entity.product.aircraft;

import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.ProductCondition;
import com.findupon.commons.entity.product.attribute.ProductTerm;

import javax.persistence.*;
import java.io.Serializable;


// @Data ...
@Entity
@Table(name = "aircraft")
public class Aircraft extends Product implements Serializable {
	private static final long serialVersionUID = 7841497969607096164L;


	@Column(name = "address")
	private String address;

	@Column(name = "city")
	private String city;

	@Column(name = "zip")
	private String zip;

	@Column(name = "country_code")
	private String countryCode;


	/*
		Contact
	 */
	@Column(name = "contact_url")
	private String contactUrl;

	@Column(name = "contact_name")
	private String contactName;

	@Column(name = "contact_phone")
	private String contactPhone;

	@Column(name = "contact_fax")
	private String contactFax;

	@Column(name = "contact_person")
	private String contactPerson;


	/*
		Description
	*/
	@Column(name = "description")
	private String description;

	@Column(name = "airframe")
	private String airframe;

	@Column(name = "modification_details")
	private String modificationDetails;

	@Column(name = "avionic_details")
	private String avionicDetails;

	@Column(name = "engine_details")
	private String engineDetails;

	@Column(name = "interior_details")
	private String interiorDetails;

	@Column(name = "exterior_details")
	private String exteriorDetails;

	@Column(name = "additional_equipment_details")
	private String additionalEquipmentDetails;

	@Column(name = "inspection_status")
	private String inspectionStatus;

	@Column(name = "year_painted")
	private Integer yearPainted;


	/*
		Specifications
	*/
	@AggregationColumn
	@Column(name = "reg_number")
	private String regNumber;

	@Column(name = "srl_number")
	private String srlNumber;

	@Column(name = "year")
	private Integer year;

	@Column(name = "category_id")
	private Integer categoryId;

	@Column(name = "make_id")
	private Integer makeId;

	@Column(name = "model_id")
	private Integer modelId;

	@Column(name = "trim_id")
	private Integer trimId;

	@Column(name = "total_time")
	private Integer totalTime;

	@Column(name = "number_of_seats")
	private Integer numberOfSeats;


	/*
		Condition & Term
	 */
	@Column(name = "product_condition_id")
	@Convert(converter = ProductCondition.ConverterImpl.class)
	private ProductCondition productCondition;

	@Column(name = "product_term_id")
	@Convert(converter = ProductTerm.ConverterImpl.class)
	private ProductTerm productTerm;


	/*
		Transient
	 */
	@Transient
	private String category;

	@Transient
	private String make;

	@Transient
	private String model;


	public Aircraft() {
	}

	public Aircraft(String url) {
		super(url);
	}

	@Override
	public String getAggregationColumn() {
		return regNumber;
	}

	@Override
	public String toString() {
		return super.toString() +
				"\nAircraft{" +
				", address='" + address + '\'' +
				", city='" + city + '\'' +
				", zip='" + zip + '\'' +
				", countryCode='" + countryCode + '\'' +
				", contactUrl='" + contactUrl + '\'' +
				", contactName='" + contactName + '\'' +
				", contactPhone='" + contactPhone + '\'' +
				", contactFax='" + contactFax + '\'' +
				", contactPerson='" + contactPerson + '\'' +
				", description='" + description + '\'' +
				", airframe='" + airframe + '\'' +
				", modificationDetails='" + modificationDetails + '\'' +
				", avionicDetails='" + avionicDetails + '\'' +
				", engineDetails='" + engineDetails + '\'' +
				", interiorDetails='" + interiorDetails + '\'' +
				", exteriorDetails='" + exteriorDetails + '\'' +
				", additionalEquipmentDetails='" + additionalEquipmentDetails + '\'' +
				", inspectionStatus='" + inspectionStatus + '\'' +
				", yearPainted=" + yearPainted +
				", regNumber='" + regNumber + '\'' +
				", srlNumber='" + srlNumber + '\'' +
				", year=" + year +
				", categoryId=" + categoryId +
				", makeId=" + makeId +
				", modelId=" + modelId +
				", trimId=" + trimId +
				", totalTime=" + totalTime +
				", numberOfSeats=" + numberOfSeats +
				", productCondition=" + productCondition +
				", productTerm=" + productTerm +
				", category='" + category + '\'' +
				", make='" + make + '\'' +
				", model='" + model + '\'' +
				'}';
	}


	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public ProductCondition getProductCondition() {
		return productCondition;
	}

	public void setProductCondition(ProductCondition productCondition) {
		this.productCondition = productCondition;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getMake() {
		return make;
	}

	public void setMake(String make) {
		this.make = make;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getRegNumber() {
		return regNumber;
	}

	public void setRegNumber(String regNumber) {
		this.regNumber = regNumber;
	}

	public String getSrlNumber() {
		return srlNumber;
	}

	public void setSrlNumber(String srlNumber) {
		this.srlNumber = srlNumber;
	}

	public Integer getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(Integer categoryId) {
		this.categoryId = categoryId;
	}

	public Integer getMakeId() {
		return makeId;
	}

	public void setMakeId(Integer makeId) {
		this.makeId = makeId;
	}

	public Integer getModelId() {
		return modelId;
	}

	public void setModelId(Integer modelId) {
		this.modelId = modelId;
	}

	public Integer getTrimId() {
		return trimId;
	}

	public void setTrimId(Integer trimId) {
		this.trimId = trimId;
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

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getContactUrl() {
		return contactUrl;
	}

	public void setContactUrl(String contactUrl) {
		this.contactUrl = contactUrl;
	}

	public String getContactName() {
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public void setContactPhone(String contactPhone) {
		this.contactPhone = contactPhone;
	}

	public String getContactFax() {
		return contactFax;
	}

	public void setContactFax(String contactFax) {
		this.contactFax = contactFax;
	}

	public String getContactPerson() {
		return contactPerson;
	}

	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAirframe() {
		return airframe;
	}

	public void setAirframe(String airframe) {
		this.airframe = airframe;
	}

	public String getModificationDetails() {
		return modificationDetails;
	}

	public void setModificationDetails(String modificationDetails) {
		this.modificationDetails = modificationDetails;
	}

	public String getAvionicDetails() {
		return avionicDetails;
	}

	public void setAvionicDetails(String avionicDetails) {
		this.avionicDetails = avionicDetails;
	}

	public String getEngineDetails() {
		return engineDetails;
	}

	public void setEngineDetails(String engineDetails) {
		this.engineDetails = engineDetails;
	}

	public String getInteriorDetails() {
		return interiorDetails;
	}

	public void setInteriorDetails(String interiorDetails) {
		this.interiorDetails = interiorDetails;
	}

	public String getExteriorDetails() {
		return exteriorDetails;
	}

	public void setExteriorDetails(String exteriorDetails) {
		this.exteriorDetails = exteriorDetails;
	}

	public String getAdditionalEquipmentDetails() {
		return additionalEquipmentDetails;
	}

	public void setAdditionalEquipmentDetails(String additionalEquipmentDetails) {
		this.additionalEquipmentDetails = additionalEquipmentDetails;
	}

	public String getInspectionStatus() {
		return inspectionStatus;
	}

	public void setInspectionStatus(String inspectionStatus) {
		this.inspectionStatus = inspectionStatus;
	}

	public Integer getYearPainted() {
		return yearPainted;
	}

	public void setYearPainted(Integer yearPainted) {
		this.yearPainted = yearPainted;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public Integer getTotalTime() {
		return totalTime;
	}

	public void setTotalTime(Integer totalTime) {
		this.totalTime = totalTime;
	}

	public Integer getNumberOfSeats() {
		return numberOfSeats;
	}

	public void setNumberOfSeats(Integer numberOfSeats) {
		this.numberOfSeats = numberOfSeats;
	}
}
