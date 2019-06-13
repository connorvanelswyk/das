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

package com.findupon.commons.entity.product.watercraft;

import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.attribute.Fuel;
import com.findupon.commons.entity.product.attribute.HullType;
import com.findupon.commons.entity.product.attribute.WatercraftType;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;


@Entity
@Table(name = "watercraft")
public class Watercraft extends Product implements Serializable {
	private static final long serialVersionUID = 7841497969607096164L;


	@AggregationColumn
	@Column(name = "hin")
	private String hin;

	@Column(name = "type_id")
	@Convert(converter = WatercraftType.ConverterImpl.class)
	private WatercraftType watercraftType;

	@Column(name = "manufacturer")
	private String manufacturer;

	@Column(name = "model")
	private String model;

	@Column(name = "trim")
	private String trim;

	@Column(name = "year")
	private Integer year;

	@Column(name = "hull_type_id")
	@Convert(converter = HullType.ConverterImpl.class)
	private HullType hullType;

	@Column(name = "fuel_id")
	@Convert(converter = Fuel.ConverterImpl.class)
	private Fuel fuel;

	@Column(name = "length_feet")
	private Integer length;

	@Column(name = "used")
	private Boolean used;

	@Column(name = "address")
	private String address;

	@Column(name = "zip")
	private String zip;

	@Column(name = "country")
	private String country;

	@Column(name = "contact_url")
	private String contactUrl;

	@Column(name = "contact_name")
	private String contactName;

	@Column(name = "contact_phone")
	private String contactPhone;

	@Column(name = "contact_address")
	private String contactAddress;

	@Column(name = "contact_city")
	private String contactCity;

	@Column(name = "contact_state")
	@Convert(converter = State.ConverterImpl.class)
	private State contactState;

	@Column(name = "contact_country")
	private String contactCountry;

	@Column(name = "contact_zip")
	private String contactZipcode;


	public Watercraft() {
	}

	public Watercraft(String url) {
		super(url);
	}

	@Override
	public String getAggregationColumn() {
		return hin;
	}

	public String getHin() {
		return hin;
	}

	public void setHin(String hin) {
		this.hin = hin;
	}

	public WatercraftType getWatercraftType() {
		return watercraftType;
	}

	public void setWatercraftType(WatercraftType watercraftType) {
		this.watercraftType = watercraftType;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public void setManufacturer(String manufacturer) {
		this.manufacturer = manufacturer;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getTrim() {
		return trim;
	}

	public void setTrim(String trim) {
		this.trim = trim;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public HullType getHullType() {
		return hullType;
	}

	public void setHullType(HullType hullType) {
		this.hullType = hullType;
	}

	public Fuel getFuel() {
		return fuel;
	}

	public void setFuel(Fuel fuel) {
		this.fuel = fuel;
	}

	public Integer getLength() {
		return length;
	}

	public void setLength(Integer length) {
		this.length = length;
	}

	public Boolean getUsed() {
		return used;
	}

	public void setUsed(Boolean used) {
		this.used = used;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
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

	public String getContactAddress() {
		return contactAddress;
	}

	public void setContactAddress(String contactAddress) {
		this.contactAddress = contactAddress;
	}

	public String getContactCity() {
		return contactCity;
	}

	public void setContactCity(String contactCity) {
		this.contactCity = contactCity;
	}

	public State getContactState() {
		return contactState;
	}

	public void setContactState(State contactState) {
		this.contactState = contactState;
	}

	public String getContactCountry() {
		return contactCountry;
	}

	public void setContactCountry(String contactCountry) {
		this.contactCountry = contactCountry;
	}

	public String getContactZipcode() {
		return contactZipcode;
	}

	public void setContactZipcode(String contactZipcode) {
		this.contactZipcode = contactZipcode;
	}
}
