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
import org.apache.cloudstack.service.OvnMeshNetworkService;

import javax.inject.Inject;

@APICommand(name = DisableMeshNetworkCmd.APINAME,
        description = "Disables a VPC mesh network. Removes the OVN data-plane (routes, NAT bypass, ACLs) from every member while keeping records and topology so it can be re-enabled.",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class DisableMeshNetworkCmd extends BaseCmd {
    public static final String APINAME = "disableMeshNetwork";

    @Inject
    OvnMeshNetworkService ovnMeshNetworkService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING,
            required = true, description = "The UUID of the VPC mesh network (or any member UUID)")
    private String id;

    public String getId() {
        return id;
    }

    @Override
    public void execute() throws ServerApiException {
        boolean result = ovnMeshNetworkService.disableMeshNetwork(this);
        if (!result) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to disable mesh network");
        }
        SuccessResponse response = new SuccessResponse(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
