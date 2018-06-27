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
    @SerializedName(ApiConstants.STDOUT)
    @Param(description = "standard output from the command execution", since = "4.11", authorized = {RoleType.Admin})
    private String stdout;

    @SerializedName(ApiConstants.STDERR)
    @Param(description = "standard error from the command execution.")
    private String stderror;

    @SerializedName(ApiConstants.EXITCODE)
    @Param(description = "the command execution return code.")
    private String exitCode;

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderror() {
        return stderror;
    }

    public void setStderror(String stderror) {
        this.stderror = stderror;
    }

    public String getStdout() {
        return stdout;
    }

    public String getExitCode() {
        return exitCode;
    }

    public void setExitCode(String exitCode) {
        this.exitCode = exitCode;
    }

}
