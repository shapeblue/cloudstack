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
import org.apache.cloudstack.api.response.MeshNetworkResponse;
import org.apache.cloudstack.api.response.VpcResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnMeshNetworkService;

import javax.inject.Inject;

@APICommand(name = CreateMeshNetworkCmd.APINAME,
        description = "Creates a mesh network connecting two OVN-backed members. Each member can be either a VPC or an Isolated guest network; pass either vpcid or networkid (and either peervpcid or peernetworkid). If the peer member already belongs to a mesh network, the calling member joins that mesh (mesh-of-N topology).",
        responseObject = MeshNetworkResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class CreateMeshNetworkCmd extends BaseCmd {
    public static final String APINAME = "createMeshNetwork";

    @Inject
    OvnMeshNetworkService ovnMeshNetworkService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING,
            required = true, description = "Name for the mesh network")
    private String name;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING,
            description = "Description for the mesh network")
    private String description;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class,
            description = "The ID of the VPC member to add. Mutually exclusive with networkid.")
    private Long vpcId;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID,
            entityType = org.apache.cloudstack.api.response.NetworkResponse.class,
            description = "The ID of the Isolated guest network member to add. Mutually exclusive with vpcid.")
    private Long networkId;

    @Parameter(name = "peervpcid", type = CommandType.UUID, entityType = VpcResponse.class,
            description = "The ID of the peer VPC. If it already belongs to a mesh network, the calling member joins that mesh. Mutually exclusive with peernetworkid.")
    private Long peerVpcId;

    @Parameter(name = "peernetworkid", type = CommandType.UUID,
            entityType = org.apache.cloudstack.api.response.NetworkResponse.class,
            description = "The ID of the peer Isolated guest network. If it already belongs to a mesh network, the calling member joins that mesh. Mutually exclusive with peervpcid.")
    private Long peerNetworkId;

    @Parameter(name = "aclid", type = CommandType.UUID, entityType = org.apache.cloudstack.api.response.NetworkACLResponse.class,
            description = "The ID of a Network ACL list to apply to this member's traffic over the mesh network link. For VPC members this must be a VPC ACL list; for Isolated networks it must be a network-scoped ACL.")
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

    public Long getNetworkId() {
        return networkId;
    }

    public Long getPeerVpcId() {
        return peerVpcId;
    }

    public Long getPeerNetworkId() {
        return peerNetworkId;
    }

    public Long getAclId() {
        return aclId;
    }

    @Override
    public void execute() throws ServerApiException {
        OvnMeshNetworkVO member = ovnMeshNetworkService.createMeshNetwork(this);
        if (member == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create mesh network");
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
