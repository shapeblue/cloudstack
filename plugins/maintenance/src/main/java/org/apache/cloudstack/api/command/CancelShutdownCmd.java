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

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;

import com.cloud.user.Account;

import org.apache.cloudstack.api.response.ManagementServerMaintenanceResponse;
import org.apache.cloudstack.acl.RoleType;

@APICommand(name = CancelShutdownCmd.APINAME,
            description = "Cancels a triggered shutdown",
            since = "4.19.0",
            responseObject = ManagementServerMaintenanceResponse.class,
            requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
            authorized = {RoleType.Admin})

public class CancelShutdownCmd extends BaseMSMaintenanceActionCmd {

    public static final String APINAME = "cancelShutdown";

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ManagementServerMaintenanceResponse response = managementServerMaintenanceManager.cancelShutdown(this);
        response.setResponseName(getCommandName());
        response.setObjectName("cancelshutdown");
        setResponseObject(response);
    }
}
