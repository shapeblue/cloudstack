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

package org.apache.cloudstack.api.command.user.fizzbuzz;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.fizzbuzz.FizzBuzzService;

import com.cloud.user.Account;

@APICommand(name = FizzBuzzCmd.APINAME, description = "FizzBuzz service, returns fizz, buzz, or fizzbuzz based on an input number",
        responseObject = FizzBuzzResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, since = "4.15",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class FizzBuzzCmd extends BaseCmd {
    public static final String APINAME = "fizzBuzz";

    @Inject
    private FizzBuzzService fizzBuzzService;

    @Parameter(name = ApiConstants.NUMBER, type = CommandType.LONG, required = true, description = "the input number")
    private Long number;

    @Override
    public void execute() {
        final FizzBuzzResponse response = new FizzBuzzResponse();
        response.setAnswer(fizzBuzzService.fizzBuzz(number));
        response.setObjectName("fizzbuzz");
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
