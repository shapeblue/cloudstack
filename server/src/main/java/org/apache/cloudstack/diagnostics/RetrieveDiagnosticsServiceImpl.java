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
import com.cloud.agent.api.RetrieveDiagnosticsAnswer;
import com.cloud.agent.api.RetrieveFilesCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.storage.CreateEntityDownloadURLAnswer;
import com.cloud.agent.api.storage.CreateEntityDownloadURLCommand;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.capacity.dao.CapacityDaoImpl;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.guru.NetworkGuru;
import com.cloud.resource.ResourceManager;
import com.cloud.server.ManagementServer;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.StorageLayer;
import com.cloud.storage.StoragePool;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountManager;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
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
import org.apache.cloudstack.storage.command.DeleteCommand;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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

    ScheduledExecutorService _executor = null;

    HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();

    private Boolean gcEnabled;
    private Long interval;

    private Float disableThreshold;
    private String filePath;
    private Long fileAge;
    private String diagnosticsZipFileName;

    private Integer nfsVersion;

    protected String _parent = "/mnt/SecStorage";

    protected boolean inSystemVm = false;
    List<NetworkGuru> networkGurus;
    private String secUrl;

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
    private RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    private ConfigDepot _configDepot;

    @Inject
    private DiagnosticsConfigurator _diagnosticsDepot;

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
                    response = retrieveDiagnosticsFiles(vmId, listOfDiagnosticsFiles, vmInstance, RetrieveDiagnosticsTimeOut.value());
                    if (response != null) {
                        String path = "/diagnosticsdata";
                        createFolder(path);

                        if (!copyFileFromSystemVmToServer()) {
                            throw new CloudRuntimeException("Unable to copy compressed files from " + vmInstance.getHostName());
                        } else {
                            CreateEntityDownloadURLCommand downloadcmd = new CreateEntityDownloadURLCommand(dir, _parent + "/diagnosticsdata", uuid, null);
                            //String fileFullPath = path + diagnosticsCompressedFileName;
                            File dir = new File("/diagnosticsdata");
                            FilenameFilter filter = new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.startsWith("diagnosticsFile_");
                                }
                            };
                            String[] diagnosticsCompressedFileNames = dir.list(filter);
                            if (diagnosticsCompressedFileNames == null) {
                                LOGGER.info("Directory /diagnosticsdata does not exist. ");
                            } else {
                                for (int i = 0; i < diagnosticsCompressedFileNames.length; i++) {
                                    CreateEntityDownloadURLAnswer downloadURLAnswer = downloadCompressedFileToSSVM(downloadcmd, diagnosticsCompressedFileNames[i]);
                                    if (downloadURLAnswer == null || !downloadURLAnswer.getResult()) {
                                        String errorString = "Unable to create a link :" + (downloadURLAnswer == null ? "" : downloadURLAnswer.getDetails());
                                        LOGGER.error(errorString);
                                        throw new CloudRuntimeException(errorString);
                                    }
                                }
                            }

                        }
                    }
                } catch(ConcurrentOperationException ex) {
                    throw new CloudRuntimeException("Unable to retrieve diagnostic files" + ex.getCause());
                }

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

    protected void createFolder(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private CreateEntityDownloadURLAnswer downloadCompressedFileToSSVM(CreateEntityDownloadURLCommand cmd, final String diagnosticsZipFileName) {
        boolean isApacheUp = checkAndStartApache();
        if (!isApacheUp) {
            String errorString = "Error in starting Apache server ";
            LOGGER.error(errorString);
            return new CreateEntityDownloadURLAnswer(errorString, CreateEntityDownloadURLAnswer.RESULT_FAILURE);
        }

        String errorString = "";
        long totalBytes = 0;
        long entitySizeinBytes;
        String sourcePath;
        BufferedInputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        final int CHUNK_SIZE = 1024 * 1024; //1M
        StringBuffer sb = new StringBuffer(secUrl);
        // Create the directory structure so that its visible under apache server root
        String extractDir = "/var/www/html/diagnosticsdata/";
        Script command = new Script("/bin/su", LOGGER);
        command.add("-s");
        command.add("/bin/bash");
        command.add("-c");
        command.add("mkdir -p " + extractDir);
        command.add("www-data");
        String result = command.execute();
        if (result != null) {
            errorString = "Error in creating directory =" + result;
            LOGGER.error(errorString);
            return new CreateEntityDownloadURLAnswer(errorString, CreateEntityDownloadURLAnswer.RESULT_FAILURE);
        }
        // Create a random file under the directory for security reasons.
        uuid = cmd.getExtractLinkUUID();
        // Create a symbolic link from the actual directory to the template location. The entity would be directly visible under /var/www/html/userdata/cmd.getInstallPath();
        command = new Script("/bin/bash", LOGGER);
        command.add("-c");
        command.add("ln -sf /mnt/SecStorage/" + cmd.getParent() + File.separator + cmd.getInstallPath() + " " + extractDir + uuid);
        result = command.execute();
        try {
            URL url = new URL(sb.toString());
            URLConnection urlc = url.openConnection();

            File sourceFile = new File(diagnosticsZipFileName);
            entitySizeinBytes = sourceFile.length();

            outputStream = new BufferedOutputStream(urlc.getOutputStream());
            inputStream = new BufferedInputStream(new FileInputStream(sourceFile));
            int bytes = 0;
            byte[] block = new byte[CHUNK_SIZE];
            boolean done = false;
            while (!done) {
                if ((bytes = inputStream.read(block, 0, CHUNK_SIZE)) > -1) {
                    outputStream.write(block, 0, bytes);
                    totalBytes += bytes;
                } else {
                    done = true;
                }
            }
            return new CreateEntityDownloadURLAnswer("Diagnostics zip file downloaded successfully.", CreateEntityDownloadURLAnswer.RESULT_SUCCESS);
        } catch (MalformedURLException e) {
            errorString = e.getMessage();
            LOGGER.error(errorString);
        } catch (IOException e) {
             errorString = e.getMessage();
            LOGGER.error(errorString);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException ioe) {
                LOGGER.error(" Caught exception while closing the resources");
            }
        }
        return null;
    }

    private boolean copyFileFromSystemVmToServer() {

        LOGGER.info("Copying the diagnostics zip file from the system vm.");
        if (!checkForDiskSpace(vmInstance, disableThreshold)) {
            throw new CloudRuntimeException("Unlimited disk space on primary storage.");
        }
        String publicIp = null;
        //final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(vmInstance);
        VirtualMachine vm = vmManager.findById(vmInstance.getId());
        final List<NicVO> nics = _nicDao.listByVmId(vm.getId());
        for (final NicVO nic : nics) {
            final NetworkVO network = _networksDao.findById(nic.getNetworkId());
            final NicVO nicVO = _nicDao.findByNtwkIdAndInstanceId(network.getId(), vm.getId());
            publicIp = nicVO.getIPv4Address();
        }
        try {
            LOGGER.info("Attempting to SSH into linux host " + publicIp);
            Connection conn = new Connection(publicIp);
            conn.connect(null, 600000, 600000);

            LOGGER.info("SSHed successfully into linux host " + publicIp);

            boolean isAuthenticated = conn.authenticateWithPassword("root", "password");

            if (isAuthenticated == false) {
                LOGGER.info("Authentication failed");
                return false;
            }
            //execute copy command
            Session sess = conn.openSession();
            String copyCommand = new String("scp root@" + vmInstance.getPrivateIpAddress() + ":/temp/diagnostics_* " + "/diagnosticsdata");
            LOGGER.info("Executing " + copyCommand);
            sess.execCommand(copyCommand);
            Thread.sleep(120000);
            sess.close();
            //close the connection
            conn.close();
        } catch (Exception ex) {
            LOGGER.error(ex);
            return false;
        }
        return true;
     }

    protected String retrieveDiagnosticsFiles(Long ssHostId, String diagnosticsFiles, final VMInstanceVO systemVmId, Long timeout)
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
        final Hypervisor.HypervisorType hypervisorType = systemVmId.getHypervisorType();
        List<String> scripts = new ArrayList<>();
        String[] files = diagnosticsFiles.split(",");
        List<String> filesToRetrieve = new ArrayList<>();
        for (int i = files.length - 1; i >= 0; i--) {
            if (files[i].contains("[") || files[i].contains("]")) {
                tempStr = files[i].trim().replaceAll("(^\\[(.*?)\\].*?)", ("$2").trim());
                boolean entityInDB = false;
                scriptName = tempStr.toLowerCase().concat(".py");
                String listDefaultFilesForVm = null;
                List<DiagnosticsKey> diagnosticsKey = allDefaultDiagnosticsTypeKeys.get(diagnosticsType);
                for (DiagnosticsKey key : diagnosticsKey) {
                    if (key.getRole().equalsIgnoreCase("ALL")) {
                        listDefaultFilesForVm = key.getDetail();
                        if (listDefaultFilesForVm.equalsIgnoreCase(scriptName)) {
                            entityInDB = true;
                        }
                    }
                }
                if (!entityInDB) {
                    _retrieveDiagnosticsDao.update("ALL", diagnosticsType, scriptName);
                    throw new CloudRuntimeException("The script name is not in the ISO, please add the script " + scriptName + "to the ISO");
                }
                scripts.add(scriptName);
            } else {
                filesToRetrieve.add(files[i]);
            }
        }

        String details = String.join(",", filesToRetrieve);
        final Map<String, String> accessDetails = networkManager.getSystemVMAccessDetails(systemVmId);
        RetrieveFilesCommand retrieveFilesCommand = new RetrieveFilesCommand(details, vmManager.getExecuteInSequence(hypervisorType));
        retrieveFilesCommand.setAccessDetail(accessDetails);
        Answer retrieveAnswer = _agentMgr.easySend(vmInstance.getHostId(), retrieveFilesCommand);

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
                final Answer answer = _agentMgr.easySend(hostId, execCmd);
                if (answer != null && (answer instanceof RetrieveDiagnosticsAnswer)) {
                    executionDetail = ((RetrieveDiagnosticsAnswer) answer).getOutput();
                } else {
                    throw new CloudRuntimeException("Failed to execute ExecuteScriptCommand on remote host: " + answer.getDetails());
                }
            }
        }
        if (Strings.isNullOrEmpty(accessDetails.get(NetworkElementCommand.ROUTER_IP))) {
            throw new CloudRuntimeException("Unable to set system vm ControlIP for the system vm with ID -> " + systemVmId);
        }
        return executionDetail;
    }

    private boolean checkForDiskSpace(VirtualMachine systemVmId, Float disableThreshold) {
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
                LOGGER.error("Unlimited disk space on primary storage.");
                return false;
            }
        }
        return true;
    }
    //get the downloaded files from the /tmp directory and create a zip file to add the files

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
                    DeleteCommand cmd = new DeleteCommand(volTO);
                    String result = cleanupDiagnostics(cmd);
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

    protected  String cleanupDiagnostics(DeleteCommand cmd) {
        DataTO obj = cmd.getData();
        DataStoreTO dstore = obj.getDataStore();
        if (dstore instanceof NfsTO) {
            NfsTO nfs = (NfsTO) dstore;
            String parent = getRootDir(nfs.getUrl(), nfsVersion);
            secUrl = nfs.getUrl();
            if (!parent.endsWith(File.separator)) {
                parent += File.separator;
            }
            String diagnosticsZipPath = obj.getPath();
            if (diagnosticsZipPath.startsWith(File.separator)) {
                diagnosticsZipPath = diagnosticsZipPath.substring(1);
            }
            String fullDiagnosticsFilePath = parent + diagnosticsZipPath;
            File diagnosticsFileDir = new File(fullDiagnosticsFilePath);
            if (diagnosticsFileDir.exists() && diagnosticsFileDir.isDirectory()) {
                LOGGER.debug("Diagnostics zip file path " + diagnosticsFileDir + " is a directory, already deleted so no need to delete");
                return  "Diagnostics zip file path " + diagnosticsFileDir + " is a directory, already deleted so no need to delete";
            }
            int index = diagnosticsZipPath.lastIndexOf("/");
            String diagnosticsZipFileName = diagnosticsZipPath.substring(index + 1);
            diagnosticsZipPath = diagnosticsZipPath.substring(0, index);
            String absolutediagnosticsZipFilePath = parent + diagnosticsZipPath;
            File diagnosticsZipDir = new File(absolutediagnosticsZipFilePath);
            String details = null;
            if (!diagnosticsZipDir.exists()) {
                details = "Diagnostics zip file directory " + diagnosticsZipDir.getName() + " doesn't exist";
                LOGGER.debug(details);
                return details;
            }
            String lPath = absolutediagnosticsZipFilePath + "/*" + diagnosticsZipFileName + "*";
            String result = deleteLocalFile(lPath);
            if (result != null) {
                details = "Failed to delete diagnostics zip file " + lPath + " , err=" + result;
                LOGGER.warn(details);
                return details;
            }
        }

        return null;
    }

    private String deleteLocalFile(String fullPath) {
        Script command = new Script("/bin/bash", LOGGER);
        command.add("-c");
        command.add("rm -rf " + fullPath);
        String result = command.execute();
        if (result != null) {
            String errMsg = "Failed to delete file " + fullPath + ", err=" + result;
            LOGGER.warn(errMsg);
            return errMsg;
        }
        return null;
    }


    private String getRootDir(String secUrl, Integer nfsVersion) {
        if (!inSystemVm) {
            return _parent;
        }
        try {
            URI uri = new URI(secUrl);
            String dir = mountUri(uri, nfsVersion);
            return _parent + "/" + dir;
        } catch (Exception e) {
            String msg = "GetRootDir for " + secUrl + " failed due to " + e.toString();
            LOGGER.error(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    protected String mountUri(URI uri, Integer nfsVersion) throws UnknownHostException {
        String uriHostIp = getUriHostIp(uri);
        String nfsPath = uriHostIp + ":" + uri.getPath();

        dir = UUID.nameUUIDFromBytes(nfsPath.getBytes(com.cloud.utils.StringUtils.getPreferredCharset())).toString();
        String localRootPath = _parent + "/" + dir;

        String remoteDevice;
        if (uri.getScheme().equals("cifs")) {
            remoteDevice = "//" + uriHostIp + uri.getPath();
            LOGGER.debug("Mounting device with cifs-style path of " + remoteDevice);
        } else {
            remoteDevice = nfsPath;
            LOGGER.debug("Mounting device with nfs-style path of " + remoteDevice);
        }
        mount(localRootPath, remoteDevice, uri, nfsVersion);
        return dir;
    }

    protected String getUriHostIp(URI uri) throws UnknownHostException {
        String nfsHost = uri.getHost();
        InetAddress nfsHostAddr = InetAddress.getByName(nfsHost);
        String nfsHostIp = nfsHostAddr.getHostAddress();
        LOGGER.info("Determined host " + nfsHost + " corresponds to IP " + nfsHostIp);
        return nfsHostIp;
    }

    protected void mount(String localRootPath, String remoteDevice, URI uri, Integer nfsVersion) {
        LOGGER.debug("mount " + uri.toString() + " on " + localRootPath + ((nfsVersion != null) ? " nfsVersion=" + nfsVersion : ""));
        ensureLocalRootPathExists(localRootPath, uri);

        if (mountExists(localRootPath, uri)) {
            return;
        }

        attemptMount(localRootPath, remoteDevice, uri, nfsVersion);

        checkForVolumesDir(localRootPath);
    }

    protected boolean checkForVolumesDir(String mountPoint) {
        String volumesDirLocation = mountPoint + "/" + "diagnosticsdata";
        return createDir("diagnosticsdata", volumesDirLocation, mountPoint);
    }

    protected boolean createDir(String dirName, String dirLocation, String mountPoint) {
        boolean dirExists = false;

        File dir = new File(dirLocation);
        if (dir.exists()) {
            if (dir.isDirectory()) {
                LOGGER.debug(dirName + " already exists on secondary storage, and is mounted at " + mountPoint);
                dirExists = true;
            } else {
                if (dir.delete() && _storage.mkdir(dirLocation)) {
                    dirExists = true;
                }
            }
        } else if (_storage.mkdir(dirLocation)) {
            dirExists = true;
        }

        if (dirExists) {
            LOGGER.info(dirName + " directory created/exists on Secondary Storage.");
        } else {
            LOGGER.info(dirName + " directory does not exist on Secondary Storage.");
        }

        return dirExists;
    }

    protected boolean mountExists(String localRootPath, URI uri) {
        Script script = null;
        script = new Script(!inSystemVm, "mount", 80000, LOGGER);

        List<String> res = new ArrayList<String>();
        ZfsPathParser parser = new ZfsPathParser(localRootPath);
        script.execute(parser);
        res.addAll(parser.getPaths());
        for (String s : res) {
            if (s.contains(localRootPath)) {
                LOGGER.debug("Some device already mounted at " + localRootPath + ", no need to mount " + uri.toString());
                return true;
            }
        }
        return false;
    }

    public static class ZfsPathParser extends OutputInterpreter {
        String _parent;
        List<String> paths = new ArrayList<String>();

        public ZfsPathParser(String parent) {
            _parent = parent;
        }

        @Override
        public String interpret(BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
                paths.add(line);
            }
            return null;
        }

        public List<String> getPaths() {
            return paths;
        }

        @Override
        public boolean drain() {
            return true;
        }
    }

    protected void ensureLocalRootPathExists(String localRootPath, URI uri) {
        LOGGER.debug("making available " + localRootPath + " on " + uri.toString());
        File file = new File(localRootPath);
        LOGGER.debug("local folder for mount will be " + file.getPath());
        if (!file.exists()) {
            LOGGER.debug("create mount point: " + file.getPath());
            _storage.mkdir(file.getPath());

            // Need to check after mkdir to allow O/S to complete operation
            if (!file.exists()) {
                String errMsg = "Unable to create local folder for: " + localRootPath + " in order to mount " + uri.toString();
                LOGGER.error(errMsg);
                throw new CloudRuntimeException(errMsg);
            }
        }
    }

    protected void attemptMount(String localRootPath, String remoteDevice, URI uri, Integer nfsVersion) {
        String result;
        LOGGER.debug("Make cmdline call to mount " + remoteDevice + " at " + localRootPath + " based on uri " + uri + ((nfsVersion != null) ? " nfsVersion=" + nfsVersion : ""));
        Script command = new Script(!inSystemVm, "mount", 80000, LOGGER);

        String scheme = uri.getScheme().toLowerCase();
        command.add("-t", scheme);

        if (scheme.equals("nfs")) {
            if ("Mac OS X".equalsIgnoreCase(System.getProperty("os.name"))) {
                // See http://wiki.qnap.com/wiki/Mounting_an_NFS_share_from_OS_X
                command.add("-o", "resvport");
            }
            if (inSystemVm) {
                command.add("-o", "soft,timeo=133,retrans=2147483647,tcp,acdirmax=0,acdirmin=0" + ((nfsVersion != null) ? ",vers=" + nfsVersion : ""));
            }
        } else {
            String errMsg = "Unsupported storage device scheme " + scheme + " in uri " + uri.toString();
            LOGGER.error(errMsg);
            throw new CloudRuntimeException(errMsg);
        }

        command.add(remoteDevice);
        command.add(localRootPath);
        result = command.execute();
        if (result != null) {
            // Fedora Core 12 errors out with any -o option executed from java
            String errMsg = "Unable to mount " + remoteDevice + " at " + localRootPath + " due to " + result;
            LOGGER.error(errMsg);
            File file = new File(localRootPath);
            if (file.exists()) {
                file.delete();
            }
            throw new CloudRuntimeException(errMsg);
        }
        LOGGER.debug("Successfully mounted " + remoteDevice + " at " + localRootPath);
    }

    protected RetrieveDiagnosticsResponse createDiagnosticsResponse(String urlString) {
        RetrieveDiagnosticsResponse response = new RetrieveDiagnosticsResponse();
        response.setUrl(urlString);
        return response;
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

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }
}
