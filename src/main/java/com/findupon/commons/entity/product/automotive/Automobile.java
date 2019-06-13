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

package com.findupon.commons.entity.product.automotive;

import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.attribute.*;

import javax.persistence.*;
import java.io.Serializable;

import static com.findupon.commons.utilities.ConsoleColors.safeLog;


@Entity
@Table(name = "automobile")
public class Automobile extends Product implements Serializable {
	private static final long serialVersionUID = 8530555932565685427L;

	@Column(name = "dealer_url")
	private String dealerUrl;

	@Column(name = "dealer_name")
	private String dealerName;

	@Column(name = "stock_number")
	private String stockNumber;

	@Column(name = "address")
	private String address;

	@Column(name = "zip")
	private String zip;

	@AggregationColumn
	@Column(name = "vin")
	private String vin;

	@Transient
	private String make;

	@Column(name = "make_id")
	private Integer makeId;

	@Transient
	private String model;

	@Column(name = "model_id")
	private Integer modelId;

	@Transient
	private String trim;

	@Column(name = "trim_id")
	private Integer trimId;

	@Column(name = "year")
	private Integer year;

	@Column(name = "mileage")
	private Integer mileage;

	@Column(name = "ext_color_id")
	@Convert(converter = ExteriorColor.ConverterImpl.class)
	private ExteriorColor exteriorColor;

	@Column(name = "int_color_id")
	@Convert(converter = InteriorColor.ConverterImpl.class)
	private InteriorColor interiorColor;

	@Column(name = "mpg_city")
	private Integer mpgCity;

	@Column(name = "mpg_highway")
	private Integer mpgHighway;

	@Column(name = "doors")
	private Integer doors;

	@Column(name = "fuel_id")
	@Convert(converter = Fuel.ConverterImpl.class)
	private Fuel fuel;

	@Column(name = "transmission_id")
	@Convert(converter = Transmission.ConverterImpl.class)
	private Transmission transmission;

	@Column(name = "drivetrain_id")
	@Convert(converter = Drivetrain.ConverterImpl.class)
	private Drivetrain drivetrain;

	@Column(name = "body_id")
	@Convert(converter = Body.ConverterImpl.class)
	private Body body;

	public String toLog() {
		String line1 = String.format("Automobile ID: [%d] URL: [%s]", getId(), getUrl());
		String line2 = String.format("%-33s %-33s %-33s %-22s %-26s %-28s %-28s %-22s %-21s %s",
				"Mk: " + safeLog(getMake()),
				"Mo:  " + safeLog(getModel()),
				"Tr: " + safeLog(getTrim()),
				"Yr: " + safeLog(getYear()),
				"Mls: " + safeLog(getMileage() == null ? null : String.format("%,d", getMileage())),
				"P:  " + safeLog(getPrice() == null ? null : "$" + String.format("%,d", getPrice().intValue())),
				"S#: " + safeLog(getStockNumber()),
				"Adr: " + safeLog(getAddress() != null ? "Y" : null),
				"Img: " + safeLog(getMainImageUrl() != null ? "Y" : null),
				"Doors: " + safeLog(getDoors())
		);
		String line3 = String.format("%-33s %-33s %-33s %-38s %-28s %-28s %-22s %s",
				"Fu: " + safeLog(getFuel()),
				"Trn: " + safeLog(getTransmission()),
				"Dr: " + safeLog(getDrivetrain()),
				"Bd: " + safeLog(getBody()),
				"Ex: " + safeLog(getExteriorColor()),
				"In: " + safeLog(getInteriorColor()),
				"MPC: " + safeLog(getMpgCity()),
				"MPH: " + safeLog(getMpgHighway())
		);
		return line1 + "\n" + line2 + "\n" + line3;
	}

	public Automobile() {
		super();
	}

	public Automobile(String url) {
		super(url);
	}

	@Override
	public String getAggregationColumn() {
		return vin;
	}

	public String getDealerUrl() {
		return dealerUrl;
	}

	public void setDealerUrl(String dealerUrl) {
		this.dealerUrl = dealerUrl;
	}

	public String getDealerName() {
		return dealerName;
	}

	public void setDealerName(String dealerName) {
		this.dealerName = dealerName;
	}

	public String getStockNumber() {
		return stockNumber;
	}

	public void setStockNumber(String stockNumber) {
		this.stockNumber = stockNumber;
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

	public String getVin() {
		return vin;
	}

	public void setVin(String vin) {
		this.vin = vin;
	}

	public String getMake() {
		return make;
	}

	public void setMake(String make) {
		this.make = make;
	}

	public Integer getMakeId() {
		return makeId;
	}

	public void setMakeId(Integer makeId) {
		this.makeId = makeId;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getModelId() {
		return modelId;
	}

	public void setModelId(Integer modelId) {
		this.modelId = modelId;
	}

	public String getTrim() {
		return trim;
	}

	public void setTrim(String trim) {
		this.trim = trim;
	}

	public Integer getTrimId() {
		return trimId;
	}

	public void setTrimId(Integer trimId) {
		this.trimId = trimId;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	public Integer getMileage() {
		return mileage;
	}

	public void setMileage(Integer mileage) {
		this.mileage = mileage;
	}

	public ExteriorColor getExteriorColor() {
		return exteriorColor;
	}

	public void setExteriorColor(ExteriorColor exteriorColor) {
		this.exteriorColor = exteriorColor;
	}

	public void setExteriorColor(Integer exteriorColorId) {
		this.exteriorColor = ExteriorColor.of(exteriorColorId);
	}

	public InteriorColor getInteriorColor() {
		return interiorColor;
	}

	public void setInteriorColor(InteriorColor interiorColor) {
		this.interiorColor = interiorColor;
	}

	public void setInteriorColor(Integer interiorColorId) {
		this.interiorColor = InteriorColor.of(interiorColorId);
	}

	public Integer getMpgCity() {
		return mpgCity;
	}

	public void setMpgCity(Integer mpgCity) {
		this.mpgCity = mpgCity;
	}

	public Integer getMpgHighway() {
		return mpgHighway;
	}

	public void setMpgHighway(Integer mpgHighway) {
		this.mpgHighway = mpgHighway;
	}

	public Integer getDoors() {
		return doors;
	}

	public void setDoors(Integer doors) {
		this.doors = doors;
	}

	public Fuel getFuel() {
		return fuel;
	}

	public void setFuel(Fuel fuel) {
		this.fuel = fuel;
	}

	public void setFuel(int fuelId) {
		this.fuel = Fuel.of(fuelId);
	}

	public Transmission getTransmission() {
		return transmission;
	}

	public void setTransmission(Transmission transmission) {
		this.transmission = transmission;
	}

	public void setTransmission(int transmissionId) {
		this.transmission = Transmission.of(transmissionId);
	}

	public Drivetrain getDrivetrain() {
		return drivetrain;
	}

	public void setDrivetrain(Drivetrain drivetrain) {
		this.drivetrain = drivetrain;
	}

	public void setDrivetrain(int drivetrainId) {
		this.drivetrain = Drivetrain.of(drivetrainId);
	}

	public Body getBody() {
		return body;
	}

	public void setBody(Body body) {
		this.body = body;
	}

	public void setBody(int bodyId) {
		this.body = Body.of(bodyId);
	}
}
