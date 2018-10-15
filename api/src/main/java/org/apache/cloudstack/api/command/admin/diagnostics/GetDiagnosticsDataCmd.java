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

import java.util.List;

import javax.inject.Inject;

import com.cloud.event.EventTypes;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.GetDiagnosticsDataResponse;
import org.apache.cloudstack.api.response.SystemVmResponse;
import org.apache.cloudstack.api.response.VolumeResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.diagnostics.DiagnosticsService;
import org.apache.log4j.Logger;

@APICommand(name = GetDiagnosticsDataCmd.APINAME,
        responseObject = GetDiagnosticsDataResponse.class,
        entityType = {VirtualMachine.class},
        responseHasSensitiveInfo = false,
        requestHasSensitiveInfo = false,
        description = "Get diagnostics data files",
        since = "4.12.0.0",
        authorized = {RoleType.Admin})
public class GetDiagnosticsDataCmd extends BaseAsyncCmd {
    private static final Logger LOGGER = Logger.getLogger(GetDiagnosticsDataCmd.class);
    public static final String APINAME = "getDiagnosticsData";

    @Inject
    private DiagnosticsService diagnosticsService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.TARGET_ID,
            type = BaseCmd.CommandType.UUID,
            entityType = SystemVmResponse.class,
            required = true,
            validations = {ApiArgValidator.PositiveNumber},
            description = "The ID of the system VM instance to retrieve diagnostics data files from")
    private Long id;

    @Parameter(name = ApiConstants.DETAILS,
            type = BaseCmd.CommandType.LIST,
            collectionType = BaseCmd.CommandType.STRING,
            description = "Optional list of additional files to retrieve and must correspond with the diagnostics file dataTypeList. Can be specified as file name or path.")
    private List<String> additionalFilesList;

    @Parameter(name = ApiConstants.TYPE,
            type = BaseCmd.CommandType.LIST,
            collectionType = BaseCmd.CommandType.STRING,
            description = "The diagnostics data dataTypeList required, examples are dhcp, iptables, dns or log files. Defaults are taken from the database if none has been provided.")
    private List<String> dataTypeList;

    @Parameter(
            name = ApiConstants.VOLUME_ID,
            type = CommandType.UUID,
            entityType = VolumeResponse.class,
            validations = {ApiArgValidator.PositiveNumber},
            description = "The ID of the volume to be temporarily mounted on system VM to seed diagnostics data files",
            required = true)
    private Long volumeId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public List<String> getAdditionalFilesList() {
        return additionalFilesList;
    }

    public List<String> getDataTypeList() {
        return dataTypeList;
    }

    public Long getVolumeId() {
        return volumeId;
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
        String result = diagnosticsService.getDiagnosticsDataCommand(this);
        GetDiagnosticsDataResponse response = new GetDiagnosticsDataResponse();
        response.setUrl(result);
        response.setObjectName("diagnostics-data");
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getEventType() {
        VirtualMachine.Type vmType = _entityMgr.findById(VirtualMachine.class, getId()).getType();
        String eventType = "";
        switch (vmType) {
            case ConsoleProxy:
                eventType = EventTypes.EVENT_PROXY_DIAGNOSTICS;
                break;
            case SecondaryStorageVm:
                eventType = EventTypes.EVENT_SSVM_DIAGNOSTICS;
                break;
            case DomainRouter:
                eventType = EventTypes.EVENT_ROUTER_DIAGNOSTICS;
                break;
        }
        return eventType;
    }

    @Override
    public String getEventDescription() {
        return "Getting diagnostics data files from system vm: " + this._uuidMgr.getUuid(VirtualMachine.class, getId());
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.SystemVm;
    }
}