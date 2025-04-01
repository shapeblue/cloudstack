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

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.RemoteRegionResponse;
import com.cloud.user.Account;

@APICommand(name = "addRemoteRegion", description = "Adds a Remote Region", responseObject = RemoteRegionResponse.class,
        requestHasSensitiveInfo = true, responseHasSensitiveInfo = false)
public class AddRemoteRegionCmd extends BaseCmd {
    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, description = "Description of the remote region")
    private String description;

    @Parameter(name = ApiConstants.END_POINT, type = CommandType.STRING, required = true, description = "Remote region endpoint URL")
    private String endpoint;

    @Parameter(name = ApiConstants.API_KEY, type = CommandType.STRING, required = true, description = "API key for the remote region")
    private String apiKey;

    @Parameter(name = ApiConstants.SECRET_KEY, type = CommandType.STRING, required = true, description = "Secret key for the remote region")
    private String secretKey;

    @Parameter(name = ApiConstants.SSL_VERIFICATION, type = CommandType.BOOLEAN, description = "Whether to verify SSL certificate (default: true)")
    private Boolean sslVerify;

    @Parameter(name = ApiConstants.SCOPE, type = CommandType.STRING, description = "Access scope for the remote region")
    private String scope;

    public String getDescription() {
        return description;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public Boolean getSslVerify() {
        return sslVerify;
    }

    public String getScope() {
        return scope;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        RemoteRegionResponse regionResponse = remoteRegionManager.addRemoteRegion(getDescription(), getEndpoint(), getApiKey(),
            getSecretKey(), getSslVerify(), getScope());
        regionResponse.setResponseName(getCommandName());
            setResponseObject(regionResponse);
    }
}
