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

package org.apache.cloudstack.api;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.metrics.MetricsService;
import org.apache.cloudstack.response.ResourceMetricsStatsResponse;

import com.cloud.server.ResourceTag;

@APICommand(name = ListResourcesMetricsStatsCmd.APINAME, description = "Lists VM stats", responseObject = ResourceMetricsStatsResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, since = "4.18.0",
        authorized = {RoleType.Admin,  RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class ListResourcesMetricsStatsCmd extends BaseListCmd {
    public static final String APINAME = "listResourcesUsageHistory";

    @Inject
    private MetricsService metricsService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.RESOURCE_IDS,
            type = BaseCmd.CommandType.LIST,
            required = true,
            collectionType = BaseCmd.CommandType.STRING,
            description = "list of resources for stats to be listed")
    private List<String> resourceIds;

    @Parameter(name = ApiConstants.RESOURCE_TYPE, type = BaseCmd.CommandType.STRING, required = true, description = "type of the resource")
    private String resourceType;

    @Parameter(name = ApiConstants.START_DATE, type = CommandType.DATE, description = "start date to filter resource stats."
            + "Use format \"yyyy-MM-dd hh:mm:ss\")")
    private Date startDate;

    @Parameter(name = ApiConstants.END_DATE, type = CommandType.DATE, description = "end date to filter resource stats."
            + "Use format \"yyyy-MM-dd hh:mm:ss\")")
    private Date endDate;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public List<String> getResourceIds() {
        return resourceIds;
    }

    public ResourceTag.ResourceObjectType getResourceType() {
        return resourceManagerUtil.getResourceType(resourceType);
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public void execute() {
        ListResponse<ResourceMetricsStatsResponse> response = metricsService.searchForResourceMetricsStats(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}