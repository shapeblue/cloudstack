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
package org.apache.cloudstack.api.command.admin.template;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.GetSystemVMTemplateDefaultURLResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.log4j.Logger;

@APICommand(name = "getSystemVMTemplateDefaultURL", description = "Gets the system virtual machine template's default download URL.",
        responseObject = GetSystemVMTemplateDefaultURLResponse.class, authorized = {RoleType.Admin})
public class GetSystemVMTemplateDefaultURLCmd extends BaseCmd {

    public static final Logger LOGGER = Logger.getLogger(GetSystemVMTemplateDefaultURLCmd.class.getName());
    private static final String NAME = "getsystemvmtemplatedefaulturlresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.VERSION, type = CommandType.STRING,
            entityType = TemplateResponse.class, required = false, description = "The CloudStack version for which to get System VM Template URL.")
    private String version;

    @Parameter(name = ApiConstants.HYPERVISOR, type = CommandType.STRING, entityType = TemplateResponse.class, required = true, description = "The hypervisor for which to get System VM Template URL.")
    private String hypervisor;

    public String getVersion() {
        return version;
    }

    public String getHypervisor() {
        return hypervisor;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
//        VirtualMachineTemplate template = _entityMgr.findById(VirtualMachineTemplate.class, 1);
//        String url = template.getUrl();
//        if (url != null && !url.isEmpty()) {
//            setResponseObject(new GetSystemVMTemplateDefaultURLResponse(getCommandName(), url));
//        } else {
//            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to find URL for version '%s' and hypervisor '%s'", version, hypervisor));
//        }
        GetSystemVMTemplateDefaultURLResponse response = _templateService.getSystemVMTemplateDefaultURL(this);
        if (response != null) {
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to find URL for version '%s' and hypervisor '%s'", version, hypervisor));
        }
    }

    @Override
    public String getCommandName() {
        return NAME;
    }

    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.Template;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
