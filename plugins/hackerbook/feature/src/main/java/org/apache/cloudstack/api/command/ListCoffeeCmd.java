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
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.response.CoffeeResponse;
import org.apache.cloudstack.api.response.ListResponse;

import java.util.ArrayList;
import java.util.List;

@APICommand(
        name = "listCoffees",
        description = "Lists all coffees",
        responseObject = CoffeeResponse.class,
        since = "4.23.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false
)
public class ListCoffeeCmd extends BaseListCmd {

    @Override
    public void execute() {
        List<CoffeeResponse> coffeeList = new ArrayList<>();

        CoffeeResponse espresso = new CoffeeResponse();
        espresso.setId("1");
        espresso.setName("Morning Espresso");
        espresso.setOffering("Espresso");
        espresso.setSize("SMALL");
        espresso.setState("Brewed");
        espresso.setObjectName("coffee");

        CoffeeResponse latte = new CoffeeResponse();
        latte.setId("2");
        latte.setName("Cloud Latte");
        latte.setOffering("Latte");
        latte.setSize("LARGE");
        latte.setState("Created");
        latte.setObjectName("coffee");

        coffeeList.add(espresso);
        coffeeList.add(latte);

        ListResponse<CoffeeResponse> response = new ListResponse<>();
        response.setResponses(coffeeList, coffeeList.size());
        response.setResponseName(getCommandName());
        response.setObjectName("coffee");
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return com.cloud.user.Account.ACCOUNT_ID_SYSTEM;
    }
}
