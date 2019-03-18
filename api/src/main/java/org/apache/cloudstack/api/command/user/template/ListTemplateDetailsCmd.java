// Licensedname = "listTemplatePermissions",  to the Apache Software Foundation (ASF) under one
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
package org.apache.cloudstack.api.command.user.template;

import java.util.List;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.TemplateDetailsResponse;

import com.cloud.template.VirtualMachineTemplate;

@APICommand(name = ListTemplateDetailsCmd.APINAME,
        description = "List all public, private, and privileged templates",
        responseObject = TemplateDetailsResponse.class,
        entityType = {VirtualMachineTemplate.class},
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class ListTemplateDetailsCmd extends ListTemplatesCmd {
    public final static String APINAME = "listTemplateDetails";

    /////////////////////////////////////////////////////
    /////////////////// Implementation //////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public void execute() {
        List<TemplateDetailsResponse> detailsResponses = _queryService.listTemplateDetails(this);
        ListResponse<TemplateDetailsResponse> response = new ListResponse<>();
        response.setResponses(detailsResponses, detailsResponses.size());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
