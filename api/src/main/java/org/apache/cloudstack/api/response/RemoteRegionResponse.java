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

import org.apache.cloudstack.remoteregion.RemoteRegion;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import com.cloud.serializer.Param;
import org.apache.cloudstack.api.EntityReference;

@EntityReference(value = RemoteRegion.class)
public class RemoteRegionResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the remote region")
    private String id;

    @SerializedName("description")
    @Param(description = "the description of the remote region")
    private String description;

    @SerializedName("endpoint")
    @Param(description = "the endpoint URL of the remote region")
    private String endpoint;

    @SerializedName("apikey")
    @Param(description = "the API key for the remote region")
    private String apiKey;

    @SerializedName("sslverify")
    @Param(description = "whether SSL verification is enabled for the remote region")
    private Boolean sslVerify;

    @SerializedName("enabled")
    @Param(description = "whether the remote region is enabled")
    private Boolean enabled;

    @SerializedName("scope")
    @Param(description = "the access scope of the remote region")
    private String scope;

    public RemoteRegionResponse(RemoteRegion remoteRegion) {
        id = remoteRegion.getUuid();
        description = remoteRegion.getDescription();
        endpoint = remoteRegion.getEndpoint();
        apiKey = remoteRegion.getApiKey();
        sslVerify = remoteRegion.isSslVerify();
        enabled = remoteRegion.isEnabled();
        scope = remoteRegion.getScope();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Boolean getSslVerify() {
        return sslVerify;
    }

    public void setSslVerify(Boolean sslVerify) {
        this.sslVerify = sslVerify;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
