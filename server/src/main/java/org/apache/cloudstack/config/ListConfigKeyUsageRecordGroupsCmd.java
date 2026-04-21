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
import org.apache.cloudstack.api.response.ConfigKeyUsageGroupResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.config.dao.ConfigKeyUsageDao;

import com.cloud.utils.Pair;

@APICommand(name = "listConfigKeyUsageRecordGroups", description = "Lists grouped config key usage records by API name and config key",
        responseObject = ConfigKeyUsageGroupResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListConfigKeyUsageRecordGroupsCmd extends BaseListCmd {

    @Parameter(name = ApiConstants.API_NAME, type = CommandType.STRING, description = "The API name that accessed the config key")
    private String apiName;

    @Parameter(name = ApiConstants.CONFIG_KEY, type = CommandType.STRING, description = "The config key that was accessed")
    private String configKey;

    @Inject
    private ConfigKeyUsageDao configKeyUsageDao;

    public String getApiName() {
        return apiName;
    }

    public String getConfigKey() {
        return configKey;
    }

    @Override
    public void execute() {
        Pair<List<ConfigKeyUsageGroupVO>, Integer> groupedResult =
                configKeyUsageDao.searchGroupedByApiNameAndConfigKey(apiName, configKey, getStartIndex(), getPageSizeVal());

        List<ConfigKeyUsageGroupResponse> groupedResponses = new ArrayList<>();
        for (ConfigKeyUsageGroupVO groupedUsage : groupedResult.first()) {
            ConfigKeyUsageGroupResponse groupedResponse = new ConfigKeyUsageGroupResponse();
            groupedResponse.setApiName(groupedUsage.getApiName());
            groupedResponse.setConfigKey(groupedUsage.getConfigKey());
            groupedResponse.setCount(groupedUsage.getCount());
            groupedResponse.setObjectName("configkeyusagegroup");
            groupedResponses.add(groupedResponse);
        }

        ListResponse<ConfigKeyUsageGroupResponse> response = new ListResponse<>();
        response.setResponses(groupedResponses, groupedResult.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
