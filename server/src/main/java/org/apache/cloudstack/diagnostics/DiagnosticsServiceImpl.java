//
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
package org.apache.cloudstack.diagnostics;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.command.admin.diagnostics.ExecuteDiagnosticsCmd;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiagnosticsServiceImpl extends ManagerBase implements PluggableService, DiagnosticsService {
    private static final Logger LOGGER = Logger.getLogger(DiagnosticsServiceImpl.class);

    @Inject
    private RouterControlHelper routerControlHelper;
    @Inject
    private AgentManager agentManager;
    @Inject
    private VMInstanceDao instanceDao;
    @Inject
    private VirtualMachineManager vmManager;
    @Inject
    private NetworkOrchestrationService networkManager;

    @Override
    public Map<String, String> runDiagnosticsCommand(final ExecuteDiagnosticsCmd cmd) throws AgentUnavailableException, InvalidParameterValueException {
        final Long vmId = cmd.getId();
        final String cmdType = cmd.getType().getValue();
        final String cmdAddress = cmd.getAddress();
        final String optionalArguments = cmd.getOptionalArguments();
        final VMInstanceVO vmInstance = instanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);

        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system vm with id " + vmId);
        }
        final Long hostId = vmInstance.getHostId();

        if (hostId == null) {
            throw new CloudRuntimeException("Unable to find host for virtual machine instance: " + vmInstance.getInstanceName());
        }

        final String shellCmd = prepareShellCmd(String.format("%s %s %s", cmdType, cmdAddress, optionalArguments));

        final DiagnosticsCommand command = new DiagnosticsCommand(shellCmd, vmManager.getExecuteInSequence(vmInstance.getHypervisorType()));
        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);
        command.setAccessDetail(accessDetails);

        Answer answer =  agentManager.easySend(hostId, command);
        final Map<String, String> detailsMap = ((DiagnosticsAnswer) answer).getExecutionDetails();

        if (!detailsMap.isEmpty()) {
            return detailsMap;
        } else {
            throw new CloudRuntimeException("Error occurred when executing diagnostics command on remote target: " + answer.getDetails());
        }
    }

    protected String prepareShellCmd(CharSequence cmdArguments) {
        List<String> tokens = new ArrayList<>();
        boolean escaping = false;
        boolean quoting = false;
        char quoteChar = ' ';
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < cmdArguments.length(); i++) {
            char c = cmdArguments.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
            } else if (c == '\\' && !(quoting && quoteChar == '\'')) {
                escaping = true;
            } else if (quoting && c == quoteChar) {
                quoting = false;
            } else if (!quoting && (c == '\'' || c == '"')) {
                quoting = true;
                quoteChar = c;
            } else if (!quoting && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return String.join(" ", tokens);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ExecuteDiagnosticsCmd.class);
        return cmdList;
    }
}