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
import org.apache.cloudstack.api.Coffee;
import org.apache.cloudstack.api.CoffeeManager;
import org.apache.cloudstack.api.response.CoffeeResponse;
import org.apache.cloudstack.acl.RoleType;

import javax.inject.Inject;
import java.util.Map;

@APICommand(
        name = "updateCoffee",
        description = "Updates an existing coffee order",
        responseObject = CoffeeResponse.class,
        since = "4.23.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User}
)
public class UpdateCoffeeCmd extends BaseAsyncCmd {

    @Inject
    private CoffeeManager coffeeManager;

    @Parameter(name = ApiConstants.ID,
            type = CommandType.LONG,
            required = true,
            description = "the ID of the coffee order")
    private Long id;

    @Parameter(name = "size",
            type = CommandType.STRING,
            required = false,
            description = "new size of coffee (SMALL, MEDIUM, LARGE)")
    private String size;

    @Parameter(name = "details",
            type = CommandType.MAP,
            required = false,
            description = "updated details for the coffee order")
    private Map<String, String> details;

    @Override
    public void execute() {
        Coffee coffee = coffeeManager.updateCoffee(this);

        CoffeeResponse response = new CoffeeResponse();
        response.setId(coffee.getUuid());
        response.setName(coffee.getName());
        response.setOffering(coffee.getOffering().name());
        response.setSize(coffee.getSize().name());
        response.setState(coffee.getState().name());
        response.setObjectName("coffee");
        response.setResponseName(getCommandName());

        setResponseObject(response);
    }

    @Override
    public String getEventType() {
        return "COFFEE.UPDATE";
    }

    @Override
    public String getEventDescription() {
        return "Updating coffee: " + id;
    }

    @Override
    public long getEntityOwnerId() {
        return com.cloud.user.Account.ACCOUNT_ID_SYSTEM;
    }

    public Long getId() {
        return id;
    }

    public String getSize() {
        return size;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}