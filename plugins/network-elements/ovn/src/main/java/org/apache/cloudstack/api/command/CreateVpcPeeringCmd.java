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

import com.cloud.network.element.OvnVpcPeeringVO;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.VpcPeeringResponse;
import org.apache.cloudstack.api.response.VpcResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnPeeringService;

import javax.inject.Inject;

@APICommand(name = CreateVpcPeeringCmd.APINAME,
        description = "Creates a peering connection between two OVN-backed VPCs. If the peer VPC already belongs to a peering group, the calling VPC joins that group (mesh topology).",
        responseObject = VpcPeeringResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class CreateVpcPeeringCmd extends BaseCmd {
    public static final String APINAME = "createVpcPeering";

    @Inject
    OvnPeeringService ovnPeeringService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING,
            required = true, description = "Name for the VPC peering group")
    private String name;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING,
            description = "Description for the VPC peering group")
    private String description;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class,
            required = true, description = "The ID of the VPC to peer")
    private Long vpcId;

    @Parameter(name = "peervpcid", type = CommandType.UUID, entityType = VpcResponse.class,
            required = true, description = "The ID of the peer VPC. If it already belongs to a peering group, the calling VPC joins that group.")
    private Long peerVpcId;

    @Parameter(name = "aclid", type = CommandType.UUID, entityType = org.apache.cloudstack.api.response.NetworkACLResponse.class,
            description = "The ID of a VPC Network ACL list to apply to this peering membership. Controls what traffic is allowed through the peering connection.")
    private Long aclId;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getVpcId() {
        return vpcId;
    }

    public Long getPeerVpcId() {
        return peerVpcId;
    }

    public Long getAclId() {
        return aclId;
    }

    @Override
    public void execute() throws ServerApiException {
        OvnVpcPeeringVO peering = ovnPeeringService.createVpcPeering(this);
        if (peering == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create VPC peering");
        }
        VpcPeeringResponse response = ovnPeeringService.createVpcPeeringResponse(peering);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
