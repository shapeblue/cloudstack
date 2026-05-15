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
package com.cloud.network.dao;

import com.cloud.network.element.OvnMeshNetworkVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@DB()
public class OvnMeshNetworkDaoImpl extends GenericDaoBase<OvnMeshNetworkVO, Long> implements OvnMeshNetworkDao {
    final SearchBuilder<OvnMeshNetworkVO> allFieldsSearch;

    public OvnMeshNetworkDaoImpl() {
        super();
        allFieldsSearch = createSearchBuilder();
        allFieldsSearch.and("id", allFieldsSearch.entity().getId(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("uuid", allFieldsSearch.entity().getUuid(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("mesh_uuid", allFieldsSearch.entity().getMeshUuid(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("vpc_id", allFieldsSearch.entity().getVpcId(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("zone_id", allFieldsSearch.entity().getZoneId(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("account_id", allFieldsSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("state", allFieldsSearch.entity().getState(), SearchCriteria.Op.EQ);
        allFieldsSearch.and("acl_id", allFieldsSearch.entity().getAclId(), SearchCriteria.Op.EQ);
        allFieldsSearch.done();
    }

    @Override
    public List<OvnMeshNetworkVO> listByMeshUuid(String meshUuid) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("mesh_uuid", meshUuid);
        sc.setParameters("state", "Active");
        return listBy(sc);
    }

    @Override
    public List<OvnMeshNetworkVO> listByMeshUuidIncludingDisabled(String meshUuid) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("mesh_uuid", meshUuid);
        return listBy(sc).stream()
                .filter(p -> !"Removed".equals(p.getState()))
                .collect(Collectors.toList());
    }

    @Override
    public List<OvnMeshNetworkVO> listByVpcId(long vpcId) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("vpc_id", vpcId);
        sc.setParameters("state", "Active");
        return listBy(sc);
    }

    @Override
    public OvnMeshNetworkVO findByMeshUuidAndVpcId(String meshUuid, long vpcId) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("mesh_uuid", meshUuid);
        sc.setParameters("vpc_id", vpcId);
        sc.setParameters("state", "Active");
        return findOneBy(sc);
    }

    @Override
    public List<OvnMeshNetworkVO> listByAccountId(long accountId) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("account_id", accountId);
        sc.setParameters("state", "Active");
        return listBy(sc);
    }

    @Override
    public List<OvnMeshNetworkVO> listByAclId(long aclId) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("acl_id", aclId);
        sc.setParameters("state", "Active");
        return listBy(sc);
    }

    @Override
    public List<OvnMeshNetworkVO> listAllActive() {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("state", "Active");
        return listBy(sc);
    }

    @Override
    public List<OvnMeshNetworkVO> listByAccountIdIncludingDisabled(long accountId) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("account_id", accountId);
        return listBy(sc).stream()
                .filter(p -> !"Removed".equals(p.getState()))
                .collect(Collectors.toList());
    }

    @Override
    public List<OvnMeshNetworkVO> listAllIncludingDisabled() {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        return listBy(sc).stream()
                .filter(p -> !"Removed".equals(p.getState()))
                .collect(Collectors.toList());
    }

    @Override
    public OvnMeshNetworkVO findByUuid(String uuid) {
        SearchCriteria<OvnMeshNetworkVO> sc = allFieldsSearch.create();
        sc.setParameters("uuid", uuid);
        return findOneBy(sc);
    }
}
