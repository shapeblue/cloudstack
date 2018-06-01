//
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
//

package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.cloud.vm.VirtualMachine;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

@EntityReference(value = VirtualMachine.class)
public class RemoteDiagnosticsResponse extends BaseResponse {
    @SerializedName(ApiConstants.DETAILS)
    @Param(description = "Script execution result")
    private String details;

    @SerializedName(ApiConstants.RESULT)
    @Param(description = "true if operation is executed successfully")
    private Boolean success;

    @SerializedName("command")
    @Param(description = "Command passed to script")
    private String networkCommand;

    public String getNetworkCommand() {
        return networkCommand;
    }

    public void setNetworkCommand(String networkCommand) {
        this.networkCommand = networkCommand;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Boolean getResult() {
        return success;
    }

    public void setResult(Boolean success) {
        this.success = success;
    }
}
