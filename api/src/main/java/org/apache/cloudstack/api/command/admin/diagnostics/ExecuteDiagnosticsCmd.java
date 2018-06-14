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
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ExecuteDiagnosticsResponse;
import org.apache.cloudstack.api.response.SystemVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.diagnostics.DiagnosticsService;
import org.apache.cloudstack.diagnostics.DiagnosticsType;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.Map;
import java.util.regex.Pattern;

@APICommand(name = ExecuteDiagnosticsCmd.APINAME, responseObject = ExecuteDiagnosticsResponse.class, entityType = {VirtualMachine.class},
        responseHasSensitiveInfo = false,
        requestHasSensitiveInfo = false,
        description = "Execute network-utility command (ping/arping/tracert) on system VMs remotely",
        authorized = {RoleType.Admin},
        since = "4.12.0.0")
public class ExecuteDiagnosticsCmd extends BaseCmd {
    private static final Logger LOGGER = Logger.getLogger(ExecuteDiagnosticsCmd.class);
    public static final String APINAME = "executeDiagnostics";

    @Inject
    private DiagnosticsService diagnosticsService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, required = true, entityType = SystemVmResponse.class,
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
            LOGGER.warn("An Invalid diagnostics command type passed: " + type);
            throw new IllegalArgumentException(type + " Is not a valid diagnostics command type. ");
        }
        return diagnosticsType;
    }

    public String getOptionalArguments() {
        final String EMPTY_STRING = "";

        if (optionalArguments == null || optionalArguments.isEmpty()) {
            return EMPTY_STRING;
        }
        final String regex = "^[\\w\\-\\s]+$";
        final Pattern pattern = Pattern.compile(regex);
        final boolean hasInvalidChar = pattern.matcher(optionalArguments).find();
        if (!hasInvalidChar) {
            LOGGER.error("An Invalid character has been passed as an optional parameter");
            throw new IllegalArgumentException("Illegal argument passed as optional parameter.");
        }
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
        ExecuteDiagnosticsResponse response = new ExecuteDiagnosticsResponse();
        try {
            final Map<String, String> answerMap = diagnosticsService.runDiagnosticsCommand(this);
            if (answerMap != null || !answerMap.isEmpty()) {
                response.setStdout(answerMap.get("STDOUT"));
                response.setStderr(answerMap.get("STDERR"));
                response.setExitCode(answerMap.get("EXITCODE"));
                response.setResult(answerMap.get("SUCCESS"));
                response.setObjectName("diagnostics");
                response.setResponseName(getCommandName());
                this.setResponseObject(response);
            }
        } catch (ServerApiException e) {
            LOGGER.warn("Exception occurred while executing remote diagnostics command: ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        } catch (CloudRuntimeException ex) {
            LOGGER.warn("Error occurred while executing diagnostics command: ");
            throw new CloudRuntimeException("Error occurred while executing diagnostics command: " + ex);
        }
    }
}