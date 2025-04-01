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
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.RemoteRegionResponse;
import com.cloud.user.Account;

@APICommand(name = "listRemoteRegions", description = "Lists Remote Regions", responseObject = RemoteRegionResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListRemoteRegionsCmd extends BaseListCmd {
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = RemoteRegionResponse.class,
            description = "ID of the remote region")
    private String id;

    @Parameter(name = ApiConstants.END_POINT, type = CommandType.STRING, description = "Endpoint of the remote region")
    private String endpoint;

    public String getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        ListResponse<RemoteRegionResponse> regions = remoteRegionManager.listRemoteRegions(getId(), getEndpoint());
        setResponseObject(regions);
    }
}
