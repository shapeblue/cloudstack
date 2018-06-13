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

import com.cloud.serializer.Param;
import com.cloud.vm.VirtualMachine;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import java.util.Map;

@EntityReference(value = VirtualMachine.class)
public class RetrieveDiagnosticsResponse extends BaseResponse {
    @SerializedName(ApiConstants.RESULT)
    @Param(description = "Have the diagnostics files been copied successfully", since = "4.11", authorized = {RoleType.Admin})
    private Boolean success;

    @SerializedName(ApiConstants.TIMEOUT)
    @Param(description = "the timeout (in seconds) for requests to the retrieve diagnostics API")
    private String timeout;

    @SerializedName("numberoffilescopied")
    @Param(description = "the total number of files copied")
    private Long copiedFilesTotal;


    @SerializedName(ApiConstants.DETAILS)
    @Param(description = "details for the account")
    private Map<String, String> details;


    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public Long getCopiedFilesTotal() {
        return copiedFilesTotal;
    }

    public void setCopiedFilesTotal(Long copiedFilesTotal) {
        this.copiedFilesTotal = copiedFilesTotal;
    }

}
