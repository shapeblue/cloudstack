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
package org.apache.cloudstack.remoteregion.dao;

import java.util.List;

import org.springframework.stereotype.Component;

import org.apache.cloudstack.remoteregion.RemoteRegionVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class RemoteRegionDaoImpl extends GenericDaoBase<RemoteRegionVO, Long> implements RemoteRegionDao {
    private final SearchBuilder<RemoteRegionVO> EndpointSearch;

    public RemoteRegionDaoImpl() {
        EndpointSearch = createSearchBuilder();
        EndpointSearch.and("endpoint", EndpointSearch.entity().getEndpoint(), SearchCriteria.Op.EQ);
        EndpointSearch.done();
    }


    @Override
    public List<RemoteRegionVO> listByEndpoint(String endpoint) {
        SearchCriteria<RemoteRegionVO> sc = EndpointSearch.create();
        sc.setParameters("endpoint", endpoint);
        return listBy(sc);
    }
}
