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
package org.apache.cloudstack.api.command.admin.resource;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.resource.RollingMaintenanceService;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;

@APICommand(name = StartRollingMaintenanceCmd.APINAME, description = "Start rolling maintenance",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin})
public class StartRollingMaintenanceCmd extends BaseCmd {

    @Inject
    RollingMaintenanceService rollingMaintenanceService;

    public static final Logger s_logger = Logger.getLogger(StartRollingMaintenanceCmd.class.getName());

    public static final String APINAME = "startRollingMaintenance";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.POD_ID, type = CommandType.UUID,
            entityType = PodResponse.class, description = "the ID of the pod to start maintenance on")
    private Long podId;

    @Parameter(name = ApiConstants.CLUSTER_ID, type = CommandType.UUID,
            entityType = ClusterResponse.class, description = "the ID of the cluster to start maintenance on")
    private Long clusterId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID,
            entityType = ZoneResponse.class, description = "the ID of the zone to start maintenance on")
    private Long zoneId;

    @Parameter(name = ApiConstants.HOST_ID, type = CommandType.UUID,
            entityType = HostResponse.class, description = "the ID of the host to start maintenance on")
    private Long hostId;

    @Parameter(name = ApiConstants.FORCED, type = CommandType.BOOLEAN,
            description = "if rolling mechanism should continue in case of an error")
    private Boolean forced;

    @Parameter(name = "payload", type = CommandType.STRING,
            required = true, description = "the command to execute while hosts are on maintenance")
    private String command;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getPodId() {
        return podId;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getHostId() {
        return hostId;
    }

    public Boolean getForced() {
        return forced;
    }

    public String getCommand() {
        return command;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        SuccessResponse response = new SuccessResponse();
        response.setSuccess(rollingMaintenanceService.startRollingMaintenance(this));
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }
}
