/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudstack.api.command;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.acl.RoleType;

import java.util.List;

@APICommand(
        name = "removeCoffee",
        description = "Removes a coffee order or multiple coffee orders",
        responseObject = SuccessResponse.class,
        since = "4.23.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User}
)
public class RemoveCoffeeCmd extends BaseAsyncCmd {

    @Parameter(name = ApiConstants.ID,
            type = CommandType.STRING,
            required = false,
            description = "the ID of the coffee order to remove")
    private String id;

    @Parameter(name = "ids",
            type = CommandType.LIST,
            collectionType = CommandType.STRING,
            required = false,
            description = "the IDs of coffee orders to remove")
    private List<String> ids;

    @Override
    public void execute() {
        // Hardcoded success response for now
        SuccessResponse response = new SuccessResponse();

        if (id != null) {
            response.setSuccess(true);
            response.setDisplayText("Successfully removed coffee order: " + id);
        } else if (ids != null && !ids.isEmpty()) {
            response.setSuccess(true);
            response.setDisplayText("Successfully removed " + ids.size() + " coffee orders");
        } else {
            response.setSuccess(false);
            response.setDisplayText("No coffee ID provided");
        }

        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getEventType() {
        return "COFFEE.REMOVE";
    }

    @Override
    public String getEventDescription() {
        if (id != null) {
            return "Removing coffee: " + id;
        } else if (ids != null) {
            return "Removing " + ids.size() + " coffees";
        }
        return "Removing coffee";
    }

    @Override
    public long getEntityOwnerId() {
        return com.cloud.user.Account.ACCOUNT_ID_SYSTEM;
    }

    public String getId() {
        return id;
    }

    public List<String> getIds() {
        return ids;
    }
}