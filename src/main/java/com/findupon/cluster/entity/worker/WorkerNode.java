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

package com.findupon.cluster.entity.worker;

import javax.persistence.*;
import java.util.Date;
import java.util.Objects;


@Entity
@Table(name = "worker_node")
public class WorkerNode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "address")
	private String address;

	@Column(name = "port")
	private Integer port;

	@Column(name = "region")
	private String region;

	@Enumerated(EnumType.STRING)
	@Column(name = "connection_status")
	private NodeConnectionStatus connectionStatus;

	@Column(name = "connection_status_dttm")
	private Date connectionStatusDate;

	@Column(name = "working")
	private Boolean isWorking;

	@Column(name = "work_description")
	private String workDescription;


	public static WorkerNode newIdleNode() {
		WorkerNode node = new WorkerNode();
		node.setAddress(com.plainviewrd.commons.utilities.NetworkUtils.getExternalOrLocalIpAddress());
		node.setPort(com.plainviewrd.commons.utilities.NetworkUtils.getAvailablePort());
		node.setRegion(com.plainviewrd.commons.utilities.LocationUtils.getRegion(node.getAddress()));
		node.setConnectionStatus(NodeConnectionStatus.IDLE);
		node.setWorkDescription(null);
		node.setIsWorking(false);
		return node;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o == null || getClass() != o.getClass()) {
			return false;
		}
		WorkerNode that = (WorkerNode)o;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "WorkerNode{" + "id=" + id +
				", address='" + address + '\'' +
				", port=" + port +
				", connectionStatus=" + connectionStatus +
				", connectionStatusDate=" + connectionStatusDate +
				", isWorking=" + isWorking +
				", workDescription='" + workDescription + '\'' + '}';
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

	public NodeConnectionStatus getConnectionStatus() {
		return connectionStatus;
	}

	public void setConnectionStatus(NodeConnectionStatus connectionStatus) {
		this.connectionStatus = connectionStatus;
		this.connectionStatusDate = new Date();
	}

	public Date getConnectionStatusDate() {
		return connectionStatusDate;
	}

	public void setConnectionStatusDate(Date connectionStatusDate) {
		this.connectionStatusDate = connectionStatusDate;
	}

	public Boolean getIsWorking() {
		return isWorking;
	}

	public void setIsWorking(Boolean isWorking) {
		this.isWorking = isWorking;
	}

	public String getWorkDescription() {
		return workDescription;
	}

	public void setWorkDescription(String workDescription) {
		this.workDescription = workDescription;
	}
}
