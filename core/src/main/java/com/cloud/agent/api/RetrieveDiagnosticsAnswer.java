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


package com.cloud.agent.api;

import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.common.base.Strings;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class RetrieveDiagnosticsAnswer extends Answer {
    public static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsAnswer.class);

    public RetrieveDiagnosticsAnswer(NetworkElementCommand cmd, boolean result, String details) {
        super(cmd, result, details);
    }

    public Map<String, String> getResultDetails() {
        final Map<String, String> resultDetailsMap = new HashMap<>();
        if (result == true && !Strings.isNullOrEmpty(details)) {
            final String[] parseDetails = details.split("&&");
            if (parseDetails.length >= 3) {
                resultDetailsMap.put(ApiConstants.STDOUT, parseDetails[0].trim());
                resultDetailsMap.put(ApiConstants.STDERR, parseDetails[1].trim());
                resultDetailsMap.put(ApiConstants.EXITCODE, String.valueOf(parseDetails[2].trim()));
                return resultDetailsMap;
            } else {
                throw new CloudRuntimeException("Unsupported diagnostics command type.");
            }
        } else {
            throw new CloudRuntimeException("Command execution failed ->" + details);
        }
    }

}
