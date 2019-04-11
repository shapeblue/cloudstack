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

package com.cloud.vm.dao;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.UserVmOVFPropertyVO;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserVmOVFPropertiesDaoImpl extends GenericDaoBase<UserVmOVFPropertyVO, Long> implements UserVmOVFPropertiesDao {

    SearchBuilder<UserVmOVFPropertyVO> OptionsSearchBuilder;

    public UserVmOVFPropertiesDaoImpl() {
        super();
        OptionsSearchBuilder = createSearchBuilder();
        OptionsSearchBuilder.and("vmid", OptionsSearchBuilder.entity().getVmId(), SearchCriteria.Op.EQ);
        OptionsSearchBuilder.and("key", OptionsSearchBuilder.entity().getKey(), SearchCriteria.Op.EQ);
        OptionsSearchBuilder.done();
    }

    @Override
    public boolean existsOption(long vmId, String key) {
        return findByVmIdAndKey(vmId, key) != null;
    }

    @Override
    public UserVmOVFPropertyVO findByVmIdAndKey(long vmId, String key) {
        SearchCriteria<UserVmOVFPropertyVO> sc = OptionsSearchBuilder.create();
        sc.setParameters("vmid", vmId);
        sc.setParameters("key", key);
        return findOneBy(sc);
    }

    @Override
    public void saveOptions(List<UserVmOVFPropertyVO> opts) {
        if (CollectionUtils.isEmpty(opts)) {
            return;
        }
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        txn.start();
        for (UserVmOVFPropertyVO opt : opts) {
            persist(opt);
        }
        txn.commit();
    }

    @Override
    public List<UserVmOVFPropertyVO> listByVmId(long vmId) {
        SearchCriteria<UserVmOVFPropertyVO> sc = OptionsSearchBuilder.create();
        sc.setParameters("vmid", vmId);
        return listBy(sc);
    }
}
