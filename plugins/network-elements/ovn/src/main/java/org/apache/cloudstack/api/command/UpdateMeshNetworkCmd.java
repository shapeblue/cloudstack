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

import com.cloud.network.element.OvnMeshNetworkVO;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.NetworkACLResponse;
import org.apache.cloudstack.api.response.MeshNetworkResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnMeshNetworkService;

import javax.inject.Inject;

@APICommand(name = UpdateMeshNetworkCmd.APINAME,
        description = "Updates a VPC mesh network membership. Allows changing the Network ACL applied to this mesh network.",
        responseObject = MeshNetworkResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class UpdateMeshNetworkCmd extends BaseCmd {
    public static final String APINAME = "updateMeshNetwork";

    @Inject
    OvnMeshNetworkService ovnMeshNetworkService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING,
            required = true, description = "The UUID of the mesh network to update")
    private String id;

    @Parameter(name = "aclid", type = CommandType.UUID, entityType = NetworkACLResponse.class,
            description = "The ID of a VPC Network ACL list to apply to this mesh network membership. Pass empty or omit to remove the ACL (allow all).")
    private Long aclId;

    public String getId() {
        return id;
    }

    public Long getAclId() {
        return aclId;
    }

    @Override
    public void execute() throws ServerApiException {
        OvnMeshNetworkVO member = ovnMeshNetworkService.updateMeshNetwork(this);
        if (member == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update mesh network");
        }
        MeshNetworkResponse response = ovnMeshNetworkService.createMeshNetworkResponse(member);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
