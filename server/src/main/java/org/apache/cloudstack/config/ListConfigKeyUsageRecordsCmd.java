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
package org.apache.cloudstack.config;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.ConfigKeyUsageResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.config.dao.ConfigKeyUsageDao;
import org.apache.commons.lang3.StringUtils;

import com.cloud.utils.Pair;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@APICommand(name = "listConfigKeyUsageRecords", description = "Lists config key usage records", responseObject = ConfigKeyUsageResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListConfigKeyUsageRecordsCmd extends BaseListCmd {

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.API_NAME, type = CommandType.STRING, description = "The API name that accessed the config key")
    private String apiName;

    @Parameter(name = ApiConstants.CONFIG_KEY, type = CommandType.STRING, description = "The config key that was accessed")
    private String configKey;

    @Parameter(name = ApiConstants.SCOPE, type = CommandType.STRING, description = "The scope of the config key (global, zone, cluster, storage, account, domain, imagestore)")
    private String scope;

    @Parameter(name = ApiConstants.ACCOUNT_ID, type = CommandType.UUID, entityType = AccountResponse.class, description = "The account ID to filter by")
    private Long accountId;

    @Inject
    private ConfigKeyUsageDao configKeyUsageDao;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public String getApiName() {
        return apiName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getScope() {
        return scope;
    }

    public Long getAccountId() {
        return accountId;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        SearchBuilder<ConfigKeyUsageVO> sb = configKeyUsageDao.createSearchBuilder();
        sb.and("apiName", sb.entity().getApiName(), SearchCriteria.Op.EQ);
        sb.and("configKey", sb.entity().getConfigKey(), SearchCriteria.Op.EQ);
        sb.and("scope", sb.entity().getScope(), SearchCriteria.Op.EQ);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("apiNameLike", sb.entity().getApiName(), SearchCriteria.Op.LIKE);
        sb.and("configKeyLike", sb.entity().getConfigKey(), SearchCriteria.Op.LIKE);
        sb.done();

        SearchCriteria<ConfigKeyUsageVO> sc = sb.create();

        if (StringUtils.isNotEmpty(apiName)) {
            sc.setParameters("apiName", apiName);
        }

        if (StringUtils.isNotEmpty(configKey)) {
            sc.setParameters("configKey", configKey);
        }

        if (StringUtils.isNotEmpty(scope)) {
            sc.setParameters("scope", scope);
        }

        if (accountId != null) {
            sc.setParameters("accountId", accountId);
        }

        String keyword = getKeyword();
        if (StringUtils.isNotEmpty(keyword)) {
            sc.setParameters("apiNameLike", "%" + keyword + "%");
            sc.setParameters("configKeyLike", "%" + keyword + "%");
        }

        Filter searchFilter = new Filter(ConfigKeyUsageVO.class, "created", false, getStartIndex(), getPageSizeVal());
        Pair<List<ConfigKeyUsageVO>, Integer> result = configKeyUsageDao.searchAndCount(sc, searchFilter);

        ListResponse<ConfigKeyUsageResponse> response = new ListResponse<>();
        List<ConfigKeyUsageResponse> configKeyUsageResponses = new ArrayList<>();

        for (ConfigKeyUsageVO usageVO : result.first()) {
            ConfigKeyUsageResponse usageResponse = new ConfigKeyUsageResponse();
            usageResponse.setId(usageVO.getUuid());
            usageResponse.setApiName(usageVO.getApiName());
            usageResponse.setConfigKey(usageVO.getConfigKey());
            usageResponse.setScope(usageVO.getScope());
            usageResponse.setResolvedScope(usageVO.getResolvedScope());
            usageResponse.setConfigValue(usageVO.getConfigValue());
            usageResponse.setContextId(usageVO.getContextId());
            usageResponse.setUserId(usageVO.getUserId());
            usageResponse.setAccountId(usageVO.getAccountId());
            usageResponse.setCreated(usageVO.getCreated() != null ? usageVO.getCreated().toString() : null);
            usageResponse.setObjectName("configkeyusage");
            configKeyUsageResponses.add(usageResponse);
        }

        response.setResponses(configKeyUsageResponses, result.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
