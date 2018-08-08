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
import com.cloud.agent.api.ExecuteScriptCommand;
import com.cloud.agent.api.HandleDiagnosticsZipFileCommand;
import com.cloud.agent.api.RetrieveDiagnosticsAnswer;
import com.cloud.agent.api.RetrieveFilesCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.storage.CopyRetrieveZipFilesCommand;
import com.cloud.agent.api.storage.CreateEntityDownloadURLAnswer;
import com.cloud.agent.api.storage.CreateEntityDownloadURLCommand;
import com.cloud.agent.api.storage.DeleteEntityDownloadURLCommand;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.capacity.dao.CapacityDaoImpl;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.element.ConfigDriveNetworkElement;
import com.cloud.network.guru.NetworkGuru;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.server.ManagementServer;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageLayer;
import com.cloud.storage.StoragePool;
import com.cloud.storage.Upload;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeDetailVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.user.AccountManager;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.engine.subsystem.api.storage.ChapInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeService;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.DiagnosticsConfigurator;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.cloudstack.storage.configdrive.org.apache.cloudstack.storage.diagnostics.Diagnostics;
import org.apache.cloudstack.storage.configdrive.org.apache.cloudstack.storage.diagnostics.DiagnosticsBuilder;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    protected StorageLayer _storage;
    private Long _timeOut;

    protected Map<String, Object> configParams = new HashMap<String, Object>();
    private Map<String, String> _configs;

    private String scriptName = null;
    private String diagnosticsType = null;
    private String dir;

    private Long hostId = null;
    private VMInstanceVO vmInstance;
    private String uuid;
    private final Map<String, UploadJob> jobs = new ConcurrentHashMap<String, UploadJob>();

    ScheduledExecutorService _executor = null;

    HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();

    private Map<String, String> accessDetails;

    private Boolean gcEnabled;
    private Long interval;

    private Float disableThreshold;
    private String filePath;
    private Long fileAge;
    private String diagnosticsZipFileName;
    private String secondaryUrl;
    private String parentDir;

    private ExecutorService threadPool;

    private Integer nfsVersion;
    private static final Random RANDOM = new Random(System.nanoTime());

    protected String _parent = "/mnt/SecStorage";

    protected boolean inSystemVm = false;
    List<NetworkGuru> networkGurus;
    private String secUrl;

    @Inject
    VolumeDataFactory volumeFactory;

    @Inject
    private VolumeDetailsDao volumeDetailsDao;

    @Inject
    NetworkModel _networkModel;

    @Inject
    NetworkDao _networksDao = null;

    @Inject
    NicDao _nicDao = null;

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
    EndPointSelector _ep;


    @Inject
    private RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    private ConfigDepot _configDepot;

    @Inject
    private DiagnosticsConfigurator _diagnosticsDepot;

    @Inject
    private VolumeService _volumeService;

    @Inject
    private ConfigDepot configDepot;

    @Inject
    private HostDetailsDao _hostDetailDao;

    @Inject
    protected VMInstanceDao _vmDao;

    @Inject
    private ServiceOfferingDao _offeringDao;

    @Inject
    private VolumeDao _volumeDao;

    @Inject private ClusterDao clusterDao;

    @Inject
    private DiskOfferingDao _diskOfferingDao;

    @Inject
    private PrimaryDataStoreDao _poolDao;

    @Inject
    private CapacityDao _capacityDao;

    @Inject
    private VirtualMachineManager vmManager;

    @Inject
    protected DataCenterDao _dcDao = null;

    @Inject
    protected PrimaryDataStoreDao _storagePoolDao = null;

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
        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Diagnostics-GarbageCollector"));
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

        String value = (String)params.get("install.numthreads");
        final int numInstallThreads = NumbersUtil.parseInt(value, 10);
        threadPool = Executors.newFixedThreadPool(numInstallThreads);

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

    private String getVolumeProperty(long volumeId, String property) {
        VolumeDetailVO volumeDetails = volumeDetailsDao.findDetail(volumeId, property);

        if (volumeDetails != null) {
            return volumeDetails.getValue();
        }

        return null;
    }

    private Map<String, String> getVolumeDetails(VolumeInfo volumeInfo) {
        long storagePoolId = volumeInfo.getPoolId();
        StoragePoolVO storagePoolVO = _storagePoolDao.findById(storagePoolId);

        if (!storagePoolVO.isManaged()) {
            return null;
        }

        Map<String, String> volumeDetails = new HashMap<>();

        VolumeVO volumeVO = _volumeDao.findById(volumeInfo.getId());

        volumeDetails.put(DiskTO.STORAGE_HOST, storagePoolVO.getHostAddress());
        volumeDetails.put(DiskTO.STORAGE_PORT, String.valueOf(storagePoolVO.getPort()));
        volumeDetails.put(DiskTO.IQN, volumeVO.get_iScsiName());

        volumeDetails.put(DiskTO.VOLUME_SIZE, String.valueOf(volumeVO.getSize()));
        volumeDetails.put(DiskTO.SCSI_NAA_DEVICE_ID, getVolumeProperty(volumeInfo.getId(), DiskTO.SCSI_NAA_DEVICE_ID));

        ChapInfo chapInfo = _volumeService.getChapInfo(volumeInfo, volumeInfo.getDataStore());

        if (chapInfo != null) {
            volumeDetails.put(DiskTO.CHAP_INITIATOR_USERNAME, chapInfo.getInitiatorUsername());
            volumeDetails.put(DiskTO.CHAP_INITIATOR_SECRET, chapInfo.getInitiatorSecret());
            volumeDetails.put(DiskTO.CHAP_TARGET_USERNAME, chapInfo.getTargetUsername());
            volumeDetails.put(DiskTO.CHAP_TARGET_SECRET, chapInfo.getTargetSecret());
        }

        return volumeDetails;
    }

    @Override
    public String getDiagnosticsFiles(final RetrieveDiagnosticsCmd cmd)
            throws ConcurrentOperationException, InvalidParameterValueException, ConfigurationException {
        String systemVmType = null;
        String fileDetails = null;
        String listOfDiagnosticsFiles = null;
        List<String> diagnosticsFiles = new ArrayList<>();
        if (configParams == null) {
            configParams = new HashMap<>();
        }
        final Long vmId = cmd.getId();
        vmInstance = _vmDao.findByIdTypes(vmId, VirtualMachine.Type.ConsoleProxy, VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm);
        //vmInstance.getDetails()

        if (vmInstance == null) {
            throw new InvalidParameterValueException("Unable to find a system VM with id " + vmId);
        }
        hostId = vmInstance.getHostId();
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
                String response = null;
                try {
                    /*final Map<String, String> vmAccessDetail = networkManager.getSystemVMAccessDetails(vmInstance);
                    String ip = vmAccessDetail.get(NetworkElementCommand.ROUTER_IP);*/
                    if (checkForDiskSpace(vmInstance, disableThreshold)) {
                        response = retrieveDiagnosticsFiles(vmId, listOfDiagnosticsFiles, vmInstance, RetrieveDiagnosticsTimeOut.value());
                        if (response != null) {
                            try {
                                VolumeVO volume = null;
                                volume = _volumeDao.findById(hostId);
                                Long zoneId = volume.getDataCenterId();
                                DataStore store = _dataStoreMgr.getImageStore(zoneId);
                                VolumeInfo volInfo = volumeFactory.getVolume(volume.getId());
                                Hypervisor.HypervisorType type = volInfo.getHypervisorType();
                                String hostIp = null;
                                HostVO hostVO = null;
                                if (Hypervisor.HypervisorType.KVM.equals(type)) {
                                    hostVO = getHost(volInfo.getDataCenterId(), type, true);
                                    hostIp = hostVO.getPublicIpAddress();
                                }
                                RetrieveZipFilesCommand command = new RetrieveZipFilesCommand(false, true);
                                /*boolean srcVolumeDetached = volInfo.getAttachedVM() == null;
                                StoragePoolVO storagePoolVO = _storagePoolDao.findById(volInfo.getPoolId());
                                Map<String, String> srcDetails = getVolumeDetails(volInfo);
                                VolumeObjectTO volTO = new VolumeObjectTO();
                                String command = "./scpScript";*/
                                Answer copyToHost = _agentMgr.send(hostVO.getId(), command);
                                if (copyToHost == null && !StringUtils.isEmpty(copyToHost.getDetails())) {
                                    throw new CloudRuntimeException(copyToHost.getDetails());
                                }
                                /*long ssZoneid = hostVO.getDataCenterId();

                                SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findByInstanceName(hostVO.getName());
                                if (secStorageVm == null) {
                                    LOGGER.warn("secondary storage VM " + hostVO.getName() + " doesn't exist");

                                }

                                DataStore ssStores = _dataStoreMgr.getImageStore(ssZoneid);
                                VolumeInfo destvol = volumeFactory.getVolume(volume.getId(), ssStores);//(VolumeInfo)ssStores;
                                String secUrl = ssStores.getUri();*/
                                command = new RetrieveZipFilesCommand(true, true);
                                Answer copyToSec = _agentMgr.send(hostVO.getId(), command);
                                if (copyToSec != null && !StringUtils.isEmpty(copyToSec.getDetails())) {
                                    throw new CloudRuntimeException(copyToSec.getDetails());
                                }
                            } catch (AgentUnavailableException e) {

                            } catch (OperationTimedoutException ex) {

                            }
                        }
                        /*try {
                           /* if (!downloadDiagnosticsFileToSecStorage(VirtualMachineProfile profile, DeployDestination dest)) {
                                return
                            }
                        } catch (ResourceUnavailableException e) {
                            throw new CloudRuntimeException("Resources on the secondary storage vm are limited to copy the diagnostics zip file.");
                        }*/
                    }

                } catch(ConcurrentOperationException ex) {
                    throw new CloudRuntimeException("Unable to retrieve diagnostic files" + ex.getCause());
                }
            }
        }
        return null;

    }

    private DataStore findDataStore(VirtualMachineProfile profile, DeployDestination dest) {
        DataStore dataStore = null;
        if (VirtualMachineManager.VmConfigDriveOnPrimaryPool.value()) {
            if (dest.getStorageForDisks() != null) {
                for (final Volume volume : dest.getStorageForDisks().keySet()) {
                    if (volume.getVolumeType() == Volume.Type.ROOT) {
                        final StoragePool primaryPool = dest.getStorageForDisks().get(volume);
                        dataStore = _dataStoreMgr.getDataStore(primaryPool.getId(), DataStoreRole.Primary);
                        break;
                    }
                }
            }
            if (dataStore == null) {
                final List<VolumeVO> volumes = _volumeDao.findByInstanceAndType(profile.getVirtualMachine().getId(), Volume.Type.ROOT);
                if (volumes != null && volumes.size() > 0) {
                    dataStore = _dataStoreMgr.getDataStore(volumes.get(0).getPoolId(), DataStoreRole.Primary);
                }
            }
        } else {
            dataStore = _dataStoreMgr.getImageStore(dest.getDataCenter().getId());
        }
        return dataStore;
    }

    private Long findAgentIdForImageStore(final DataStore dataStore) throws ResourceUnavailableException {
        EndPoint endpoint = _ep.select(dataStore);
        if (endpoint == null) {
            throw new ResourceUnavailableException("Config drive creation failed, secondary store not available",
                    dataStore.getClass(), dataStore.getId());
        }
        return endpoint.getId();
    }

    private Long findAgentId(VirtualMachineProfile profile, DeployDestination dest, DataStore dataStore) throws ResourceUnavailableException {
        Long agentId;
        if (dest.getHost() == null) {
            agentId = (profile.getVirtualMachine().getHostId() == null ? profile.getVirtualMachine().getLastHostId() : profile.getVirtualMachine().getHostId());
        } else {
            agentId = dest.getHost().getId();
        }
        if (!VirtualMachineManager.VmConfigDriveOnPrimaryPool.value()) {
            agentId = findAgentIdForImageStore(dataStore);
        }
        return agentId;
    }

    private boolean downloadDiagnosticsFileToSecStorage(VirtualMachineProfile profile, DeployDestination dest) throws ResourceUnavailableException {
        final DataStore dataStore = findDataStore(profile, dest);
        final Long agentId = findAgentId(profile, dest, dataStore);
        if (agentId == null || dataStore == null) {
            throw new ResourceUnavailableException("Failed to copy diagnostics file, agent or datastore not available",
                    ConfigDriveNetworkElement.class, 0L);
        }

        LOGGER.debug("Copying diagnostics zip file to secondary storage.");

        final String diagnosticsFileName = Diagnostics.diagnosticsFileName(profile.getInstanceName());
        final String diagnosticsPath = Diagnostics.createDiagnosticsPath(profile.getInstanceName());
        final String diagnosticsData = DiagnosticsBuilder.buildConfigDrive(Diagnostics.DIAGNOSTICSDIR, diagnosticsFileName);
        final HandleDiagnosticsZipFileCommand diagnosticsZipFileCommand = new HandleDiagnosticsZipFileCommand(diagnosticsPath, diagnosticsData, dataStore.getTO(), true);

        final Answer answer = _agentMgr.easySend(agentId, diagnosticsZipFileCommand);
        if (!answer.getResult()) {
            throw new ResourceUnavailableException(String.format("Config drive iso creation failed, details: %s",
                    answer.getDetails()), ConfigDriveNetworkElement.class, 0L);
        }
        return true;
    }


    private boolean checkAndStartApache() {
        //Check whether the Apache server is running
        Script command = new Script("/bin/systemctl", LOGGER);
        command.add("is-active");
        command.add("apache2");
        String result = command.execute();

        //Apache Server is not running. Try to start it.
        if (result != null && !result.equals("active")) {
            command = new Script("/bin/systemctl", LOGGER);
            command.add("start");
            command.add("apache2");
            result = command.execute();
            if (result != null) {
                LOGGER.warn("Error in starting apache2 service err=" + result);
                return false;
            }
        }

        return true;
    }

    private CreateEntityDownloadURLAnswer handleCreateEntityURLCommand(CreateEntityDownloadURLCommand cmd) {

        boolean isApacheUp = checkAndStartApache();
        if (!isApacheUp) {
            String errorString = "Error in starting Apache server ";
            LOGGER.error(errorString);
            return new CreateEntityDownloadURLAnswer(errorString, CreateEntityDownloadURLAnswer.RESULT_FAILURE);
        }
        // Create the directory structure so that its visible under apache server root
        String downloadDir = "/var/www/html/diagnosticsdata/";
        Script command = new Script("/bin/su", LOGGER);
        command.add("-s");
        command.add("/bin/bash");
        command.add("-c");
        command.add("mkdir -p " + downloadDir);
        command.add("www-data");
        String result = command.execute();
        if (result != null) {
            String errorString = "Error in creating directory =" + result;
            LOGGER.error(errorString);
            return new CreateEntityDownloadURLAnswer(errorString, CreateEntityDownloadURLAnswer.RESULT_FAILURE);
        }

        // Create a random file under the directory for security reasons.
        String uuid = cmd.getExtractLinkUUID();
        // Create a symbolic link from the actual directory to the template location. The entity would be directly visible under /var/www/html/diagnosticsdata/cmd.getInstallPath();
        command = new Script("/bin/bash", LOGGER);
        command.add("-c");
        command.add("ln -sf /mnt/SecStorage/" + cmd.getParent() + File.separator + cmd.getInstallPath() + " " + downloadDir + uuid);
        result = command.execute();
        if (result != null) {
            String errorString = "Error in linking  err=" + result;
            LOGGER.error(errorString);
            return new CreateEntityDownloadURLAnswer(errorString, CreateEntityDownloadURLAnswer.RESULT_FAILURE);
        }

        return new CreateEntityDownloadURLAnswer("", CreateEntityDownloadURLAnswer.RESULT_SUCCESS);
    }

    public class Completion implements CompressedFileUploader.UploadCompleteCallback {
        private final String jobId;

        public Completion(String jobId) {
            this.jobId = jobId;
        }

        @Override
        public void uploadComplete(CompressedFileUploader.Status status) {
            setUploadStatus(jobId, status);
        }
    }

    public void setUploadStatus(String jobId, CompressedFileUploader.Status status) {
        UploadJob uj = jobs.get(jobId);
        if (uj == null) {
            LOGGER.warn("setUploadStatus for jobId: " + jobId + ", status=" + status + " no job found");
            return;
        }
        CompressedFileUploader tu = uj.getTemplateUploader();
        LOGGER.warn("Upload Completion for jobId: " + jobId + ", status=" + status);
        LOGGER.warn("error=" + tu.getUploadError());

        switch (status) {
            case ABORTED:
            case NOT_STARTED:
            case UNRECOVERABLE_ERROR:
                // Delete the entity only if its a volume. TO DO - find a better way of finding it a volume.
                if (uj.getTemplateUploader().getUploadLocalPath().indexOf("volume") > -1) {
                    uj.cleanup();
                }
                break;
            case UNKNOWN:
                return;
            case IN_PROGRESS:
                LOGGER.info("Resuming jobId: " + jobId + ", status=" + status);
                tu.setResume(true);
                threadPool.execute(tu);
                break;
            case RECOVERABLE_ERROR:
                threadPool.execute(tu);
                break;
            case UPLOAD_FINISHED:
                tu.setUploadError("Upload success, starting install ");
                String result = postUpload(jobId);
                if (result != null) {
                    LOGGER.error("Failed post upload script: " + result);
                    tu.setStatus(CompressedFileUploader.Status.UNRECOVERABLE_ERROR);
                    tu.setUploadError("Failed post upload script: " + result);
                } else {
                    LOGGER.warn("Upload completed successfully at " + new SimpleDateFormat().format(new Date()));
                    tu.setStatus(CompressedFileUploader.Status.POST_UPLOAD_FINISHED);
                    tu.setUploadError("Upload completed successfully at " + new SimpleDateFormat().format(new Date()));
                }
                // Delete the entity only if its a volume. TO DO - find a better way of finding it a volume.
                if (uj.getTemplateUploader().getUploadLocalPath().indexOf("volume") > -1) {
                    uj.cleanup();
                }
                break;
            default:
                break;
        }
    }

    private String postUpload(String jobId) {
        return null;
    }

    private static class UploadJob {
        private final CompressedFileUploader tu;

        public UploadJob(CompressedFileUploader tu, String jobId, long id, String name, Storage.ImageFormat format, boolean hvm, Long accountId, String descr, String cksum,
                         String installPathPrefix) {
            super();
            this.tu = tu;
        }

        public CompressedFileUploader getTemplateUploader() {
            return tu;
        }

        public void cleanup() {
            if (tu != null) {
                String upldPath = tu.getUploadLocalPath();
                if (upldPath != null) {
                    File f = new File(upldPath);
                    f.delete();
                }
            }
        }

    }

    private Answer handleDeleteEntityDownloadURLCommand(DeleteEntityDownloadURLCommand cmd) {

        //Delete the soft link. Example path = volumes/8/74eeb2c6-8ab1-4357-841f-2e9d06d1f360.vhd
        LOGGER.warn("handleDeleteEntityDownloadURLCommand Path:" + cmd.getPath() + " Type:" + cmd.getType().toString());
        String path = cmd.getPath();
        Script command = new Script("/bin/bash", LOGGER);
        command.add("-c");

        //We just need to remove the UUID.vhd
        String extractUrl = cmd.getExtractUrl();
        command.add("unlink /var/www/html/userdata/" + extractUrl.substring(extractUrl.lastIndexOf(File.separator) + 1));
        String result = command.execute();
        if (result != null) {
            // FIXME - Ideally should bail out if you cant delete symlink. Not doing it right now.
            // This is because the ssvm might already be destroyed and the symlinks do not exist.
            LOGGER.warn("Error in deleting symlink :" + result);
        }

        // If its a volume also delete the Hard link since it was created only for the purpose of download.
        if (cmd.getType() == Upload.Type.VOLUME) {
            command = new Script("/bin/bash", LOGGER);
            command.add("-c");
            command.add("rm -rf /mnt/SecStorage/" + cmd.getParentPath() + File.separator + path);
            LOGGER.warn(" " + parentDir + File.separator + path);
            result = command.execute();
            if (result != null) {
                String errorString = "Error in deleting volume " + path + " : " + result;
                LOGGER.warn(errorString);
                return new Answer(cmd, false, errorString);
            }
        }

        return new Answer(cmd, true, "");
    }

    private HostVO getHost(Long zoneId, Hypervisor.HypervisorType hypervisorType, boolean computeClusterMustSupportResign) {
        Preconditions.checkArgument(zoneId != null, "Zone ID cannot be null.");
        Preconditions.checkArgument(hypervisorType != null, "Hypervisor type cannot be null.");

        List<HostVO> hosts = _hostDao.listByDataCenterIdAndHypervisorType(zoneId, hypervisorType);

        if (hosts == null) {
            return null;
        }

        List<Long> clustersToSkip = new ArrayList<>();

        Collections.shuffle(hosts, RANDOM);

        for (HostVO host : hosts) {

            if (!ResourceState.Enabled.equals(host.getResourceState())) {
                continue;
            }

            if (computeClusterMustSupportResign) {
                long clusterId = host.getClusterId();

                if (clustersToSkip.contains(clusterId)) {
                    continue;
                }

                if (clusterDao.getSupportsResigning(clusterId)) {
                    return host;
                }
                else {
                    clustersToSkip.add(clusterId);
                }
            }
            else {
                return host;
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

    protected void createFolder(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    protected String retrieveDiagnosticsFiles(Long ssHostId, String diagnosticsFiles, final VMInstanceVO vmInstance, Long timeout)
            throws ConcurrentOperationException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieving diagnostics files : " + getConfigComponentName());
        }
        if (gcEnabled) {
            if (!start()) {
                LOGGER.info("Failed to start the Diagnostics GarbageCollector");
            }
        }
        String tempStr = null;
        String executionDetail = null;
        final Hypervisor.HypervisorType hypervisorType = vmInstance.getHypervisorType();
        List<String> scripts = new ArrayList<>();
        String[] files = diagnosticsFiles.split(",");
        List<String> filesToRetrieve = new ArrayList<>();
        for (int i = files.length - 1; i >= 0; i--) {
            if (files[i].contains("[") || files[i].contains("]")) {
                tempStr = files[i].trim().replaceAll("(^\\[(.*?)\\].*?)", ("$2").trim());
                scriptName = tempStr.toLowerCase().concat(".py");
                scripts.add(scriptName);
            } else {
                filesToRetrieve.add(files[i]);
            }
        }

        String details = String.join(" ", filesToRetrieve);
        accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);
        RetrieveFilesCommand retrieveFilesCommand = new RetrieveFilesCommand(details, vmManager.getExecuteInSequence(hypervisorType));
        Answer retrieveAnswer = null;
        retrieveFilesCommand.setAccessDetail(accessDetails);
        try {
            retrieveAnswer = _agentMgr.send(this.vmInstance.getHostId(), retrieveFilesCommand);
        } catch (AgentUnavailableException ex) {

        } catch (OperationTimedoutException e) {

        }
        if (retrieveAnswer != null) {
            executionDetail = ((RetrieveDiagnosticsAnswer) retrieveAnswer).getOutput();
        } else {
            throw new CloudRuntimeException("Failed to execute RetrieveDiagnosticsCommand on remote host: " + retrieveAnswer.getDetails());
        }

        ExecuteScriptCommand execCmd = null;
        if (!scripts.isEmpty()) {
            for (String script : scripts) {
                execCmd = new ExecuteScriptCommand(script, vmManager.getExecuteInSequence(hypervisorType));
                execCmd.setAccessDetail(accessDetails);
                try {
                    retrieveAnswer = _agentMgr.send(hostId, execCmd);
                } catch (AgentUnavailableException ex) {

                } catch (OperationTimedoutException e) {

                }
                if (retrieveAnswer != null && (retrieveAnswer instanceof RetrieveDiagnosticsAnswer)) {
                    executionDetail = ((RetrieveDiagnosticsAnswer) retrieveAnswer).getOutput();
                } else {
                    throw new CloudRuntimeException("Failed to execute ExecuteScriptCommand on remote host: " + retrieveAnswer.getDetails());
                }
            }
        }
        if (Strings.isNullOrEmpty(accessDetails.get(NetworkElementCommand.ROUTER_IP))) {
            throw new CloudRuntimeException("Unable to set system vm ControlIP for the system vm with ID -> " + vmInstance);
        }
        return executionDetail;
    }

    private boolean checkForDiskSpace(VirtualMachine systemVmId, Float disableThreshold) {
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
                LOGGER.error("Unlimited disk space on primary storage.");
                return false;
            }
        }
        return true;
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
        return new ConfigKey<?>[] { RetrieveDiagnosticsTimeOut, RetrieveDiagnosticsTimeOut, RetrieveDiagnosticsGCEnable,
                RetrieveDiagnosticsInterval, RetrieveDiagnosticsDisableThreshold, RetrieveDiagnosticsFileAge };
    }


    @Override
    public List<Class<?>> getCommands(){
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(RetrieveDiagnosticsCmd.class);
        return cmdList;
    }

    protected class DiagnosticsGarbageCollector implements Runnable {
        private String diagnosticsZipFileName = null;
        private String path = "/diagnosticsdata/";

        public DiagnosticsGarbageCollector(String diagnosticsZipFileName) {
            this.diagnosticsZipFileName = diagnosticsZipFileName;
        }

        @Override
        public void run() {
            try {
                LOGGER.trace("Diagnostics Garbage Collection Thread is running.");
                File diagnosticszipFile = new File(path + diagnosticsZipFileName);
                Path diagnosticsZipFilePath = diagnosticszipFile.toPath();
                BasicFileAttributes attributes = null;
                try {
                    attributes = Files.readAttributes(diagnosticsZipFilePath, BasicFileAttributes.class);
                } catch (IOException exception) {
                    LOGGER.error("Could not find the zip file.", exception);
                }
                long milliseconds = attributes.creationTime().to(TimeUnit.MILLISECONDS);
                VolumeVO volume = null;
                volume = _volumeDao.findById(hostId);
                Long zoneId = volume.getDataCenterId();
                DataStore store = _dataStoreMgr.getImageStore(zoneId);
                DataStoreTO destStore = store.getTO();

                VolumeObjectTO volTO = new VolumeObjectTO();
                volTO.setDataStore(store.getTO());

                if (store == null) {
                    throw new CloudRuntimeException("cannot find an image store for zone " + zoneId);
                }
                //DataStoreTO destStoreTO = store.getTO();
                if (milliseconds > RetrieveDiagnosticsFileAge.value()) {
                    String result = cleanupDiagnostics();
                    if (result != null) {
                        String msg = "Unable to delete diagnostics zip file: " + result;
                        LOGGER.error(msg);
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Caught the following Exception", e);
            }
        }
    }

    protected  String cleanupDiagnostics() {
        Script command = new Script("/bin/bash", LOGGER);
        command.add("-c");
        command.add("rm -rf " + secondaryUrl);
        CopyRetrieveZipFilesCommand diagnosticsCleanup = new CopyRetrieveZipFilesCommand(command.toString(), "", true, true);
        try {
            Answer answer = _agentMgr.send(vmInstance.getHostId(), diagnosticsCleanup);
            if (answer == null || !answer.getResult()) {
                if (answer != null && !StringUtils.isEmpty(answer.getDetails())) {
                    throw new CloudRuntimeException(answer.getDetails());
                }
            }
            return answer.getDetails();
        }catch (AgentUnavailableException ex) {
            throw new CloudRuntimeException("Agent not available.");
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("operation timed out.");
        }
    }

    @Override
    public boolean start() {
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
            _executor.scheduleWithFixedDelay(new DiagnosticsGarbageCollector(diagnosticsZipFileName), initialDelay, cleanupInterval.intValue(), TimeUnit.SECONDS);
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

}
