// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.command.dao;

import java.util.Date;
import java.util.List;

import org.apache.cloudstack.command.ReconcileCommandVO;
import org.springframework.stereotype.Component;

import com.cloud.agent.api.Command;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@DB
public class ReconcileCommandDaoImpl extends GenericDaoBase<ReconcileCommandVO, Long> implements ReconcileCommandDao {

    final SearchBuilder<ReconcileCommandVO> updateCommandSearch;

    public ReconcileCommandDaoImpl() {

        updateCommandSearch = createSearchBuilder();
        updateCommandSearch.and("managementServerId", updateCommandSearch.entity().getManagementServerId(), SearchCriteria.Op.EQ);
        updateCommandSearch.and("stateByManagement", updateCommandSearch.entity().getStateByManagement(), SearchCriteria.Op.IN);
        updateCommandSearch.and("reqSequence", updateCommandSearch.entity().getRequestSequence(), SearchCriteria.Op.EQ);
        updateCommandSearch.and("commandName", updateCommandSearch.entity().getCommandName(), SearchCriteria.Op.EQ);
        updateCommandSearch.done();
    }

    @Override
    public List<ReconcileCommandVO> listByManagementServerId(long managementServerId) {
        QueryBuilder<ReconcileCommandVO> sc = QueryBuilder.create(ReconcileCommandVO.class);
        sc.and(sc.entity().getManagementServerId(), SearchCriteria.Op.EQ, managementServerId);
        return sc.list();
    }

    @Override
    public List<ReconcileCommandVO> listByHostId(long hostId) {
        QueryBuilder<ReconcileCommandVO> sc = QueryBuilder.create(ReconcileCommandVO.class);
        sc.and(sc.entity().getHostId(), SearchCriteria.Op.EQ, hostId);
        return sc.list();
    }

    @Override
    public List<ReconcileCommandVO> listByState(Command.State... states) {
        QueryBuilder<ReconcileCommandVO> sc = QueryBuilder.create(ReconcileCommandVO.class);
        sc.and(sc.entity().getStateByManagement(), SearchCriteria.Op.IN,  (Object[]) states);
        return sc.list();
    }

    @Override
    public void removeCommand(long reqSequence, String commandName, Command.State state) {
        SearchCriteria<ReconcileCommandVO> sc = updateCommandSearch.create();
        sc.setParameters("reqSequence", reqSequence);
        sc.setParameters("commandName", commandName);

        ReconcileCommandVO vo = createForUpdate();
        if (state != null) {
            vo.setStateByManagement(state);
        }
        vo.setRemoved(new Date());
        update(vo, sc);
    }

    @Override
    public ReconcileCommandVO findCommand(long reqSequence, String commandName) {
        SearchCriteria<ReconcileCommandVO> sc = updateCommandSearch.create();
        sc.setParameters("reqSequence", reqSequence);
        sc.setParameters("commandName", commandName);
        return findOneBy(sc);
    }

    @Override
    public void updateCommandsToInterrupted(long managementServerId) {
        SearchCriteria<ReconcileCommandVO> sc = updateCommandSearch.create();
        sc.setParameters("managementServerId", managementServerId);
        sc.setParameters("stateByManagement", Command.State.CREATED, Command.State.RECONCILING);

        ReconcileCommandVO vo = createForUpdate();
        vo.setStateByManagement(Command.State.INTERRUPTED);

        update(vo, sc);
    }
}
