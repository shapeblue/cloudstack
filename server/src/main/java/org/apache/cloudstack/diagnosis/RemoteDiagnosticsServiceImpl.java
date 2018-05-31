/*
 * // Licensed to the Apache Software Foundation (ASF) under one
 * // or more contributor license agreements.  See the NOTICE file
 * // distributed with this work for additional information
 * // regarding copyright ownership.  The ASF licenses this file
 * // to you under the Apache License, Version 2.0 (the
 * // "License"); you may not use this file except in compliance
 * // with the License.  You may obtain a copy of the License at
 * //
 * //   http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing,
 * // software distributed under the License is distributed on an
 * // "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * // KIND, either express or implied.  See the License for the
 * // specific language governing permissions and limitations
 * // under the License.
 */

package org.apache.cloudstack.diagnosis;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.command.admin.diagnosis.RemoteDiagnosticsCmd;
import org.apache.cloudstack.api.response.RemoteDiagnosticsResponse;
import org.apache.cloudstack.diangosis.RemoteDiagnosticsService;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class RemoteDiagnosticsServiceImpl extends ManagerBase implements PluggableService, RemoteDiagnosticsService {
    private static final Logger s_logger = Logger.getLogger(RemoteDiagnosticsServiceImpl.class);

    @Inject
    private RouterControlHelper routerControlHelper;
    @Inject
    private AgentManager agentManager;
    @Inject
    private VMInstanceDao vmInstanceDao;


    @Override
    public RemoteDiagnosticsResponse executeDiagnosisToolInSystemVm(final RemoteDiagnosticsCmd cmd) throws AgentUnavailableException,
            InvalidParameterValueException {
        final Long systemVmId = cmd.getId();
        final VMInstanceVO systemVm = vmInstanceDao.findById(systemVmId);
        if (systemVm == null){
            s_logger.error("Unable to find a virtual machine with id " + systemVm);
            throw new InvalidParameterValueException("Unable to find a virtual machine with id " + systemVm);
        }
        final Long hostId = systemVm.getHostId();

        final String diagnosisCommandType = cmd.getDiagnosisType();
        final String destinationIpAddress = cmd.getDestinationIpAddress();
        final String optionalArgunments = cmd.getOptionalArguments();



        String remoteCommand = setupRemoteCommand(diagnosisCommandType, destinationIpAddress, optionalArgunments);

        final ExecuteDiagnosisCommand command = new ExecuteDiagnosisCommand(remoteCommand);
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(systemVm.getId()));
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, systemVm.getInstanceName());

        final String commandPassed = command.getSrciptArguments();

        Answer origAnswer;
        try{
            origAnswer = agentManager.send(hostId, command);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            throw new AgentUnavailableException("Unable to send commands to virtual machine ", hostId, e);
        }

        ExecuteDiagnosisAnswer answer = null;
        if (origAnswer instanceof ExecuteDiagnosisAnswer) {
            answer = (ExecuteDiagnosisAnswer) origAnswer;
        }

        return createRemoteDiagnosisResponse(answer,commandPassed);
    }

    private static RemoteDiagnosticsResponse createRemoteDiagnosisResponse(ExecuteDiagnosisAnswer answer, String commandPaased){
        RemoteDiagnosticsResponse response = new RemoteDiagnosticsResponse();
        response.setResult(answer.getResult());
        response.setDetails(answer.getDetails());
        response.setNetworkCommand(commandPaased);
        return response;
    }

    private static String setupRemoteCommand(String diagnosisType, String destinationIpAddress, String optionalArguments){
        if (optionalArguments != null){
            return String.format("%s %s", diagnosisType, destinationIpAddress+" "+optionalArguments);
        }

        return String.format("%s %s", diagnosisType, destinationIpAddress);
    }


    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(RemoteDiagnosticsCmd.class);
        return cmdList;
    }


}
