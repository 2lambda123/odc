/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.metadb.flow;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.oceanbase.odc.core.shared.constant.TaskType;
import com.oceanbase.odc.service.flow.model.FlowNodeStatus;
import com.oceanbase.odc.service.flow.model.FlowNodeType;

/**
 * Repository layer for {@link ServiceTaskInstanceEntity}
 *
 * @author yh263208
 * @date 2022-02-15 11:44
 * @since ODC_release_3.3.0
 */
public interface ServiceTaskInstanceRepository extends JpaRepository<ServiceTaskInstanceEntity, Long>,
        JpaSpecificationExecutor<ServiceTaskInstanceEntity> {

    @Transactional
    @Query("update ServiceTaskInstanceEntity as st set st.targetTaskId=:#{#param.targetTaskId},st.status=:#{#param.status},"
            + "st.taskType=:#{#param.taskType} where st.id=:#{#param.id}")
    @Modifying
    int update(@Param("param") ServiceTaskInstanceEntity entity);

    @Transactional
    @Query(value = "update flow_instance_node_task set status=:#{#status.name()} where id=:id", nativeQuery = true)
    @Modifying
    int updateStatusById(@Param("id") Long id, @Param("status") FlowNodeStatus status);

    @Transactional
    @Query("delete from ServiceTaskInstanceEntity as st where st.flowInstanceId=:instanceId")
    @Modifying
    int deleteByFlowInstanceId(@Param("instanceId") Long instanceId);

    List<ServiceTaskInstanceEntity> findByFlowInstanceIdIn(Set<Long> flowInstanceIds);

    @Query(value = "select na.* from flow_instance_node_task as na inner join flow_instance_node as n on na.id=n.instance_id "
            + "where n.instance_type=:#{#instanceType.name()} and n.activity_id=:activityId and n.flow_instance_id=:flowInstanceId",
            nativeQuery = true)
    Optional<ServiceTaskInstanceEntity> findByInstanceTypeAndActivityId(
            @Param("instanceType") FlowNodeType instanceType, @Param("activityId") String activityId,
            @Param("flowInstanceId") Long flowInstanceId);

    @Query(value = "select na.* from flow_instance_node_task as na inner join flow_instance_node as n on na.id=n.instance_id "
            + "where n.instance_type=:#{#instanceType.name()} and n.name=:name and n.flow_instance_id=:flowInstanceId",
            nativeQuery = true)
    Optional<ServiceTaskInstanceEntity> findByInstanceTypeAndName(@Param("instanceType") FlowNodeType instanceType,
            @Param("name") String name, @Param("flowInstanceId") Long flowInstanceId);

    Optional<ServiceTaskInstanceEntity> findByFlowInstanceId(Long flowInstanceId);

    @Query(value = "select nt.flow_instance_id from flow_instance_node_task nt inner join task_task tt on "
            + "nt.task_task_id=tt.id where tt.connection_id in (:connectionIds) and "
            + "(nt.create_time between :startTime and :endTime)",
            nativeQuery = true)
    List<Long> findIdByConnectionIdsAndPeriod(@Param("connectionIds") Collection<Long> connectionIds,
            @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Query(value = "select nt.flow_instance_id from flow_instance_node_task nt inner join task_task tt on "
            + "nt.task_task_id=tt.id where tt.database_name like %:databaseName% and "
            + "(nt.create_time between :startTime and :endTime)",
            nativeQuery = true)
    List<Long> findIdByDatabaseNameLikeAndPeriod(@Param("databaseName") String databaseName,
            @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Query(value = "select nt.flow_instance_id from flow_instance_node_task nt where nt.task_type=:taskType and "
            + "(nt.create_time between :startTime and :endTime)", nativeQuery = true)
    List<Long> findIdByTaskTypeAndPeriod(@Param("taskType") String taskType, @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    @Query(value = "select b.* from flow_instance a left join flow_instance_node_task b "
            + "on a.id = b.flow_instance_id "
            + "where a.parent_instance_id=:id and b.task_type=:#{#type.name()}", nativeQuery = true)
    List<ServiceTaskInstanceEntity> findByScheduleIdAndTaskType(@Param("id") Long scheduleId,
            @Param("type") TaskType type);

}
