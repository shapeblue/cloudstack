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
package org.apache.cloudstack.api.command.user.remoteregion;

import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RemoteRegionResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.remoteregion.RemoteRegionManager;
import com.cloud.user.Account;

@APICommand(name = "remoteExecute",
    description = "Executes an API command on a remote region",
    responseObject = SuccessResponse.class,  // Generic response since we don't know the response type in advance
    requestHasSensitiveInfo = true,
    responseHasSensitiveInfo = true)
public class RemoteExecuteCmd extends BaseCmd {

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = RemoteRegionResponse.class,
        required = true, description = "ID of the remote region")
    private String regionId;

    @Parameter(name = "command", type = CommandType.STRING, required = true,
        description = "The API command to execute on the remote region")
    private String command;

    @Parameter(name = "parameters", type = CommandType.MAP,
        description = "Parameters for the API command as key-value pairs")
    private Map<String, String> parameters;

    @Inject
    private RemoteRegionManager remoteRegionManager;

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public void execute() {
        try {
            Object result = remoteRegionManager.executeRemoteCommand(getRegionId(), getCommand(), getParameters());
            setResponseObject(result);
        } catch (Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR,
                "Failed to execute command on remote region: " + e.getMessage());
        }
    }
}
