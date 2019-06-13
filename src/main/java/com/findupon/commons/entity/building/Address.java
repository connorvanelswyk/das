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

package com.findupon.commons.entity.building;

import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.entity.product.realestate.RealEstate;
import com.findupon.commons.entity.product.watercraft.Watercraft;

import java.io.Serializable;


public class Address implements Serializable {
	private static final long serialVersionUID = 3963756841460940641L;

	private String line;
	private String city;
	private State state;
	private String zip;
	private Float latitude;
	private Float longitude;


	public void setAutomobileAddress(Automobile automobile) {
		automobile.setAddress(line);
		automobile.setZip(zip);
		automobile.setLatitude(latitude);
		automobile.setLongitude(longitude);
	}

	public void setRealEstateAddress(RealEstate realEstate) {
		realEstate.setAddress(line);
		realEstate.setZip(zip);
		realEstate.setLatitude(latitude);
		realEstate.setLongitude(longitude);
	}

	public void setWatercraftAddress(Watercraft watercraft) {
		watercraft.setAddress(line);
		watercraft.setZip(zip);
		watercraft.setLatitude(latitude);
		watercraft.setLongitude(longitude);
	}

	public void setAircraftAddress(Aircraft aircraft) {
		aircraft.setAddress(line);
		aircraft.setZip(zip);
		aircraft.setLatitude(latitude);
		aircraft.setLongitude(longitude);
	}

	@Override
	public String toString() {
		return "Address{" +
				"line='" + line + '\'' +
				", zip='" + zip + '\'' +
				", latitude=" + latitude +
				", longitude=" + longitude +
				'}';
	}

	public String getLine() {
		return line;
	}

	public void setLine(String line) {
		this.line = line;
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
