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
package com.cloud.api;

import java.util.List;

import org.apache.cloudstack.config.ConfigKeyUsageVO;
import org.apache.cloudstack.config.dao.ConfigKeyUsageDao;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKeyAccessTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigKeyUsageRecorder {
    private static final Logger logger = LogManager.getLogger(ConfigKeyUsageRecorder.class);

    private ConfigKeyUsageDao configKeyUsageDao;

    public void setConfigKeyUsageDao(ConfigKeyUsageDao configKeyUsageDao) {
        this.configKeyUsageDao = configKeyUsageDao;
    }

    public void persist(String apiName, CallContext context, List<ConfigKeyAccessTracker.Access> accesses) {
        if (accesses == null || accesses.isEmpty()) {
            return;
        }
        Long userId = context != null ? context.getCallingUserId() : null;
        Long accountId = context != null ? context.getCallingAccountId() : null;
        String contextId = context != null ? context.getContextId() : null;
        for (ConfigKeyAccessTracker.Access access : accesses) {
            try {
                ConfigKeyUsageVO usageVO = new ConfigKeyUsageVO(apiName, contextId, userId, accountId,
                        access.getKey(), access.getScope());
                configKeyUsageDao.persist(usageVO);
            } catch (Exception e) {
                logger.debug("Failed to persist config key usage for API {} and key {}", apiName, access.getKey(), e);
            }
        }
    }
}
