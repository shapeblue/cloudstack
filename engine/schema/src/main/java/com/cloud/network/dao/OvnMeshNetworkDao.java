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
import com.cloud.utils.db.GenericDao;

import java.util.List;

public interface OvnMeshNetworkDao extends GenericDao<OvnMeshNetworkVO, Long> {
    List<OvnMeshNetworkVO> listByMeshUuid(String meshUuid);
    List<OvnMeshNetworkVO> listByMeshUuidIncludingDisabled(String meshUuid);
    List<OvnMeshNetworkVO> listByVpcId(long vpcId);
    List<OvnMeshNetworkVO> listByNetworkId(long networkId);
    OvnMeshNetworkVO findByMeshUuidAndVpcId(String meshUuid, long vpcId);
    OvnMeshNetworkVO findByMeshUuidAndNetworkId(String meshUuid, long networkId);
    List<OvnMeshNetworkVO> listByAccountId(long accountId);
    List<OvnMeshNetworkVO> listByAccountIdIncludingDisabled(long accountId);
    List<OvnMeshNetworkVO> listAllActive();
    List<OvnMeshNetworkVO> listAllIncludingDisabled();
    List<OvnMeshNetworkVO> listByAclId(long aclId);
    OvnMeshNetworkVO findByUuid(String uuid);
}
