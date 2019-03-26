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

import java.util.List;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class DetailOptionsResponse extends BaseResponse {
    @SerializedName(ApiConstants.KEY)
    @Param(description = "Name of a possible detail key for the resource")
    private String key;

    @SerializedName(ApiConstants.VALUES)
    @Param(description = "List of possible values for the key")
    private List<String> details;

    @SerializedName(ApiConstants.CUSTOMIZED)
    @Param(description = "True is value can be a custom value")
    private Boolean isCustom = false;

    public DetailOptionsResponse(String key, List<String> details) {
        this.key = key;
        this.details = details;
        setObjectName("details");
    }

    public DetailOptionsResponse(String key, Boolean isCustom) {
        this.key = key;
        this.isCustom = isCustom;
        setObjectName("details");
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }
}
