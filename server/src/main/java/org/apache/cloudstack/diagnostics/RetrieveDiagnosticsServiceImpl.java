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
import com.cloud.agent.api.RetrieveFilesCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.capacity.dao.CapacityDaoImpl;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
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
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.SecondaryStorageVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
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
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    private Long _timeOut;

    protected Map<String, Object> configParams = new HashMap<String, Object>();
    private Map<String, String> _configs;

    private String scriptNameRetrieve = null;

    private String scriptNameRemove = null;



    HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();

    @Inject
    private HostDao _hostDao;

    @Inject
    private SecondaryStorageVmDao _secStorageVmDao;

    @Inject
    private DataStoreManager _dataStoreMgr;

    @Inject
    private AgentManager _agentMgr;

    @Inject
    public AccountManager _accountMgr;

    @Inject
    protected ConfigurationDao _configDao;

    @Inject
    protected ResourceManager _resourceMgr;

    @Inject
    protected ManagementServer _serverMgr;

    @Inject
    RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    ConfigDepot _configDepot;

    @Inject
    DiagnosticsConfigurator _diagnosticsDepot;

    @Inject
    ConfigDepot configDepot;

    @Inject
    RouterControlHelper routerControlHelper;

    @Inject
    HostDetailsDao _hostDetailDao;

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


    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);

    public RetrieveDiagnosticsServiceImpl() {
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Initialising configuring values for retrieve diagnostics api : " + name);
        }
        _configs = _configDao.getConfiguration();

        _timeOut = RetrieveDiagnosticsTimeOut.value();
        if (params != null) {
            params.put(RetrieveDiagnosticsTimeOut.key(), (Long)RetrieveDiagnosticsTimeOut.value());
            return true;
        }

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
    public RetrieveDiagnosticsResponse getDiagnosticsFiles(final RetrieveDiagnosticsCmd cmd) throws InvalidParameterValueException, ConfigurationException {
        String systemVmType = null;
        String diagnosticsType = null;
        String fileDetails = null;
        String[] filesToRetrieve = null;
        String[] listOfDiagnosticsFiles = null;
        List<String> diagnosticsFiles = new ArrayList<>();
        if (configParams == null) {
            configParams = new HashMap<>();
        }
        final Long vmId = cmd.getId();
        final VMInstanceVO vmInstance = _vmDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);
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
                    for (String entry : filesToRetrieve ) {
                        diagnosticsFiles.add(entry);
                    }
                } else {
                    fileDetails = cmd.getOptionalListOfFiles();
                    if (fileDetails != null) {
                        filesToRetrieve = fileDetails.split(",");
                        listOfDiagnosticsFiles = getDefaultFilesForVm(diagnosticsType, systemVmType);
                        for (String entry : filesToRetrieve ) {
                            diagnosticsFiles.add(entry);
                        }
                        for (String defaultFileList : listOfDiagnosticsFiles) {
                            diagnosticsFiles.add(defaultFileList);
                        }
                    } else {
                        //retrieve default files from diagnostics data class for the system vm
                         listOfDiagnosticsFiles = getDefaultFilesForVm(diagnosticsType, systemVmType);
                        for (String key : listOfDiagnosticsFiles) {
                            diagnosticsFiles.add(key);
                        }

                    }
                }
                retrieveDiagnosticsFiles(cmd.getId(), diagnosticsFiles, vmInstance, RetrieveDiagnosticsTimeOut.value());
            }
        }
        return null;

    }

    @Override
    public RetrieveDiagnosticsResponse createRetrieveDiagnosticsResponse() {
        RetrieveDiagnosticsResponse response = new RetrieveDiagnosticsResponse();
        response.setSuccess(true);
        response.getDetails();
        return response;
    }

    protected String[] getAllDefaultFilesForEachSystemVm(String diagnosticsType) {
        StringBuilder listDefaultFilesForEachVm = new StringBuilder();
        List<DiagnosticsKey> diagnosticsKey = get(diagnosticsType);
        for (DiagnosticsKey key : diagnosticsKey) {
            listDefaultFilesForEachVm.append(key.getDetail());
        }
        return listDefaultFilesForEachVm.toString().split(",");
    }

    protected String[] getDefaultFilesForVm(String diagnosticsType, String systemVmType) {
        String listDefaultFilesForVm = null;
        List<DiagnosticsKey> diagnosticsKey = allDefaultDiagnosticsTypeKeys.get(diagnosticsType);
        for (DiagnosticsKey key : diagnosticsKey) {
            if (key.getRole().equalsIgnoreCase(systemVmType)) {
                listDefaultFilesForVm = key.getDetail();
            }
            return listDefaultFilesForVm.split(",");
        }
        return null;
    }

    protected void retrieveDiagnosticsFiles(Long ssHostId, List<String> diagnosticsFiles, final VMInstanceVO systemVmId, Long timeout) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieving diagnostics files : " + getConfigComponentName());
        }

        Float disableThreshold = Float.parseFloat(_configs.get("retrieveDiagnostics.disablethreshold"));
        String filePath = _configs.get("retrieveDiagnostics.filepath");
        Long fileAge = Long.parseLong(_configs.get("retrieveDiagnostics.max.fileage"));
        Long timeIntervalGCexecution = Long.parseLong(_configs.get("retrieveDiagnostics.gc.interval"));
        boolean gcEnabled = Boolean.parseBoolean("retrieveDiagnostics.gc.enabled");
        Long wait = Long.parseLong(_configDao.getValue(RetrieveDiagnosticsTimeOut.key()), 3600);
        String tempStr = null;

        if (ssHostId == null) {
            LOGGER.info("No host selected." + getConfigComponentName());
        }
        if (wait <= 0) {
            timeout = RetrieveDiagnosticsTimeOut.value();
        }
        if (filePath == null) {
            filePath = "\\tmp";
        }
        if (fileAge == null) {
            fileAge = 86400L;
        }
        if (disableThreshold == null) {
            disableThreshold = 0.95F;
        }
        if (timeIntervalGCexecution == null) {
            timeIntervalGCexecution = 86400L;
        }
        if (!gcEnabled) {
            gcEnabled = true;
        }
        for (String squareBracketsString : diagnosticsFiles) {
            if (squareBracketsString.contains("[]")) {
                tempStr = squareBracketsString.trim().replaceAll("(^\\[(.*?)\\].*)",("$2").trim());
                if (!tempStr.equalsIgnoreCase("IPTABLES") || !tempStr.equalsIgnoreCase("IFCONFIG")) {
                    throw new InvalidParameterValueException("CloudStack does not support " + squareBracketsString);
                }
                //make the script file name to run from VRScript.java. This name should already be in VRScript.java
                scriptNameRetrieve = tempStr.toLowerCase().concat("retrieve.py");
                scriptNameRemove = tempStr.toLowerCase().concat("remove.py");
            }
        }
        RetrieveFilesCommand command = new RetrieveFilesCommand();
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(systemVmId.getId()));
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, systemVmId.getInstanceName() );

        Answer answer;
        command.setWait(wait.intValue());
        try{
            answer = _agentMgr.send(systemVmId.getHostId(), command);
        }catch (Exception e){
            LOGGER.error("Unable to send command");
            throw new InvalidParameterValueException("Agent unavailable");
        }
        createRetrieveDiagnosticsResponse();
        if (answer != null && answer instanceof Answer) {
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
        if (assignSecStorageFromRunningPool(ssHostId) != null) {



        }

    }

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

    public String getScriptNameRetrieve() {
        return scriptNameRetrieve;
    }

    public void setScriptNameRetrieve(String scriptNameRetrieve) {
        this.scriptNameRetrieve = scriptNameRetrieve;
    }

    public String getScriptNameRemove() {
        return scriptNameRemove;
    }

    public void setScriptNameRemove(String scriptNameRemove) {
        this.scriptNameRemove = scriptNameRemove;
    }


}
