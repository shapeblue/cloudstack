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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.configuration.Config;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.EncryptionUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.cloudstack.api.command.admin.diagnostics.GetDiagnosticsDataCmd;
import org.apache.cloudstack.api.command.admin.diagnostics.RunDiagnosticsCmd;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.utils.imagestore.ImageStoreUtil;
import org.apache.cxf.common.util.CollectionUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


public class DiagnosticsServiceImpl extends ManagerBase implements PluggableService, DiagnosticsService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(DiagnosticsServiceImpl.class);

    @Inject
    private AgentManager agentManager;
    @Inject
    private VMInstanceDao instanceDao;
    @Inject
    private VirtualMachineManager vmManager;
    @Inject
    private NetworkOrchestrationService networkManager;
    @Inject
    private ConfigurationDao configDao;


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

    /**
     * Global configs below are used to set the diagnostics
     * data types applicable for each system vm.
     *
     * the names wrapped in square brackets are for data types that need to first execute a script
     * in the system vm and grab output for retrieval, e.g. the output from iptables-save is written to a file
     * which will then be retrieved.
     */
    private static final ConfigKey<String> SsvmDefaultSupportedFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.ssvm.defaults", "[IPTABLES], [IFCONFIG], [ROUTE], /usr/local/cloud/systemvm/conf/agent.properties," +
            " /usr/local/cloud/systemvm/conf/consoleproxy.properties, /var/log/cloud.log",
            "List of supported diagnostics data file options for the ssvm", false);

    private static final ConfigKey<String> CpvmDefaultSupportedFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.cpvm.defaults", "[IPTABLES], [IFCONFIG], [ROUTE], /usr/local/cloud/systemvm/conf/agent.properties, " +
            "/usr/local/cloud/systemvm/conf/consoleproxy.properties, /var/log/cloud.log",
            "List of supported diagnostics data file options for the cpvm", false);

    private static final ConfigKey<String> VrDefaultSupportedFiles = new ConfigKey<>("Advanced", String.class,
            "diagnostics.data.vr.defaults", "defaults: \"[IPTABLES], [IFCONFIG], [ROUTE], " +
            "/etc/dnsmasq.conf, /etc/resolv.conf, /etc/haproxy.conf, /etc/hosts.conf, /etcdnsmaq-resolv.conf, /var/log/cloud.log, " +
            "/var/log/routerServiceMonitor.log, /var/log/dnsmasq.log",
            "List of supported diagnostics data file options for the VR", false);



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

    private String getSecondaryStoragePostUploadParams(String b64encodedData){
        Map<String, String> uploadParams = new HashMap<>();
        String ssvmUrlDomain = configDao.getValue(Config.SecStorageSecureCopyCert.key());
        String uuid = UUID.randomUUID().toString();
        List<VMInstanceVO> vm = instanceDao.listByTypes(VirtualMachine.Type.SecondaryStorageVm);
        String url = ImageStoreUtil.generatePostUploadUrl(ssvmUrlDomain,"172.20.20.11", uuid );

        DateTime currentTime = new DateTime(DateTimeZone.UTC);
        String expires = currentTime.plusMinutes(3).toString();

        // Get Key
        String key = configDao.getValue(Config.SSVMPSK.key());

        // encode metadata using the post upload config ssh key
        Gson gson = new GsonBuilder().create();
        String metadata = EncryptionUtil.encodeData(gson.toJson(b64encodedData), key);

        // Compute signature on url, expiry and metadata
        String signature = EncryptionUtil.generateSignature(metadata + url + expires, key);
        return url;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_SYSTEM_VM_DIAGNOSTICS, eventDescription = "running diagnostics on system vm", async = true)
    public String getDiagnosticsDataCommand(GetDiagnosticsDataCmd cmd) {
        final Long vmId = cmd.getId();
        final VMInstanceVO vmInstance = instanceDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);

        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system vm with id " + vmId);
        }

        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);

        List<String> fileList = prepareFiles(cmd.getDataTypeList(), cmd.getAdditionalFileList(), vmInstance);

        //PrepareFilesCommand prepareZipFilesCommand = new PrepareFilesCommand(fileList, secondaryStorageUrl);
        String dummyUrl = "http://172.20.20.11";
        PrepareFilesCommand prepareZipFilesCommand = new PrepareFilesCommand(fileList, dummyUrl);
        prepareZipFilesCommand.setAccessDetail(accessDetails);
        Answer zipFilesAnswer = agentManager.easySend(vmInstance.getHostId(), prepareZipFilesCommand);

        // Retrieve zip file is b64 encoded payload
        String b64EncodedPayload = "";
        if (zipFilesAnswer.getResult()){
            b64EncodedPayload = zipFilesAnswer.getDetails();
        }

        String secondaryStorageUrl = getSecondaryStoragePostUploadParams(b64EncodedPayload);


        return secondaryStorageUrl;
    }

    // Prepare List of files to be retrieved from system vm or VR
    protected List<String> prepareFiles(List<String> dataTypeList, List<String> additionalFileList, VirtualMachine vm){
        List<String> filesList = new ArrayList<>();
        VirtualMachine.Type vmType = vm.getType();
        String[] defaultFiles;
        if (CollectionUtils.isEmpty(dataTypeList)){
            switch (vmType){
                case DomainRouter:
                    defaultFiles = VrDefaultSupportedFiles.value().split(",");
                    for (String file: defaultFiles) {
                        filesList.add(file);
                    }
                        break;
                case SecondaryStorageVm:
                    defaultFiles = SsvmDefaultSupportedFiles.value().split(",");
                    for (String file: defaultFiles) {
                        filesList.add(file);
                    }
                    break;
                case ConsoleProxy:
                    defaultFiles = CpvmDefaultSupportedFiles.value().split(",");
                    for (String file: defaultFiles) {
                        filesList.add(file);
                    }
                    break;
                default:
                    throw new CloudRuntimeException("Unsupported vm type for retrieve diagnostics data: ");
            }
        } else {
            for (String file: dataTypeList) {
                filesList.add(file);
            }
        }
        if (!CollectionUtils.isEmpty(additionalFileList)){
            for (String extraFile: additionalFileList) {
                filesList.add(extraFile);

            }
        }
        return filesList;
    }



    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{EnableGarbageCollector, TmpMgmtDataStoragePath, DataRetrievalTimeout,
                MaximumFileAgeforGarbageCollection, GarbageCollectionInterval, DiskQuotaPercentageThreshold,
                SsvmDefaultSupportedFiles, CpvmDefaultSupportedFiles, VrDefaultSupportedFiles};
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