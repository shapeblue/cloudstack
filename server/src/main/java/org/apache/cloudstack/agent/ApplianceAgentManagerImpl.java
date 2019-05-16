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

package org.apache.cloudstack.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.admin.agent.LivePatchCmd;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.virtualappliance.ApplianceAgentRpcClient;
import org.apache.cloudstack.virtualappliance.protobuf.PingRequest;
import org.apache.cloudstack.virtualappliance.protobuf.PingResponse;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.Networks;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;

public class ApplianceAgentManagerImpl extends ManagerBase implements PluggableService, ApplianceAgentService {

    @Inject
    VMInstanceDao vmInstanceDao;

    @Inject
    private NetworkOrchestrationService networkManager;

    @Override
    public List<Class<?>> getCommands() {
        return Arrays.asList(LivePatchCmd.class);
    }

    @Override
    public String livePatch(LivePatchCmd cmd) {
        final Long vmId = cmd.getId();
        final String message = cmd.getToken();
        final VMInstanceVO vmInstance = vmInstanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);

        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system vm with the requested ID)");
        }

        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);
        final String applianceManagementAddress = accessDetails.get(Networks.TrafficType.Public.name());

        if (Strings.isNullOrEmpty(applianceManagementAddress)) {
            throw new CloudRuntimeException("Unable to set system vm management IP for system vm");
        }

        // TODO: exception handling, checks etc.
        ApplianceAgentRpcClient client = new ApplianceAgentRpcClient(applianceManagementAddress, 8200);
        PingRequest request = PingRequest.newBuilder()
                .setMessage(message)
                .build();
        PingResponse response = client.ping(request);
        return response.getMessage();
    }
}
