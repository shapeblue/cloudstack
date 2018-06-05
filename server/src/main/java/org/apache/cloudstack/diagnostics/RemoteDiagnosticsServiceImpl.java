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
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.command.admin.diagnostics.RemoteDiagnosticsCmd;
import org.apache.cloudstack.api.response.RemoteDiagnosticsResponse;
import org.apache.cloudstack.diangosis.RemoteDiagnosticsService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RemoteDiagnosticsServiceImpl extends ManagerBase implements PluggableService, RemoteDiagnosticsService, Configurable {
    private static final Logger s_logger = Logger.getLogger(RemoteDiagnosticsServiceImpl.class);

    @Inject
    private RouterControlHelper routerControlHelper;
    @Inject
    private AgentManager agentManager;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private ConfigurationDao configurationDao;



    @Override
    public RemoteDiagnosticsResponse executeDiagnosticsToolInSystemVm(final RemoteDiagnosticsCmd cmd) throws AgentUnavailableException,
            InvalidParameterValueException {
        final Long systemVmId = cmd.getId();
        final VMInstanceVO systemVm = vmInstanceDao.findByIdTypes(systemVmId, VirtualMachine.Type.ConsoleProxy,
                VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);
        if (systemVm == null){
            s_logger.error("Invalid system VM id provided " + systemVmId);
            throw new InvalidParameterValueException("Unable to find a system virtual machine with id " + systemVmId);
        }

        final Long hostId = systemVm.getHostId();
        if (hostId == null){
            s_logger.warn("Unable to find host for virtual machine instance " + systemVm.getInstanceName());
            throw new CloudRuntimeException("Unable to find host for virtual machine instance " + systemVm.getInstanceName());
        }

        final String cmdType = cmd.getType();
        final String cmdAddress = cmd.getAddress();
        final String optionalArgunments = cmd.getOptionalArguments();

        String remoteCommand = setupRemoteCommand(cmdType, cmdAddress, optionalArgunments);

        final ExecuteDiagnosticsCommand command = new ExecuteDiagnosticsCommand(remoteCommand);
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(systemVm.getId()));
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, systemVm.getInstanceName());

        Answer origAnswer;
        try{
            origAnswer = agentManager.send(hostId, command);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            throw new AgentUnavailableException("Unable to send commands to virtual machine ", hostId, e);
        }

        ExecuteDiagnosticsAnswer answer = null;
        if (origAnswer instanceof ExecuteDiagnosticsAnswer) {
            answer = (ExecuteDiagnosticsAnswer) origAnswer;
        }

        return createRemoteDiagnosisResponse(answer);
    }

    protected RemoteDiagnosticsResponse createRemoteDiagnosisResponse(ExecuteDiagnosticsAnswer answer){
        RemoteDiagnosticsResponse response = new RemoteDiagnosticsResponse();
        response.setResult(answer.getResult());
        response.setDetails(answer.getDetails());
        return response;
    }

    protected String setupRemoteCommand(String cmdType, String cmdAddress, String optionalArguments){
        String COMMAND_LINE_TEMPLATE = String.format("%s %s", cmdType, cmdAddress);
        if (optionalArguments != null){
            final String regex = "^[\\w\\-\\s]+$";
            final Pattern pattern = Pattern.compile(regex);
            final boolean hasInvalidChar = pattern.matcher(optionalArguments).find();

            if (!hasInvalidChar){
                s_logger.error("An Invalid character has been passed as an optional parameter");
                throw new IllegalArgumentException("Illegal argument passed as optional parameter.");
            }
            return String.format("%s %s", COMMAND_LINE_TEMPLATE, optionalArguments);
        }
        return COMMAND_LINE_TEMPLATE;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(RemoteDiagnosticsCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return RemoteDiagnosticsService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[] {PingUtility, TracerouteUtility, ArpingUtility};
    }
}
