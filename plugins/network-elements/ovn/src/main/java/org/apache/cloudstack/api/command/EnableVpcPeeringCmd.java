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
package org.apache.cloudstack.api.command;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnPeeringService;

import javax.inject.Inject;

@APICommand(name = EnableVpcPeeringCmd.APINAME,
        description = "Enables a VPC peering group. Re-applies the OVN data-plane (routes, NAT bypass, ACLs) for every member of the group.",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class EnableVpcPeeringCmd extends BaseCmd {
    public static final String APINAME = "enableVpcPeering";

    @Inject
    OvnPeeringService ovnPeeringService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING,
            required = true, description = "The UUID of the VPC peering group (or any member peering UUID)")
    private String id;

    public String getId() {
        return id;
    }

    @Override
    public void execute() throws ServerApiException {
        boolean result = ovnPeeringService.enableVpcPeering(this);
        if (!result) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to enable VPC peering");
        }
        SuccessResponse response = new SuccessResponse(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
