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

package com.findupon.repository;

import com.findupon.cluster.entity.worker.WorkerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Repository
@Transactional(isolation = Isolation.SERIALIZABLE)
public interface WorkerNodeRepo extends JpaRepository<WorkerNode, Long> {

	@Query("select w from WorkerNode w where w.connectionStatus = 'STARTED'")
	List<WorkerNode> findAllAwaitingConnection();

	@Query("select w from WorkerNode w where w.connectionStatus = 'FAILURE'")
	List<WorkerNode> findFailureNodes();
}
