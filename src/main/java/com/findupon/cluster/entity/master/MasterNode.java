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

package com.findupon.cluster.entity.master;

import javax.persistence.*;
import java.util.Date;


@Entity
@Table(name = "master_node")
public class MasterNode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "address")
	private String address;

	@Column(name = "port")
	private Integer port;

	@Column(name = "region")
	private String region;

	@Column(name = "running")
	private Boolean running;

	@Column(name = "startup_dttm")
	private Date startupDate;


	public static MasterNode getStarted() {
		MasterNode masterNode = new MasterNode();
		masterNode.setAddress(com.findupon.commons.utilities.NetworkUtils.getExternalOrLocalIpAddress());
		masterNode.setPort(com.findupon.commons.utilities.NetworkUtils.getAvailablePort());
		masterNode.setRegion(com.findupon.commons.utilities.LocationUtils.getRegion(masterNode.getAddress()));
		masterNode.setStartupDate(new Date());
		masterNode.setRunning(true);
		return masterNode;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public Boolean getRunning() {
		return running;
	}

	public void setRunning(Boolean running) {
		this.running = running;
	}

	public Date getStartupDate() {
		return startupDate;
	}

	public void setStartupDate(Date startupDate) {
		this.startupDate = startupDate;
	}
}
