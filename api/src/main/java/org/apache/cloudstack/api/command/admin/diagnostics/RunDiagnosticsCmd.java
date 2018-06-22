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
package org.apache.cloudstack.api.command.admin.diagnostics;

import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RunDiagnosticsResponse;
import org.apache.cloudstack.api.response.SystemVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.diagnostics.DiagnosticsService;
import org.apache.cloudstack.diagnostics.DiagnosticsType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

@APICommand(name = RunDiagnosticsCmd.APINAME, responseObject = RunDiagnosticsResponse.class, entityType = {VirtualMachine.class},
        responseHasSensitiveInfo = false,
        requestHasSensitiveInfo = false,
        description = "Execute network-utility command (ping/arping/tracert) on system VMs remotely",
        authorized = {RoleType.Admin},
        since = "4.12.0.0")
public class RunDiagnosticsCmd extends BaseCmd {
    private static final Logger LOGGER = Logger.getLogger(RunDiagnosticsCmd.class);
    public static final String APINAME = "runDiagnostics";

    @Inject
    private DiagnosticsService diagnosticsService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.TARGET_ID, type = CommandType.UUID, required = true, entityType = SystemVmResponse.class,
            validations = {ApiArgValidator.PositiveNumber},
            description = "The ID of the system VM instance to diagnose")
    private Long id;

    @Parameter(name = ApiConstants.IP_ADDRESS, type = CommandType.STRING, required = true,
            validations = {ApiArgValidator.NotNullOrEmpty},
            description = "The IP/Domain address to test connection to")
    private String address;

    @Parameter(name = ApiConstants.TYPE, type = CommandType.STRING, required = true,
            validations = {ApiArgValidator.NotNullOrEmpty},
            description = "The system VM diagnostics type  valid options are: ping, traceroute, arping")
    private String type;

    @Parameter(name = ApiConstants.PARAMS, type = CommandType.STRING,
            authorized = {RoleType.Admin},
            description = "Additional command line options that apply for each command")
    private String optionalArguments;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public DiagnosticsType getType() {
        DiagnosticsType diagnosticsType = DiagnosticsType.getCommand(type);
        if (diagnosticsType == null) {
            throw new IllegalArgumentException(type + " Is not a valid diagnostics command type. ");
        }
        return diagnosticsType;
    }

    public String getOptionalArguments() {
        return optionalArguments;
    }

    /////////////////////////////////////////////////////
    /////////////////// Implementation //////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        Account account = CallContext.current().getCallingAccount();
        if (account != null) {
            return account.getId();
        }
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException {
        RunDiagnosticsResponse response = new RunDiagnosticsResponse();
        try {
            final Map<String, String> answerMap = diagnosticsService.runDiagnosticsCommand(this);
            if (CollectionUtils.isNotEmpty(Collections.singleton(answerMap))) {
                response.setStdout(answerMap.get(ApiConstants.STDOUT));
                response.setStderr(answerMap.get(ApiConstants.STDERR));
                response.setExitCode(answerMap.get(ApiConstants.EXITCODE));
                response.setObjectName("diagnostics");
                response.setResponseName(getCommandName());
                this.setResponseObject(response);
            }
        } catch (ServerApiException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}