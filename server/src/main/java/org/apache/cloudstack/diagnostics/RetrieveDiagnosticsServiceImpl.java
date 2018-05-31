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
import com.cloud.agent.api.RetrieveDiagnosticsCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterDetailsDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.domain.dao.DomainDetailsDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.dao.HostDao;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.StorageManager;
import com.cloud.storage.secondary.SecondaryStorageListener;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.user.AccountDetailsDao;
import com.cloud.user.AccountManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigDepotAdmin;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.framework.config.impl.DiagnosticsConfigDepotImpl;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDao;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDetailsDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    private String instance;
    private int mgmtPort = 8250;
    private boolean editConfiguration = false;

    private Long hostId = null;
    private String diagnosticsType = null;
    private String fileDetails = null;

    public Map<String, Object> getConfigParams() {
        return configParams;
    }

    public void setConfigParams(Map<String, Object> configParams) {
        this.configParams = configParams;
    }

    protected Map<String, Object> configParams = new HashMap<String, Object>();
    protected Pair<List<RetrieveDiagnosticsVO>, Integer> defaultDiagnosticsData;

    private Long _timeOut;
    private Boolean _enabledGC;
    private String _filePath;
    private Float _disableThreshold;
    private Long _fileAge;
    private Long _intervalGC;

    @Inject
    SecondaryStorageVmManager _ssVmMgr;
    private SecondaryStorageListener _listener;

    @Inject
    private AgentManager _agentMgr;

    @Inject
    public AccountManager _accountMgr;

    @Inject
    protected ConfigurationDao _configDao;
    HostDao hostDao;

    @Inject
    private VirtualMachineManager _itMgr;

    @Inject
    protected ResourceManager _resourceMgr;

    @Inject
    DataCenterDao _zoneDao;

    @Inject
    DataCenterDetailsDao _dcDetailsDao;

    @Inject
    RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    ClusterDetailsDao _clusterDetailsDao;

    @Inject
    ClusterDao _clusterDao;

    @Inject
    PrimaryDataStoreDao _storagePoolDao;

    @Inject
    private ConfigDepot _configDepot;

    @Inject
    private DiagnosticsConfigDepotImpl _diagnosticsDepot;

    @Inject
    StoragePoolDetailsDao _storagePoolDetailsDao;

    @Inject
    AccountDao _accountDao;

    @Inject
    AccountDetailsDao _accountDetailsDao;

    @Inject
    ImageStoreDao _imageStoreDao;

    @Inject
    ImageStoreDetailsDao _imageStoreDetailsDao;

    @Inject
    DomainDetailsDao _domainDetailsDao;

    @Inject
    DomainDao _domainDao;

    @Inject
    StorageManager _storageManager;

    @Inject
    ConfigDepot configDepot;
    @Inject
    MessageBus messageBus;

    @Inject
    RouterControlHelper routerControlHelper;

    @Inject
    protected VMInstanceDao _vmDao;

    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    ConfigKey<Boolean> enabledGCollector = new ConfigKey<>("Advanced", Boolean.class, "retrieveDiagnostics.gc.enabled",
            "true", "Garbage collection on/off switch (true|false", true, ConfigKey.Scope.Global);
    ConfigKey<String> RetrieveDiagnosticsFilePath = new ConfigKey<String>("Advanced", String.class, "retrieveDiagnostics.filepath",
            "/tmp", "File path to use on the management server for all temporary data. This allows CloudStack administrators to determine where best to place the files.", true, ConfigKey.Scope.Global);
    ConfigKey<Float> RetrieveDiagnosticsDisableThreshold = new ConfigKey<Float>("Advanced", Float.class, "retrieveDiagnostics.disablethreshold", "0.95",
            "The percentage disk space cut-off before API call will fail", true, ConfigKey.Scope.Global);

    ConfigKey<Long> RetrieveDiagnosticsFileAge = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.max.fileage", "86400",
            "The diagnostics file age in seconds before considered for garbage collection", true, ConfigKey.Scope.Global);

    ConfigKey<Long> RetrieveDiagnosticsInterval = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.gc.interval", "86400",
            "The interval between garbage collection executions in seconds", true, ConfigKey.Scope.Global);


/*    DiagnosticsKey<String> IPTablesRemove = new DiagnosticsKey<String>(String.class, "IPtables.remove", "The IPtables rules to be removed", null, null);
    DiagnosticsKey<String> IPTablesRetrieve = new DiagnosticsKey<String>(String.class, "IPTables.retrieve", "The IPTable rules to be retrieved", null, null);
    DiagnosticsKey<String> LOGFILES = new DiagnosticsKey<String>(String.class, "LogFiles", "Logfiles to be retrieved", null, null);
    DiagnosticsKey<String> PROPERTYFILES = new DiagnosticsKey<String>(String.class, "PropertyFiles", "Property files to be retrieved", null, null);
    DiagnosticsKey<String> DNSFILES = new DiagnosticsKey<String>(String.class, "DnsFiles", "Dns files to be retrieved", null, null);
    DiagnosticsKey<String> DHCPFILES = new DiagnosticsKey<String>(String.class, "DhcpFiles", "Dhcp files to be retrieved", null, null);
    DiagnosticsKey<String> USERDATA = new DiagnosticsKey<String>(String.class, "Userdata", "User data to be retrieved", null, null);
    DiagnosticsKey<String> LB = new DiagnosticsKey<String>(String.class, "LoadBalancing", "Load balancing files to be retrieved", null, null);
    DiagnosticsKey<String> VPN = new DiagnosticsKey<String>(String.class, "Vpn", "Logfiles to be retrieved", null, null);*/
    public RetrieveDiagnosticsServiceImpl() {
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Initialising configuring values for retrieve diagnostics api : " + name);
        }

        _timeOut = RetrieveDiagnosticsTimeOut.value();
        _fileAge = RetrieveDiagnosticsFileAge.value();
        _enabledGC = enabledGCollector.value();
        _filePath = RetrieveDiagnosticsFilePath.value();
        _disableThreshold = RetrieveDiagnosticsDisableThreshold.value();
        _intervalGC = RetrieveDiagnosticsInterval.value();
        if (params != null) {
            params.put(RetrieveDiagnosticsTimeOut.key(), (Long)RetrieveDiagnosticsTimeOut.value());
            params.put(RetrieveDiagnosticsFileAge.key(), (Long)RetrieveDiagnosticsFileAge.value());
            params.put(enabledGCollector.key(), (Boolean)enabledGCollector.value());
            params.put(RetrieveDiagnosticsFilePath.key(), (String)RetrieveDiagnosticsFilePath.value());
            params.put(RetrieveDiagnosticsDisableThreshold.key(), (Float)RetrieveDiagnosticsDisableThreshold.value());
            params.put(RetrieveDiagnosticsInterval.key(), (Long)RetrieveDiagnosticsInterval.value());
            return true;
        }

        return false;
    }

    protected Pair<List<RetrieveDiagnosticsVO>, Integer> loadDiagnosticsDataConfiguration() {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Retrieving diagnostics data values for retrieve diagnostics api : " + getConfigComponentName());
        }
        return _retrieveDiagnosticsDao.getDiagnosticsDetails();
    }

   public Pair<List<? extends Configuration>, Integer> searchForDiagnosticsConfigurations(final RetrieveDiagnosticsCmd cmd) {
       final Long zoneId = _accountMgr.checkAccessAndSpecifyAuthority(CallContext.current().getCallingAccount(), cmd.getId());
       Boolean _enabledGCollector = false;
       final Long timeOut = NumbersUtil.parseLong(cmd.getTimeOut(), 3600);
       final Float disableThreshold = NumbersUtil.parseFloat(cmd.getDisableThreshold(), 0.95f);
       String _enabledGC = cmd.getEnabledGC();
       if ("true".equalsIgnoreCase(_enabledGC)) {
           _enabledGCollector = true;
       }
       final Long intervalGC = NumbersUtil.parseLong(cmd.getIntervalGC(), 86400);
       final Long fileAge = NumbersUtil.parseLong(cmd.getFileAge(), 86400);
       final String filePath = cmd.getFilePath();

       final Object id = _accountMgr.checkAccessAndSpecifyAuthority(CallContext.current().getCallingAccount(), cmd.getId());

       final Filter searchFilter = new Filter(ConfigurationVO.class, "id", true, 0L, 0L);

       final SearchBuilder<ConfigurationVO> sb = _configDao.createSearchBuilder();
       sb.and("timeout", sb.entity().getValue(), SearchCriteria.Op.EQ);
       sb.and("disablethreshold", sb.entity().getValue(), SearchCriteria.Op.EQ);
       sb.and("enabledGC", sb.entity().getValue(), SearchCriteria.Op.EQ);
       sb.and("intervalGC", sb.entity().getValue(), SearchCriteria.Op.EQ);
       sb.and("fileage", sb.entity().getValue(), SearchCriteria.Op.EQ);
       sb.and("filepath", sb.entity().getValue(), SearchCriteria.Op.EQ);

       final SearchCriteria<ConfigurationVO> sc = sb.create();
       if (timeOut != null) {
           sc.setParameters("timeout", timeOut);
       }

       if (disableThreshold != null) {
           sc.setParameters("disablethreshold", disableThreshold);
       }

       if (_enabledGCollector != null) {
           sc.setParameters("enabledGC", _enabledGCollector);
       }

       if (intervalGC != null) {
           sc.setParameters("intervalGC", intervalGC);
       }

       if (fileAge != null) {
           sc.setParameters("fileage", fileAge);
       }

       if (filePath != null) {
           sc.setParameters("filepath", filePath);
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
                   s_logger.warn("ConfigDepot could not find parameter " + param.getName());
               }
           } else {
               s_logger.warn("Configuration item  " + param.getName() + " not found.");
           }
       }

        return new Pair<List<? extends Configuration>, Integer>(configVOList, configVOList.size());

   }

   @Override
   public List<RetrieveDiagnosticsVO> searchAndUpdateDiagnosticsType(RetrieveDiagnosticsCmd cmd, final String diagnosticsType) {
       final Filter searchFilter = new Filter(RetrieveDiagnosticsVO.class, "role", true, null, null);
       final SearchCriteria<RetrieveDiagnosticsVO> sc = _retrieveDiagnosticsDao.createSearchCriteria();
       List<RetrieveDiagnosticsVO> resultVo;

       final SearchCriteria<RetrieveDiagnosticsVO> ssc = _retrieveDiagnosticsDao.createSearchCriteria();
       ssc.addAnd("role", SearchCriteria.Op.LIKE, "%" + cmd.getEventType() + "%");
       ssc.addAnd("class", SearchCriteria.Op.LIKE, "%" + diagnosticsType + "%");
       ssc.addAnd("value", SearchCriteria.Op.LIKE, "%" + cmd.getOptionalListOfFiles() + "%");

       final List<RetrieveDiagnosticsVO> result = _retrieveDiagnosticsDao.search(ssc, searchFilter);
       final List<RetrieveDiagnosticsVO> diagnosticsVOList = new ArrayList<RetrieveDiagnosticsVO>();
       for (final RetrieveDiagnosticsVO param : result) {
           final RetrieveDiagnosticsVO diagnosticsVo = _retrieveDiagnosticsDao.findByName(param.getDiagnosticsType());
           if (diagnosticsVo != null) {
              final DiagnosticsKey key = _diagnosticsDepot.getKey(param.getRole());
              if (key != null) {
                 diagnosticsVo.setValue(key.valueIn(cmd.getEventType()) == null ? null : key.valueIn(cmd.getEventType()).toString());
                 diagnosticsVOList.add(diagnosticsVo);
              } else {
                 s_logger.warn("DiagnosticsConfigDepot could not find parameter " + param.getDiagnosticsType());
              }
              return diagnosticsVOList;
           } else {
                 s_logger.warn("Global setting item  " + param.getDiagnosticsType() + " not found in ");
           }

       }

       return result;

   }

    @Override
    public RetrieveDiagnosticsResponse getDiagnosticsFiles(final RetrieveDiagnosticsCmd cmd) throws InvalidParameterValueException, ConfigurationException {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Initialising configuring values for retrieve diagnostics api : " + getConfigComponentName());
        }
        String systemVmType = null;
        String diagnosticsType = null;
        String fileDetails = null;
        String[] filesToRetrieve = null;
        List<String> listOfDiagnosticsFiles;
        boolean diagnosticsTypeExists = false;
        if (configParams == null) {
            configParams = new HashMap<>();
        }
        if (configure(getConfigComponentName(), configParams)) {
            if (cmd != null) {
                if (!cmd.getDisableThreshold().isEmpty()) {
                    RetrieveDiagnosticsDisableThreshold = new ConfigKey<Float>("Advanced", Float.class, "", cmd.getDiagnosticsType(), "", true);
                }
                if (!cmd.getTimeOut().isEmpty()) {
                    RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "", cmd.getTimeOut(), "", true);
                }
                if (!cmd.getEnabledGC().isEmpty()) {
                    enabledGCollector = new ConfigKey<Boolean>("Advanced", Boolean.class, "", cmd.getEnabledGC(), "", true);
                }
                if (!cmd.getIntervalGC().isEmpty()) {
                    RetrieveDiagnosticsInterval = new ConfigKey<Long>("Advanced", Long.class, "", cmd.getIntervalGC(), "", true);
                }
                if (!cmd.getFileAge().isEmpty()) {
                    RetrieveDiagnosticsFileAge = new ConfigKey<Long>("Advanced", Long.class, "", cmd.getFileAge(), "", true);
                }
                if (!cmd.getFilePath().isEmpty()) {
                    RetrieveDiagnosticsFilePath = new ConfigKey<String>("Advanced", String.class, "", cmd.getFilePath(), "", true);
                }
                systemVmType = cmd.getEventType();
                diagnosticsType = cmd.getDiagnosticsType();
                if (systemVmType == null) {
                    throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "No host was selected.");
                }

                defaultDiagnosticsData = loadDiagnosticsDataConfiguration();
                diagnosticsType = cmd.getDiagnosticsType();
                if (diagnosticsType == null) {
                    listOfDiagnosticsFiles = getAllDefaultFilesForEachSystemVm(diagnosticsType);
                } else {
                    fileDetails = cmd.getOptionalListOfFiles();
                    if (fileDetails != null) {
                        fileDetails = cmd.getOptionalListOfFiles();
                        filesToRetrieve = fileDetails.split(",");
                        listOfDiagnosticsFiles = getDetailFilesAndDefaults(filesToRetrieve);

                    } else {
                        //retrieve default files from db for the system vm
                        for (RetrieveDiagnosticsVO defaultsVO : defaultDiagnosticsData.first()) {
                            String vmRole = defaultsVO.getRole();
                            String classDiagnosticsType = defaultsVO.getDiagnosticsType();
                            String defaultFileList = defaultsVO.getDefaultValue();
                            if (vmRole.equalsIgnoreCase(systemVmType) && classDiagnosticsType.equalsIgnoreCase(diagnosticsType)) {
                                filesToRetrieve = defaultFileList.split(",");
                                listOfDiagnosticsFiles = getDefaultFilesForVm(filesToRetrieve);
                                diagnosticsTypeExists = true;
                            }

                        }
                        if (!diagnosticsTypeExists) {
                            List<RetrieveDiagnosticsVO> diagnosticsVOList = searchAndUpdateDiagnosticsType(cmd, diagnosticsType);
                            if (diagnosticsVOList != null) {
                                for (RetrieveDiagnosticsVO diagnosticsVO : diagnosticsVOList) {


                                }
                            } else {
                                //diagnostics type entry does not exist in the DB, so insert it
                            }

                        } else {


                        }
 /*                       List<RetrieveDiagnosticsVO> result = null;
                        result = _retrieveDiagnosticsDao.listByName(cmd.getEventType()); //get the systemvmid
                        listOfDiagnosticsFiles = getDefaultFilesForVm(result);*/
                    }
                    /*final VMInstanceVO instance = _vmDao.findById(cmd.getId());
                    retrieveDiagnosticsFiles(cmd.getId(), diagnosticsType, listOfDiagnosticsFiles, instance );*/
                }
            }
        }
        return null;

    }

    protected List<String> getAllDefaultFilesForEachSystemVm(String diagnosticsType) {
        return null;
    }

    protected List<String> getDetailFilesAndDefaults(String[] detailFiles) {
        return null;
    }

    protected List<String> getDefaultFilesForVm(String[] defaultFiles) {
        return null;
    }

    protected boolean retrieveDiagnosticsFiles(Long hostId, String diagnosticsType, List<String> diagnosticsFiles, final VMInstanceVO systemVmId) {
        RetrieveDiagnosticsCommand command = new RetrieveDiagnosticsCommand();
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, routerControlHelper.getRouterControlIp(systemVmId.getId()));
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, systemVmId.getInstanceName() );

        Answer answer;
        try{
            answer = _agentMgr.send(systemVmId.getHostId(), command);
        }catch (Exception e){
            s_logger.error("Unable to send command");
            throw new InvalidParameterValueException("Agent unavailable");
        }
        return true;
    }

    @Override
    public String getConfigComponentName() {
        return RetrieveDiagnosticsServiceImpl.class.getSimpleName();
    }

    public DiagnosticsKey getDiagnosticsConfigKeys()
    {
        return null; //new DiagnosticsKey<?>[] { IPTablesRemove, IPTablesRetrieve, LOGFILES, PROPERTYFILES, DNSFILES, DHCPFILES, USERDATA, LB, VPN   };
    }

    @Override
    public ConfigKey<?>[] getConfigKeys()
    {
        return new ConfigKey<?>[] { RetrieveDiagnosticsFilePath, RetrieveDiagnosticsFileAge, RetrieveDiagnosticsInterval, RetrieveDiagnosticsTimeOut,
                RetrieveDiagnosticsDisableThreshold, enabledGCollector };
    }


    @Override
    public List<Class<?>> getCommands(){
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(RetrieveDiagnosticsCmd.class);
        return cmdList;
    }

/*    public SecondaryStorageVmVO startSecondaryStorageVm(long secStorageVmId) {
        return _ssVmMgr.startSecStorageVm(secStorageVmId);
    }

    public boolean generateSecStorageSetupCommand(Long ssHostId) {
        return _ssVmMgr.generateSetupCommand(ssHostId);
    }

    public boolean generateVMSetupCommand(Long ssAHostId) {
        return _ssVmMgr.generateVMSetupCommand(ssAHostId);
    }

    public void onAgentConnect(Long dcId, StartupCommand cmd) {
        onAgentConnect(dcId, cmd);
    }
*/


}
