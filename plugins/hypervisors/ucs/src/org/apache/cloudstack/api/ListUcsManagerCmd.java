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
//
// Automatically generated by addcopyright.py at 02/28/2013
// regarding copyright ownership.  The ASF licenses this file
// "License"); you may not use this file except in compliance
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
package org.apache.cloudstack.api;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.UcsManagerResponse;
import org.apache.cloudstack.api.response.ZoneResponse;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.ucs.manager.UcsManager;
import com.cloud.user.Account;

@APICommand(name = "listUcsManagers", description = "List ucs manager", responseObject = UcsManagerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListUcsManagerCmd extends BaseListCmd {
    public static final Logger s_logger = LogManager.getLogger(ListUcsManagerCmd.class);

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, description = "the zone id", entityType = ZoneResponse.class)
    private Long zoneId;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = UcsManagerResponse.class, description = "the ID of the ucs manager")
    private Long id;

    @Inject
    private UcsManager mgr;

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
        ResourceAllocationException, NetworkRuleConflictException {
        try {
            ListResponse<UcsManagerResponse> response = mgr.listUcsManager(this);
            response.setResponseName(getCommandName());
            response.setObjectName("ucsmanager");
            this.setResponseObject(response);
        } catch (Exception e) {
            s_logger.warn("Exception: ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return "listucsmanagerreponse";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
