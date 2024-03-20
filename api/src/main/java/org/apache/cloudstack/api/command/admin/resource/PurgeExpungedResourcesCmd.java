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


import java.util.Date;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;

@APICommand(name = "purgeExpungedResources",
        description = "Purge expunged resources",
        responseObject = SuccessResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = true,
        authorized = {RoleType.Admin},
        since = "4.12.0")
public class PurgeExpungedResourcesCmd extends BaseAsyncCmd {

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.RESOURCE_TYPE, type = BaseCmd.CommandType.STRING,
            description = "the type of the resource which need to be purged")
    private String resourceType;

    @Parameter(name = ApiConstants.BATCH_SIZE, type = CommandType.INTEGER,
            description = "the size of batch used during purging")
    private Integer batchSize;

    @Parameter(name = ApiConstants.START_DATE,
            type = CommandType.DATE,
            description = "the start date range of the resources used for purging " +
                    "(use format \"yyyy-MM-dd\" or \"yyyy-MM-dd HH:mm:ss\")")
    private Date startDate;

    @Parameter(name = ApiConstants.END_DATE,
            type = CommandType.DATE,
            description = "the start date range of the resources used for purging " +
                    "(use format \"yyyy-MM-dd\" or \"yyyy-MM-dd HH:mm:ss\")")
    private Date endDate;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////


    public String getResourceType() {
        return resourceType;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }

    @Override
    public String getEventType() {
        return null;
    }

    @Override
    public String getEventDescription() {
        return null;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        try {
            boolean result = true; // ToDo: Implement
            if (result) {
                SuccessResponse response = new SuccessResponse(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to purge resources");
            }
        } catch (Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getLocalizedMessage());
        }
    }
}
