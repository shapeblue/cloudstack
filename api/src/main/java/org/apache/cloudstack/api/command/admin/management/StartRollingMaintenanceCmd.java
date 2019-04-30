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
package org.apache.cloudstack.api.command.admin.management;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

@APICommand(name = StartRollingMaintenanceCmd.APINAME, description = "Start rolling maintenance",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin})
public class StartRollingMaintenanceCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListMgmtsCmd.class.getName());

    public static final String APINAME = "startRollingMaintenance";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ClusterResponse.class, description = "the id of the cluster")
    private Long id;

    @Parameter(name = "maintenance", type = CommandType.BOOLEAN, description = "if host should be put in maintenance mode")
    private Boolean maintenance;

    @Parameter(name = ApiConstants.FORCED, type = CommandType.BOOLEAN, description = "if rolling mechanism should continue in case of an error")
    private Boolean forced;

    @Parameter(name = "payload", type = CommandType.STRING, description = "arbitrary command")
    private String command;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public Boolean getMaintenance() {
        return maintenance;
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
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public void execute() {
        SuccessResponse response = new SuccessResponse();
        response.setSuccess(_resourceService.rollingMaintenance(this));
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }
}
