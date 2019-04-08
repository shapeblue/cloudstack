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

package org.apache.cloudstack.storage.datastore.db;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OVFPropertiesDaoImpl extends GenericDaoBase<OVFPropertyVO, Long> implements OVFPropertiesDao {

    private final static Logger s_logger = Logger.getLogger(OVFPropertiesDaoImpl.class);

    SearchBuilder<OVFPropertyVO> OptionsSearchBuilder;

    public OVFPropertiesDaoImpl() {
        super();
        OptionsSearchBuilder = createSearchBuilder();
        OptionsSearchBuilder.and("templateid", OptionsSearchBuilder.entity().getTemplateId(), SearchCriteria.Op.EQ);
        OptionsSearchBuilder.and("key", OptionsSearchBuilder.entity().getKey(), SearchCriteria.Op.EQ);
        OptionsSearchBuilder.done();
    }

    @Override
    public boolean existsOption(long templateId, String key) {
        return findByTemplateAndKey(templateId, key) != null;
    }

    @Override
    public OVFPropertyVO findByTemplateAndKey(long templateId, String key) {
        SearchCriteria<OVFPropertyVO> sc = OptionsSearchBuilder.create();
        sc.setParameters("templateid", templateId);
        sc.setParameters("key", key);
        return findOneBy(sc);
    }

    @Override
    public void saveOptions(List<OVFPropertyVO> opts) {
        if (CollectionUtils.isEmpty(opts)) {
            return;
        }
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        txn.start();
        for (OVFPropertyVO opt : opts) {
            persist(opt);
        }
        txn.commit();
    }
}
