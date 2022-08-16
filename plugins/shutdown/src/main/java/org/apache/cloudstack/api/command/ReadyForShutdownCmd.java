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

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.response.ReadyForShutdownResponse;
import org.apache.cloudstack.shutdown.ShutdownManager;
import org.apache.log4j.Logger;
import org.apache.cloudstack.acl.RoleType;

import com.cloud.user.Account;

@APICommand(name = ReadyForShutdownCmd.APINAME,
            description = "My API short description here",
            responseObject = ReadyForShutdownResponse.class,
            since = "4.xx.yy",
            requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
            authorized = {RoleType.Admin})
public class ReadyForShutdownCmd extends BaseCmd {
    public static final Logger LOG = Logger.getLogger(ReadyForShutdownCmd.class);
    public static final String APINAME = "readyForShutdown";

    @Inject
    private ShutdownManager shutdownManager;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() {
        // logic to handle API request

        final ReadyForShutdownResponse response = new ReadyForShutdownResponse(shutdownManager.isReadyForShutdown());
        // logic to setup the API response object
        response.setResponseName(getCommandName());
        response.setObjectName("readyforshutdown");
        setResponseObject(response);
    }
}