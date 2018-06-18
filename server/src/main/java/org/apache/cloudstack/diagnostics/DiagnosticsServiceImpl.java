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
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.command.admin.diagnostics.ExecuteDiagnosticsCmd;
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
    private NicDao nicDao;
    @Inject
    private NetworkDao networkDao;
    @Inject
    private DataCenterDao dataCenterDao;


    @Override
    public Map<String, String> runDiagnosticsCommand(final ExecuteDiagnosticsCmd cmd) throws AgentUnavailableException, InvalidParameterValueException {
        final Long vmId = cmd.getId();
        final String cmdType = cmd.getType().getValue();
        final String cmdAddress = cmd.getAddress();
        final String optionalArguments = cmd.getOptionalArguments();
        final VMInstanceVO vmInstance = instanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);

        if (vmInstance == null) {
            LOGGER.error("Invalid system vm id provided " + vmId);
            throw new InvalidParameterValueException("Unable to find a system vm with id " + vmId);
        }
        final Long hostId = vmInstance.getHostId();

        if (hostId == null) {
            LOGGER.error("Unable to find host for virtual machine instance: " + vmInstance.getInstanceName());
            throw new CloudRuntimeException("Unable to find host for virtual machine instance: " + vmInstance.getInstanceName());
        }


        final DiagnosticsCommand command = new DiagnosticsCommand(cmdType, cmdAddress, optionalArguments);

        if (vmInstance.getType() == VirtualMachine.Type.DomainRouter) {
            command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(vmInstance.getId()));
        } else {
            final NicVO nicVO = nicDao.getControlNicForVM(vmInstance.getId());
            command.setAccessDetail(NetworkElementCommand.ROUTER_IP, nicVO.getIPv4Address());
        }
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, vmInstance.getInstanceName());

        //command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(vmInstance.getId()));

//        String controlIP = null;
//
//        // if(vmInstanceVO.getHypervisorType() == Hypervisor.HypervisorType.VMware  && dcVo.getNetworkType() == DataCenter.NetworkType.Basic ){
//        if(vmInstance.getHypervisorType() == Hypervisor.HypervisorType.VMware){
//
//            final List<NicVO> nics = nicDao.listByVmId(vmInstance.getId());
//            for (final NicVO nic : nics) {
//                final NetworkVO nc = networkDao.findById(nic.getNetworkId());
//                if (nc.getTrafficType() == Networks.TrafficType.Guest && nic.getIPv4Address() != null) {
//                    controlIP = nic.getIPv4Address();
//                    break;
//                }
//            }
//
//        }else{
//            controlIP = routerControlHelper.getRouterControlIp(vmInstance.getId());
//        }
//
//        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, controlIP);

        Answer answer =  agentManager.easySend(hostId, command);
        final Map<String, String> detailsMap = ((DiagnosticsAnswer) answer).getExecutionDetails();

        if (!detailsMap.isEmpty()) {
            return detailsMap;
        } else {
            LOGGER.error("Failed to parse diagnostics command execution results: " + answer.getDetails());
            throw new CloudRuntimeException("Failed to parse diagnostics command execution results ");
        }
//
//        try {
//            answer = agentManager.send(hostId, command);
//            if (answer instanceof DiagnosticsAnswer) {
//                final Map<String, String> detailsMap = ((DiagnosticsAnswer) answer).getExecutionDetails();
//                if (!detailsMap.isEmpty()) {
//                    return detailsMap;
//                } else {
//                    LOGGER.error("Failed to parse diagnostics command execution results: " + answer.getDetails());
//                    throw new CloudRuntimeException("Failed to parse diagnostics command execution results ");
//                }
//
//            } else {
//                LOGGER.error("Failed to execute diagnostics command: " + answer.getDetails());
//                throw new CloudRuntimeException("Failed to execute diagnostics command: " + answer.getDetails());
//            }
//        } catch (OperationTimedoutException e) {
//            LOGGER.warn("Timed Out", e);
//            throw new AgentUnavailableException("Unable to send commands to virtual machine ", hostId, e);
//        }
    }
//    private String getControlIp (VMInstanceVO vmInstanceVO){
//        final DataCenterVO dcVo = dataCenterDao.findById(vmInstanceVO.getDataCenterId());
//        String controlIP = null;
//
//       // if(vmInstanceVO.getHypervisorType() == Hypervisor.HypervisorType.VMware  && dcVo.getNetworkType() == DataCenter.NetworkType.Basic ){
//        if(vmInstanceVO.getHypervisorType() == Hypervisor.HypervisorType.VMware){
//
//            final List<NicVO> nics = nicDao.listByVmId(vmInstanceVO.getId());
//            for (final NicVO nic : nics) {
//                final NetworkVO nc = networkDao.findById(nic.getNetworkId());
//                if (nc.getTrafficType() == Networks.TrafficType.Guest && nic.getIPv4Address() != null) {
//                    controlIP = nic.getIPv4Address();
//                    break;
//                }
//            }
//
//        }else{
//            controlIP = routerControlHelper.getRouterControlIp(vmInstanceVO.getId());
//        }
//
//        return controlIP;
//    }




    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(ExecuteDiagnosticsCmd.class);
        return cmdList;
    }
}