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
import com.cloud.agent.api.Command;
import com.cloud.agent.api.ExecuteScriptCommand;
import com.cloud.agent.api.RetrieveDiagnosticsAnswer;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.manager.Commands;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.capacity.dao.CapacityDaoImpl;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.resource.ResourceManager;
import com.cloud.server.ManagementServer;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.StoragePool;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountManager;
import com.cloud.utils.ExecutionResult;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.ssh.SshHelper;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.SecondaryStorageVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;
import com.jcraft.jsch.JSchException;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.DiagnosticsConfigurator;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    private Long _timeOut;

    protected Map<String, Object> configParams = new HashMap<String, Object>();
    private Map<String, String> _configs;

    private String scriptName = null;

    ScheduledExecutorService _executor = null;

    HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();

    private Boolean gcEnabled;
    private Long interval;

    private Float disableThreshold;
    private String filePath;
    private Long fileAge;

    @Inject
    private HostDao _hostDao;

    @Inject
    private SecondaryStorageVmDao _secStorageVmDao;

    @Inject
    private DataStoreManager _dataStoreMgr;

    @Inject
    private AgentManager _agentMgr;

    @Inject
    private ManagementServerHostDao managementServerHostDao;

    @Inject
    public AccountManager _accountMgr;

    @Inject
    protected ConfigurationDao _configDao;

    @Inject
    protected ResourceManager _resourceMgr;

    @Inject
    private NetworkOrchestrationService networkManager;

    @Inject
    protected ManagementServer _serverMgr;

    @Inject
    private RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    private ConfigDepot _configDepot;

    @Inject
    private DiagnosticsConfigurator _diagnosticsDepot;

    @Inject
    private ConfigDepot configDepot;

    @Inject
    private RouterControlHelper routerControlHelper;

    @Inject
    private HostDetailsDao _hostDetailDao;

    @Inject
    protected VMInstanceDao _vmDao;

    @Inject
    private ServiceOfferingDao _offeringDao;

    @Inject
    private VolumeDao _volumeDao;

    @Inject
    private DiskOfferingDao _diskOfferingDao;

    @Inject
    private PrimaryDataStoreDao _poolDao;

    @Inject
    private CapacityDao _capacityDao;

    @Inject
    private VirtualMachineManager vmManager;


    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    ConfigKey<Boolean> RetrieveDiagnosticsGCEnable = new ConfigKey<Boolean>("Advanced", Boolean.class, "retrieveDiagnostics.gc.enabled", "true",
            "Garbage collection on/off switch", true, ConfigKey.Scope.Global);
    ConfigKey<Long> RetrieveDiagnosticsInterval = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.gc.interval", "86400",
            "Interval between garbage collection execution", true, ConfigKey.Scope.Global);
    ConfigKey<Float> RetrieveDiagnosticsDisableThreshold = new ConfigKey<Float>("Advanced", Float.class, "retrieveDiagnostics.disablethreshold", "0.95",
            "Percentage disk space cut-off before API will fail", true, ConfigKey.Scope.Global);
    ConfigKey<String> RetrieveDiagnosticsFilePath = new ConfigKey<String>("Advanced", String.class, "retrieveDiagnostics.filepath", "/tmp",
            "The path to use on the management server for all temporary data", true, ConfigKey.Scope.Global);
    ConfigKey<Long> RetrieveDiagnosticsFileAge = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.max.fileage", "86400",
            "The Diagnostics file age in seconds before considered for garbage collection", true, ConfigKey.Scope.Global);

    public RetrieveDiagnosticsServiceImpl() {
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Initialising configuring values for retrieve diagnostics api : " + name);
        }
        _configs = _configDao.getConfiguration();

        _timeOut = RetrieveDiagnosticsTimeOut.value();
        gcEnabled = RetrieveDiagnosticsGCEnable.value();
        interval = RetrieveDiagnosticsInterval.value();
        disableThreshold = RetrieveDiagnosticsDisableThreshold.value();
        filePath = RetrieveDiagnosticsFilePath.value();
        fileAge = RetrieveDiagnosticsFileAge.value();

        if (params != null) {
            params.put(RetrieveDiagnosticsTimeOut.key(), (Long)RetrieveDiagnosticsTimeOut.value());
            params.put(RetrieveDiagnosticsGCEnable.key(), (Boolean)RetrieveDiagnosticsGCEnable.value());
            params.put(RetrieveDiagnosticsInterval.key(), (Long)RetrieveDiagnosticsInterval.value());
            params.put(RetrieveDiagnosticsDisableThreshold.key(), (Float)RetrieveDiagnosticsDisableThreshold.value());
            params.put(RetrieveDiagnosticsFilePath.key(), (String)RetrieveDiagnosticsFilePath.value());
            params.put(RetrieveDiagnosticsFileAge.key(), (Long)RetrieveDiagnosticsFileAge.value());

            return true;
        }

        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Diagnostics-GarbageCollector"));

        return false;
    }

    @Override
    public List<DiagnosticsKey> get(String key) {
        List<DiagnosticsKey> value = allDefaultDiagnosticsTypeKeys.get(key);
        return value != null ? value : null;
    }


    protected void loadDiagnosticsDataConfiguration() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieving diagnostics data values for retrieve diagnostics api : " + getConfigComponentName());
        }
        List<RetrieveDiagnosticsVO> listVO = _retrieveDiagnosticsDao.retrieveAllDiagnosticsData();
        DiagnosticsKey diagnosticsKey = null;
        for (RetrieveDiagnosticsVO vo : listVO) {
            if (allDefaultDiagnosticsTypeKeys != null) {
                List<DiagnosticsKey> value = get(vo.getType());
                if (value == null) {
                    value = new ArrayList<>();
                    diagnosticsKey = new DiagnosticsKey(vo.getRole(), vo.getType(), vo.getDefaultValue(), "");
                    value.add(diagnosticsKey);
                } else {
                    diagnosticsKey = new DiagnosticsKey(vo.getRole(), vo.getType(), vo.getDefaultValue(), "");
                    value.add(diagnosticsKey);
                }
                allDefaultDiagnosticsTypeKeys.put(vo.getType(), value);
             }
        }
        _diagnosticsDepot.setDiagnosticsKeyHashMap(allDefaultDiagnosticsTypeKeys);
    }

   public Pair<List<? extends Configuration>, Integer> searchForDiagnosticsConfigurations(final RetrieveDiagnosticsCmd cmd) {
       final Long zoneId = _accountMgr.checkAccessAndSpecifyAuthority(CallContext.current().getCallingAccount(), cmd.getId());
       final Long timeOut = NumbersUtil.parseLong(cmd.getTimeOut(), 3600);
       final Object id = _accountMgr.checkAccessAndSpecifyAuthority(CallContext.current().getCallingAccount(), cmd.getId());

       final Filter searchFilter = new Filter(ConfigurationVO.class, "id", true, 0L, 0L);

       final SearchBuilder<ConfigurationVO> sb = _configDao.createSearchBuilder();
       sb.and("timeout", sb.entity().getValue(), SearchCriteria.Op.EQ);
       final SearchCriteria<ConfigurationVO> sc = sb.create();
       if (timeOut != null) {
           sc.setParameters("timeout", timeOut);
       }
       final Pair<List<ConfigurationVO>, Integer> result = _configDao.searchAndCount(sc, searchFilter);
       final List<ConfigurationVO> configVOList = new ArrayList<ConfigurationVO>();
       for (final ConfigurationVO param : result.first()) {
           final ConfigurationVO configVo = _configDao.findByName(param.getName());
           if (configVo != null) {
               final ConfigKey<?> key = _configDepot.get(param.getName());
               if (key != null) {
                   configVo.setValue(key.valueIn((Long) id) == null ? null : key.valueIn((Long) id).toString());
                   configVOList.add(configVo);
               } else {
                   LOGGER.warn("ConfigDepot could not find parameter " + param.getName());
               }
           } else {
               LOGGER.warn("Configuration item  " + param.getName() + " not found.");
           }
       }
       return new Pair<List<? extends Configuration>, Integer>(configVOList, configVOList.size());
   }

    @Override
    public Map<String, String> getDiagnosticsFiles(final RetrieveDiagnosticsCmd cmd)
            throws ResourceUnavailableException, OperationTimedoutException, ConcurrentOperationException, InvalidParameterValueException, ConfigurationException {
        String systemVmType = null;
        String diagnosticsType = null;
        String fileDetails = null;
        String listOfDiagnosticsFiles = null;
        List<String> diagnosticsFiles = new ArrayList<>();
        if (configParams == null) {
            configParams = new HashMap<>();
        }
        final Long vmId = cmd.getId();
        final VMInstanceVO vmInstance = _vmDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);
        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system VM with id " + vmId);
        }
        final Long hostId = vmInstance.getHostId();
        if (hostId == null) {
            throw new CloudRuntimeException("Unable to find host for virtual machine instance -> " + vmInstance.getInstanceName());
        }

        loadDiagnosticsDataConfiguration();
        if (configure(getConfigComponentName(), configParams)) {
            if (cmd != null) {
                if (cmd.getTimeOut() != null) {
                    RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "", cmd.getTimeOut(), "", true);
                }
                systemVmType = cmd.getEventType();
                diagnosticsType = cmd.getType();
                if (systemVmType == null) {
                    throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "No host was selected.");
                }
                if (diagnosticsType == null) {
                    listOfDiagnosticsFiles = getAllDefaultFilesForEachSystemVm(diagnosticsType);
                } else {
                    fileDetails = cmd.getOptionalListOfFiles();
                    if (fileDetails != null) {
                        StringBuilder filesToRetrieve = new StringBuilder();
                        filesToRetrieve.append(fileDetails);
                        listOfDiagnosticsFiles = getDefaultFilesForVm(diagnosticsType, systemVmType);
                        if (listOfDiagnosticsFiles != null) {
                            filesToRetrieve.append("," + listOfDiagnosticsFiles);
                        }
                        listOfDiagnosticsFiles = filesToRetrieve.toString();
                    } else {
                        //retrieve default files from diagnostics data class for the system vm
                         listOfDiagnosticsFiles = getDefaultFilesForVm(diagnosticsType, systemVmType);
                    }
                }
                Map<String, String> response = null;
                try {
                    response = retrieveDiagnosticsFiles(vmId, listOfDiagnosticsFiles, vmInstance, RetrieveDiagnosticsTimeOut.value());
                } catch(JSchException ex) {
                    System.exit(1);
                } catch (IOException e) {
                    System.exit(1);
                }
                if (response != null)
                    return response;
            }
        }
        return null;

    }

    protected String getAllDefaultFilesForEachSystemVm(String diagnosticsType) {
        StringBuilder listDefaultFilesForEachVm = new StringBuilder();
        List<DiagnosticsKey> diagnosticsKey = get(diagnosticsType);
        for (DiagnosticsKey key : diagnosticsKey) {
            listDefaultFilesForEachVm.append(key.getDetail());
        }
        return listDefaultFilesForEachVm.toString();
    }

    protected String getDefaultFilesForVm(String diagnosticsType, String systemVmType) {
        String listDefaultFilesForVm = null;
        List<DiagnosticsKey> diagnosticsKey = allDefaultDiagnosticsTypeKeys.get(diagnosticsType);
        for (DiagnosticsKey key : diagnosticsKey) {
            if (key.getRole().equalsIgnoreCase(systemVmType)) {
                listDefaultFilesForVm = key.getDetail();
                return listDefaultFilesForVm;
            }

        }
        return null;
    }

    public ExecutionResult copyFileFromSystemVm(final String ip, final String filename) {

        final File permKey = new File("/root/.ssh/id_rsa.cloud");
        boolean success = true;
        String details = "Copying file in System VM, with ip: " + ip + ", file: " + filename;
        LOGGER.debug(details);

        try {
            SshHelper.scpTo(ip, 22, "root", permKey, null, null, filename.getBytes(), filename, null);
        } catch (final Exception e) {
            LOGGER.warn("Fail to copy file " + filename + " in System VM " + ip, e);
            details = e.getMessage();
            success = false;
        }
        return new ExecutionResult(success, details);
    }


    protected Map<String, String> retrieveDiagnosticsFiles(Long ssHostId, String diagnosticsFiles, final VMInstanceVO systemVmId, Long timeout)
            throws ResourceUnavailableException, OperationTimedoutException, ConcurrentOperationException, JSchException, IOException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieving diagnostics files : " + getConfigComponentName());
        }
        String tempStr = null;
        final Hypervisor.HypervisorType hypervisorType = systemVmId.getHypervisorType();
        List<String> scripts = new ArrayList<>();
        Commands cmds = null;
        String[] files = diagnosticsFiles.split(",");
        for (int i = files.length - 1; i >= 0; i--) {
            if (files[i].contains("[") || files[i].contains("]")) {
                tempStr = files[i].trim().replaceAll("(^\\[(.*?)\\].*?)", ("$2").trim());
                scriptName = tempStr.toLowerCase().concat(".py");
                scripts.add(scriptName);
                files = (String[])ArrayUtils.removeElement(files, files[i]);
            }
        }
        ManagementServerHostVO managementServerHostVO = managementServerHostDao.findById(systemVmId.getHostId());
        String ipHostAddress = managementServerHostVO.getServiceIP();
        StringBuilder filesSpaceDelimited = new StringBuilder();
        for (String file : files) {
            filesSpaceDelimited.append(file);
            copyFileFromSystemVm(ipHostAddress, file);
            filesSpaceDelimited.append(" ");
        }
        String filesToRetrieveCommaSeparated = filesSpaceDelimited.toString();
        cmds = new Commands(Command.OnError.Stop);
        ExecuteScriptCommand execCmd = null;
        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(systemVmId);
        if (!scripts.isEmpty()) {
            for (String script : scripts) {
                execCmd = new ExecuteScriptCommand(script, vmManager.getExecuteInSequence(hypervisorType));
                execCmd.setAccessDetail(accessDetails);
                cmds.addCommand(execCmd);
            }
        }

        //RetrieveFilesCommand retrieveFilesCommand = new RetrieveFilesCommand(filesToRetrieveCommaSeparated, vmManager.getExecuteInSequence(hypervisorType));
        //retrieveFilesCommand.setAccessDetail(accessDetails);
        //cmds.addCommand(retrieveFilesCommand);

        if (Strings.isNullOrEmpty(accessDetails.get(NetworkElementCommand.ROUTER_IP))) {
            throw new CloudRuntimeException("Unable to set system vm ControlIP for the system vm with ID -> " + systemVmId);
        }
        Map<String, String> resultsMap;
        final Long hostId = systemVmId.getHostId();
        Answer[] answers = _agentMgr.send(hostId, cmds);

        if (!cmds.isSuccessful()) {
            copyFileFromSystemVm(ipHostAddress, scriptName + ".log");
            for (final Answer answer : answers) {
                if (!answer.getResult()) {
                    LOGGER.warn("Failed to retrieve results due to: " + answer.getDetails());

                    throw new CloudRuntimeException("Unable to retrieve results " + systemVmId + " due to " + answer.getDetails());
                }
                if (answer != null && (answer instanceof RetrieveDiagnosticsAnswer)) {
                    resultsMap = ((RetrieveDiagnosticsAnswer) answer).getResultDetails();
                    return resultsMap;
                } else {
                    throw new CloudRuntimeException("Diagnostics command failed");
                }

            }
            checkForDiskSpace(systemVmId, disableThreshold);
        }
        return null;

    }

    private void checkForDiskSpace(VirtualMachine systemVmId, Float disableThreshold) {
        //zip the files on the fly (in the script), send the tar file to mgt-server
        //and choose secondary storage to download the zip file. In this scenario, we will not have to worry about the disk space
        //on the System VM but will have to check for free disk space on the mgt-server where the tar is temporarily copied to.
        // Check if the vm is using any disks on local storage.
        final VirtualMachineProfile vmProfile = new VirtualMachineProfileImpl(systemVmId, null, _offeringDao.findById(systemVmId.getId(), systemVmId.getServiceOfferingId()), null, null);
        final List<VolumeVO> volumes = _volumeDao.findCreatedByInstance(vmProfile.getId());
        boolean usesLocal = false;
        VolumeVO localVolumeVO = null;
        Long volClusterId = null;
        for (final VolumeVO volume : volumes) {
            final DiskOfferingVO diskOffering = _diskOfferingDao.findById(volume.getDiskOfferingId());
            final DiskProfile diskProfile = new DiskProfile(volume, diskOffering, vmProfile.getHypervisorType());
            if (diskProfile.useLocalStorage()) {
                usesLocal = true;
                localVolumeVO = volume;
                break;
            }
        }
        if (usesLocal) {
            StoragePool storagePool = _poolDao.findById(localVolumeVO.getPoolId());
            volClusterId = storagePool.getClusterId();
            if (volClusterId != null) {
                if (storagePool.isLocal() || usesLocal) {
                    if (localVolumeVO.getPath() == null) {
                        String temp = _configs.get("retrieveDiagnostics.filepath");
                        localVolumeVO.setPath(_configs.get(temp));
                    }
                }
            }
        } else {

        }
        List<Short> clusterCapacityTypes = getCapacityTypesAtClusterLevel();
        for (Short capacityType : clusterCapacityTypes) {
            List<CapacityDaoImpl.SummedCapacity> capacity = new ArrayList<>();
            capacity = _capacityDao.findCapacityBy(capacityType.intValue(), null, null, volClusterId);
            if (capacityType.compareTo(Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED) != 0) {
                continue;
            }
            double totalCapacity = capacity.get(0).getTotalCapacity();
            double usedCapacity = capacity.get(0).getUsedCapacity() + capacity.get(0).getReservedCapacity();
            if (totalCapacity != 0 && usedCapacity / totalCapacity > disableThreshold) {
                LOGGER.error("Unlimited disk space");
            }
        }
    }
    //get the downloaded files from the /tmp directory and create a zip file to add the files

    private void checkDiagnosticsFilesAndZip(String diagnosticsFileName, ZipOutputStream zipFile) {
        File f = new File(diagnosticsFileName);
        if (f.exists() && !f.isDirectory()) {

        }
    }

    private List<Short> getCapacityTypesAtClusterLevel() {
        List<Short> clusterCapacityTypes = new ArrayList<Short>();
        clusterCapacityTypes.add(Capacity.CAPACITY_TYPE_CPU);
        clusterCapacityTypes.add(Capacity.CAPACITY_TYPE_MEMORY);
        clusterCapacityTypes.add(Capacity.CAPACITY_TYPE_STORAGE);
        clusterCapacityTypes.add(Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED);
        clusterCapacityTypes.add(Capacity.CAPACITY_TYPE_LOCAL_STORAGE);
        return clusterCapacityTypes;
    }

    protected String assignSecStorageFromRunningPool(Long zoneId) {
        HostVO cssHost = _hostDao.findById(zoneId);
        Long zone = cssHost.getDataCenterId();
        if (cssHost.getType() == Host.Type.SecondaryStorageVM) {
            SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findByInstanceName(cssHost.getName());
            if (secStorageVm == null) {
                LOGGER.warn("secondary storage VM " + cssHost.getName() + " doesn't exist");
                return null;
            }
        }
        DataStore ssStore = _dataStoreMgr.getImageStore(zone);
        return ssStore.getUri();
    }

    public Map<String, Object> getConfigParams() {
        return configParams;
    }

    public void setConfigParams(Map<String, Object> configParams) {
        this.configParams = configParams;
    }

    @Override
    public String getConfigComponentName() {
        return RetrieveDiagnosticsServiceImpl.class.getSimpleName();
    }

    public Map<String, List<DiagnosticsKey>> getDefaultDiagnosticsData() {
        return allDefaultDiagnosticsTypeKeys;
    }

    @Override
    public ConfigKey<?>[] getConfigKeys()
    {
        return new ConfigKey<?>[] { RetrieveDiagnosticsTimeOut };
    }


    @Override
    public List<Class<?>> getCommands(){
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(RetrieveDiagnosticsCmd.class);
        return cmdList;
    }

    protected class DiagnosticsGarbageCollector implements Runnable {

        public DiagnosticsGarbageCollector() {
        }

        @Override
        public void run() {
            try {
                LOGGER.trace("Diagnostics Garbage Collection Thread is running.");

                cleanupDiagnostics();

            } catch (Exception e) {
                LOGGER.error("Caught the following Exception", e);
            }
        }
    }

    @Override
    public void cleanupDiagnostics() {
    }

    @Override
    public boolean start() {
        gcEnabled = Boolean.parseBoolean("retrieveDiagnostics.gc.enabled");
        if (interval == null) {
            interval = 86400L;
        }
        Integer cleanupInterval = (int)(long) interval;
        if (!super.start()) {
            return false;
        }
        if (gcEnabled.booleanValue()) {
            Random generator = new Random();
            int initialDelay = generator.nextInt(cleanupInterval);
            _executor.scheduleWithFixedDelay(new DiagnosticsGarbageCollector(), initialDelay, cleanupInterval.intValue(), TimeUnit.SECONDS);
        } else {
            LOGGER.debug("Diagnostics garbage collector is not enabled, so the cleanup thread is not being scheduled.");
        }

        return true;
    }

    @Override
    public boolean stop() {
        if (gcEnabled.booleanValue()) {
            _executor.shutdown();
        }
        return true;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }



}
