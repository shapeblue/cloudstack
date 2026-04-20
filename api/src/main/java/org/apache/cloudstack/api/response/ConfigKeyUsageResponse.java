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
package org.apache.cloudstack.api.response;

import com.google.gson.annotations.SerializedName;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import com.cloud.serializer.Param;

public class ConfigKeyUsageResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "The ID of the config key usage record")
    private String id;

    @SerializedName("apiname")
    @Param(description = "The API name that accessed the config key")
    private String apiName;

    @SerializedName("configkey")
    @Param(description = "The config key that was accessed")
    private String configKey;

    @SerializedName("scope")
    @Param(description = "The scope of the config key (global, zone, cluster, storage, account, domain, imagestore)")
    private String scope;

    @SerializedName("contextid")
    @Param(description = "The context ID of the API call")
    private String contextId;

    @SerializedName(ApiConstants.USER_ID)
    @Param(description = "The user ID who accessed the config key")
    private Long userId;

    @SerializedName(ApiConstants.ACCOUNT_ID)
    @Param(description = "The account ID associated with the access")
    private Long accountId;

    @SerializedName("created")
    @Param(description = "The time the config key usage was recorded")
    private String created;

    @SerializedName("resolved_scope")
    @Param(description = "The resolved scope of the config key usage")
    private String resolvedScope;

    @SerializedName("config_value")
    @Param(description = "The config value observed during access")
    private String configValue;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getResolvedScope() {
        return resolvedScope;
    }

    public void setResolvedScope(String resolvedScope) {
        this.resolvedScope = resolvedScope;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
