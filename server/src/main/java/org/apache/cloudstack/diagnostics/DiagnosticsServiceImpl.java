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


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;
import org.apache.cloudstack.api.command.admin.diagnostics.GetDiagnosticsDataCmd;
import org.apache.cloudstack.api.command.admin.diagnostics.RunDiagnosticsCmd;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;


public class DiagnosticsServiceImpl extends ManagerBase implements PluggableService, DiagnosticsService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(DiagnosticsServiceImpl.class);

    protected static final int DefaultDomRSshPort = 3922;
    final File permKey = new File("/root/.ssh/id_rsa.cloud");

    @Inject
    private AgentManager agentManager;
    @Inject
    private VMInstanceDao instanceDao;
    @Inject
    private VirtualMachineManager vmManager;
    @Inject
    private NetworkOrchestrationService networkManager;

    private static final ConfigKey<Boolean> EnableGarbageCollector = new ConfigKey<>("Advanced", Boolean.class,
            "diagnostics.data.gc.enable", "true", "enable the diagnostics data files garbage collector", true);

    private static final ConfigKey<String> TmpMgmtDataStoragePath = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.file.path", "/tmp", "set the temporary path in which to store data in the management server node", false);

    private static final ConfigKey<Integer> DataRetrievalTimeout = new ConfigKey<>("Advanced", Integer.class,
            "diagnostics.data.retrieval.timeout", "3600", "overall data retrieval timeout in seconds", false);

    private static final ConfigKey<Integer> MaximumFileAgeforGarbageCollection = new ConfigKey<>("Advanced", Integer.class,
            "diagnostics.data.max.file.age", "86400", "maximum file age for garbage collection in seconds", false);

    private static final ConfigKey<Integer> GarbageCollectionInterval = new ConfigKey<>("Advanced", Integer.class,
            "diagnostics.data.gc.interval", "86400", "garbage collection interval in seconds", false);

    private static final ConfigKey<Integer> DiskQuotaPercentageThreshold = new ConfigKey<>("Advanced", Integer.class,
            "diagnostics.data.disable.threshold", "0.95", "Minimum disk space percentage to initiate diagnostics file retrieval", false);

    private static final ConfigKey<String> DefaultSupportedDataTypes = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.supported.types", "property, log, configuration, dns, dhcp, vpn, userdata,lb, [iptables], [ifconfig], [routes]", "List of supported diagnostics data type options", false);

    /**
     * Global configs below are used to set the diagnostics
     * data types applicable for each system vm.
     *
     * the names wrapped in square brackets are for data types that need to first execute a script
     * in the system vm and grab output for retrieval, e.g. the output from iptables-save is written to a file
     * which will then be retrieved.
     */
    private static final ConfigKey<String> SsvmDefaultSupportedLogFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.ssvm.supported.log.files", "agent.log, cloud.log", "List of supported diagnostics data log file options for ssvm", false);

    private static final ConfigKey<String> SsvmDefaultSupportedNetworkFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.ssvm.supported.network.files", "[iptables], [ifconfig], [routes]", "List of supported diagnostics data network file options for ssvm", false);

    private static final ConfigKey<String> CpvmDefaultSupportedLogFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.cpvm.supported.log.files", " agent.log", "List of supported diagnostics data log file options for cpvm", false);

    private static final ConfigKey<String> CpvmDefaultSupportedNetworkFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.cpvm.supported.log.files", "[iptables], [ifconfig], [routes]", "List of supported diagnostics data network file options for cpvm", false);


    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SYSTEM_VM_DIAGNOSTICS, eventDescription = "running diagnostics on system vm", async = true)
    public Map<String, String> runDiagnosticsCommand(final RunDiagnosticsCmd cmd) {
        final Long vmId = cmd.getId();
        final String cmdType = cmd.getType().getValue();
        final String ipAddress = cmd.getAddress();
        final String optionalArguments = cmd.getOptionalArguments();
        final VMInstanceVO vmInstance = instanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);

        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system vm with id " + vmId);
        }
        final Long hostId = vmInstance.getHostId();

        if (hostId == null) {
            throw new CloudRuntimeException("Unable to find host for virtual machine instance: " + vmInstance.getInstanceName());
        }

        final String shellCmd = prepareShellCmd(cmdType, ipAddress, optionalArguments);

        if (Strings.isNullOrEmpty(shellCmd)) {
            throw new IllegalArgumentException("Optional parameters contain unwanted characters: " + optionalArguments);
        }

        final Hypervisor.HypervisorType hypervisorType = vmInstance.getHypervisorType();

        final DiagnosticsCommand command = new DiagnosticsCommand(shellCmd, vmManager.getExecuteInSequence(hypervisorType));
        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);

        if (Strings.isNullOrEmpty(accessDetails.get(NetworkElementCommand.ROUTER_IP))) {
            throw new CloudRuntimeException("Unable to set system vm ControlIP for system vm with ID: " + vmId);
        }

        command.setAccessDetail(accessDetails);

        Map<String, String> detailsMap;

        final Answer answer = agentManager.easySend(hostId, command);

        if (answer != null && (answer instanceof DiagnosticsAnswer)) {
            detailsMap = ((DiagnosticsAnswer) answer).getExecutionDetails();
            return detailsMap;
        } else {
            throw new CloudRuntimeException("Failed to execute diagnostics command on remote host: " + answer.getDetails());
        }
    }

    protected boolean hasValidChars(String optionalArgs) {
        if (Strings.isNullOrEmpty(optionalArgs)) {
            return true;
        } else {
            final String regex = "^[\\w\\-\\s.]+$";
            final Pattern pattern = Pattern.compile(regex);
            return pattern.matcher(optionalArgs).find();
        }
    }

    protected String prepareShellCmd(String cmdType, String ipAddress, String optionalParams) {
        final String CMD_TEMPLATE = String.format("%s %s", cmdType, ipAddress);
        if (Strings.isNullOrEmpty(optionalParams)) {
            return CMD_TEMPLATE;
        } else {
            if (hasValidChars(optionalParams)) {
                return String.format("%s %s", CMD_TEMPLATE, optionalParams);
            } else {
                return null;
            }
        }
    }


    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SYSTEM_VM_DIAGNOSTICS, eventDescription = "running diagnostics on system vm", async = true)
    public String getDiagnosticsDataCommand(GetDiagnosticsDataCmd cmd) {
        final Long vmId = cmd.getId();
        List<String> dataType = cmd.getDataTypeList();
        List<String> detail = cmd.getAdditionalFilesList();
        final VMInstanceVO vmInstance = instanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);


        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system vm with id " + vmId);
        }

        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);

        PrepareFilesCommand prepareZipFilesCommand = new PrepareFilesCommand(dataType);
        prepareZipFilesCommand.setAccessDetail(accessDetails);
        final Answer zipFilesAnswer = agentManager.easySend(vmInstance.getHostId(), prepareZipFilesCommand);
        Answer copyZipAnswer = null;

        if (zipFilesAnswer.getResult()){
            String zipFileDir = zipFilesAnswer.getDetails().replace("\n", "");
            CopyZipFilesCommand copyZipCommand = new CopyZipFilesCommand(accessDetails.get("Control"), zipFileDir);
            copyZipAnswer = agentManager.easySend(vmInstance.getHostId(), copyZipCommand);

        }

//        DataStoreTO dataStoreTO
//        CopyToSecondaryStorageCommand toSecondaryStorageCommand = new CopyToSecondaryStorageCommand();
//        return copyZipAnswer.getDetails();
        return null;
    }



//    private String[] getDefaults(VirtualMachine vm) {
//        VirtualMachine.Type vmType = vm.getType();
//        String[] supportedTypes = DefaultSupportedDataTypes.value().split(",");
//        String[] defaults;
//        List<String> filesToRetrieve = new ArrayList<>();
//        switch (vmType){
//            case DomainRouter:
//
//                break;
//            case ConsoleProxy:
//                break;
//            case SecondaryStorageVm:
//                break;
//
//            default:
//                throw new CloudRuntimeException("Unsupported VM type");
//
//        }
//
//        return supportedTypes;
//    }

//    private boolean isTypeSupported(String type) {
//        String[] supportedTypes = DefaultSupportedDataTypes.value().split(",");
//        return Arrays.stream(supportedTypes).anyMatch(type::equals);
//    }


    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{EnableGarbageCollector, TmpMgmtDataStoragePath, DataRetrievalTimeout,
                MaximumFileAgeforGarbageCollection, GarbageCollectionInterval, DiskQuotaPercentageThreshold, DefaultSupportedDataTypes,
        SsvmDefaultSupportedLogFiles, SsvmDefaultSupportedNetworkFiles, CpvmDefaultSupportedLogFiles, CpvmDefaultSupportedNetworkFiles,
        };
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(RunDiagnosticsCmd.class);
        cmdList.add(GetDiagnosticsDataCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return DiagnosticsService.class.getSimpleName();
    }
}