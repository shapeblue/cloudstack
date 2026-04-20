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

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "config_key_usage")
public class ConfigKeyUsageVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false)
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "api_name")
    private String apiName;

    @Column(name = "context_id")
    private String contextId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "resolved_scope", nullable = false)
    private String resolvedScope;

    @Column(name = "config_value")
    private String configValue;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created", nullable = false)
    private Date created = new Date();

    public ConfigKeyUsageVO() {
    }

    public ConfigKeyUsageVO(String apiName, String contextId, Long userId, Long accountId, String configKey, String scope,
            String resolvedScope, String configValue) {
        this.apiName = apiName;
        this.contextId = contextId;
        this.userId = userId;
        this.accountId = accountId;
        this.configKey = configKey;
        this.scope = scope;
        this.resolvedScope = resolvedScope;
        this.configValue = configValue;
    }

    public Long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getApiName() {
        return apiName;
    }

    public String getContextId() {
        return contextId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getScope() {
        return scope;
    }

    public String getResolvedScope() {
        return resolvedScope;
    }

    public String getConfigValue() {
        return configValue;
    }

    public Date getCreated() {
        return created;
    }
}

