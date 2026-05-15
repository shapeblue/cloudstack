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
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.MeshNetworkResponse;
import org.apache.cloudstack.api.response.VpcResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnMeshNetworkService;

import javax.inject.Inject;
import java.util.List;

@APICommand(name = ListMeshNetworksCmd.APINAME,
        description = "Lists mesh networks for the calling account. Optionally filter by VPC ID or mesh network.",
        responseObject = MeshNetworkResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class ListMeshNetworksCmd extends BaseListCmd {
    public static final String APINAME = "listMeshNetworks";

    @Inject
    OvnMeshNetworkService ovnMeshNetworkService;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class,
            description = "The ID of the VPC to list mesh network memberships for")
    private Long vpcId;

    @Parameter(name = "meshuuid", type = CommandType.STRING,
            description = "The mesh network UUID to filter by")
    private String meshUuid;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING,
            description = "The mesh network ID (alias of meshuuid; used by AutogenView for the detail view)")
    private String id;

    public Long getVpcId() {
        return vpcId;
    }

    public String getMeshUuid() {
        // id is exposed as the resource identifier of a member "group" so the standard
        // AutogenView /:id/ detail flow works. We aliase it onto meshUuid since both
        // refer to the same member mesh.
        if (meshUuid != null) return meshUuid;
        return id;
    }

    public String getId() {
        return id;
    }

    @Override
    public void execute() throws ServerApiException {
        List<MeshNetworkResponse> responses = ovnMeshNetworkService.listMeshNetworks(this);
        ListResponse<MeshNetworkResponse> listResponse = new ListResponse<>();
        listResponse.setResponses(responses, responses.size());
        listResponse.setResponseName(getCommandName());
        setResponseObject(listResponse);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
