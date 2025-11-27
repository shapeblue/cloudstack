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
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.Coffee;
import org.apache.cloudstack.api.CoffeeManager;
import org.apache.cloudstack.api.response.CoffeeResponse;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.context.CallContext;

import javax.inject.Inject;
import java.util.Map;

@APICommand(
        name = CreateCoffeeCmd.APINAME,
        description = "Creates a new coffee order",
        responseObject = CoffeeResponse.class,
        since = "4.23.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User}
)
public class CreateCoffeeCmd extends BaseAsyncCreateCmd {
    public static final String APINAME = "createCoffee";

    @Inject
    private CoffeeManager coffeeManager;

    private Coffee coffee;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NAME,
            type = CommandType.STRING,
            required = true,
            description = "name of the coffee order")
    private String name;

    @Parameter(name = "offering",
            type = CommandType.STRING,
            required = true,
            description = "type of coffee (ESPRESSO, CAPPUCCINO, MOCHA, LATTE)")
    private String offering;

    @Parameter(name = "size",
            type = CommandType.STRING,
            required = true,
            description = "size of coffee (SMALL, MEDIUM, LARGE)")
    private String size;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getName() {
        return name;
    }

    public String getOffering() {
        return offering;
    }

    public String getSize() {
        return size;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void create() {
        coffee = coffeeManager.createCoffee(this);

        if (coffee != null) {
            setEntityId(coffee.getId());
            setEntityUuid(coffee.getUuid());
        }
    }

    @Override
    public void execute() {
        CoffeeResponse response = coffeeManager.createCoffeeResponse(coffee);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getEventType() {
        return "COFFEE.CREATE";
    }

    @Override
    public String getEventDescription() {
        return "Creating coffee: " + name;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }
}
