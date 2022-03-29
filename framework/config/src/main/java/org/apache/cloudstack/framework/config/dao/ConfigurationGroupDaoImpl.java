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
package org.apache.cloudstack.framework.config.dao;

import org.apache.cloudstack.framework.config.impl.ConfigurationGroupVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class ConfigurationGroupDaoImpl extends GenericDaoBase<ConfigurationGroupVO, Long> implements ConfigurationGroupDao {
    private static final Logger s_logger = Logger.getLogger(ConfigurationGroupDaoImpl.class);

    final SearchBuilder<ConfigurationGroupVO> NameSearch;

    public ConfigurationGroupDaoImpl() {
        super();

        NameSearch = createSearchBuilder();
        NameSearch.and("name", NameSearch.entity().getName(), SearchCriteria.Op.EQ);
    }

    @Override
    public ConfigurationGroupVO findByName(String name) {
        SearchCriteria<ConfigurationGroupVO> sc = NameSearch.create();
        sc.setParameters("name", name);
        return findOneIncludingRemovedBy(sc);
    }
}
