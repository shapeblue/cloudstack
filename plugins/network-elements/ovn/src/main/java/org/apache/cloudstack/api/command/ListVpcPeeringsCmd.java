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
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.VpcPeeringResponse;
import org.apache.cloudstack.api.response.VpcResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnPeeringService;

import javax.inject.Inject;
import java.util.List;

@APICommand(name = ListVpcPeeringsCmd.APINAME,
        description = "Lists VPC peerings for the calling account. Optionally filter by VPC ID or peering group.",
        responseObject = VpcPeeringResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User},
        since = "4.23.0")
public class ListVpcPeeringsCmd extends BaseCmd {
    public static final String APINAME = "listVpcPeerings";

    @Inject
    OvnPeeringService ovnPeeringService;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class,
            description = "The ID of the VPC to list peerings for")
    private Long vpcId;

    @Parameter(name = "groupuuid", type = CommandType.STRING,
            description = "The peering group UUID to filter by")
    private String groupUuid;

    public Long getVpcId() {
        return vpcId;
    }

    public String getGroupUuid() {
        return groupUuid;
    }

    @Override
    public void execute() throws ServerApiException {
        List<VpcPeeringResponse> responses = ovnPeeringService.listVpcPeerings(this);
        ListResponse<VpcPeeringResponse> listResponse = new ListResponse<>();
        listResponse.setResponses(responses, responses.size());
        listResponse.setResponseName(getCommandName());
        setResponseObject(listResponse);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
