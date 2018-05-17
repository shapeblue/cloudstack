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
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterDetailsDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.domain.dao.DomainDetailsDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.host.dao.HostDao;
import com.cloud.offering.ServiceOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageManager;
import com.cloud.storage.secondary.SecondaryStorageListener;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.user.AccountDetailsDao;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.SystemVmLoadScanner;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.framework.messagebus.PublishScope;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDao;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDetailsDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RetrieveDiagnosticsManagerImpl extends ManagerBase implements RetrieveDiagnosticsManager, Configurable {

    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsManagerImpl.class);

    private int _maxVolumeSizeInGb = Integer.parseInt(Config.MaxVolumeSize.getDefaultValue());

    private Long timeOut;
    private Float disableThreshold;
    private String filePath;
    private Long fileAge;
    private Long intervalGC;
    private Boolean enabledGC;

    private String _instance;
    private int _mgmtPort = 8250;

    @Inject
    SecondaryStorageVmManager _ssVmMgr;
    private SecondaryStorageListener _listener;

    @Inject
    ConfigDepot _configDepot;

    @Inject
    private AgentManager _agentMgr;

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
    ClusterDetailsDao _clusterDetailsDao;

    @Inject
    ClusterDao _clusterDao;

    @Inject
    PrimaryDataStoreDao _storagePoolDao;

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
    MessageBus messageBus;

    public RetrieveDiagnosticsManagerImpl() {
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Start configuring retrieve diagnostics api : " + name);
        }
        Map<String, String> configs = _configDao.getConfiguration("retrieve-diagnostics", params);

        final String maxVolumeSizeInGbString = _configDao.getValue(Config.MaxVolumeSize.key());
        _maxVolumeSizeInGb = NumbersUtil.parseInt(maxVolumeSizeInGbString, Integer.parseInt(Config.MaxVolumeSize.getDefaultValue()));

        String retrieveDiagnosticsInstance = _configDao.getValue("retrieveDiagnostics.instance.instance");
        boolean _retrieveDiagnosticsInstance = false;
        if ("true".equalsIgnoreCase(retrieveDiagnosticsInstance)) {
            _retrieveDiagnosticsInstance = true;
        }

        String _enabledGC = _configDao.getValue("retrieveDiagnostics.gc.enabled");
        if ("true".equalsIgnoreCase(_enabledGC)) {
            enabledGC = true;
        }

        String _timeOut = _configDao.getValue("retrieveDiagnostics.retrieval.timeout");
        timeOut = NumbersUtil.parseLong(_timeOut, 3600);
        String _disableThreshold = _configDao.getValue("retrieveDiagnostics.disablethreshold");
        disableThreshold = NumbersUtil.parseFloat(_disableThreshold,0.95f);
        filePath = _configDao.getValue("retrieveDiagnostics.filepath");
        String _fileAge = _configDao.getValue("retrieveDiagnostics.max.fileage");
        fileAge = NumbersUtil.parseLong(_fileAge, 86400);
        String _intervalGC = _configDao.getValue("retrieveDiagnostics.gc.interval");
        intervalGC = NumbersUtil.parseLong(_intervalGC, 86400);
        NumbersUtil.parseLong((String)params.get("retrieveDiagnostics.gc.interval"), 86400);

        return true;
    }

    public Long getTimeOut() {
        return timeOut;
    }

    @Override
    public List<String> getConfiguration() throws ConfigurationException {
        return null;
    }

    public void setTimeOut(Long timeOut) {
        this.timeOut = timeOut;
    }

    public Float getDisableThreshold() {
        return disableThreshold;
    }

    public void setDisableThreshold(Float disableThreshold) {
        this.disableThreshold = disableThreshold;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileAge() {
        return fileAge;
    }

    public void setFileAge(Long fileAge) {
        this.fileAge = fileAge;
    }

    public Long getIntervalGC() {
        return intervalGC;
    }

    public void setIntervalGC(Long intervalGC) {
        this.intervalGC = intervalGC;
    }

    public Boolean getEnabledGC() {
        return enabledGC;
    }

    public void setEnabledGC(Boolean enabledGC) {
        this.enabledGC = enabledGC;
    }

    @Override
    public RetrieveDiagnosticsResponse getDiagnosticsFiles(RetrieveDiagnosticsCmd cmd) throws AgentUnavailableException {
        return null;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_CONFIGURATION_VALUE_EDIT, eventDescription = "updating configuration")
    public Configuration updateConfiguration(RetrieveDiagnosticsCmd cmd) {
        final Long userId = CallContext.current().getCallingUserId();
        final String name = cmd.getCfgName();
        String value = cmd.getValue();
        final Long timeOut = NumbersUtil.parseLong(cmd.getTimeOut(), 3600);
        final Float disableThreshold = NumbersUtil.parseFloat(cmd.getDisableThreshold(),0.95f);
        final String filePath = cmd.getFilePath();
        final Long fileAge = NumbersUtil.parseLong(cmd.getFileAge(), 86400);
        final Long intervalGC = NumbersUtil.parseLong(cmd.getIntervalGC(), 86400);
        final String enabledGC = cmd.getEnabledGC();
        CallContext.current().setEventDetails(" Name: " + name + " New Value: " + (name.toLowerCase().contains("password") ? "*****" : value == null ? "" : value));
        // check if config value exists
        final ConfigurationVO config = _configDao.findByName(name);

        if (value == null) {
            return _configDao.findByName(name);
        }
        value = value.trim();

        if (value.isEmpty() || value.equals("null")) {
            value = null;
        }

        String scope = null;
        Long id = null;
        int paramCountCheck = 0;

        if (timeOut != null) {
            setTimeOut(timeOut);
        }
        if (disableThreshold != null) {
            setDisableThreshold(disableThreshold);
        }
        if (filePath != null) {
            setFilePath(filePath);
        }
        if (fileAge != null) {
            setFileAge(fileAge);
        }
        if (intervalGC != null) {
            setIntervalGC(intervalGC);
        }
        if (enabledGC.isEmpty()) {
            setEnabledGC(true);
        }
        final String updatedValue = updateConfiguration(name, value);
        if (value == null && updatedValue == null || updatedValue.equalsIgnoreCase(value)) {
            return _configDao.findByName(name);
        } else {
            throw new CloudRuntimeException("Unable to update configuration parameter " + name);
        }

    }

    private String validateConfigurationValue(RetrieveDiagnosticsCmd cmd) {
         return null;
    }

    @Override
    @DB
    public String updateConfiguration(final String name, final String value) {


        //txn.commit();
        messageBus.publish(_name, EventTypes.EVENT_CONFIGURATION_VALUE_EDIT, PublishScope.GLOBAL, name);
        return _configDao.getValue(name);
    }

    @Override
    public String getConfigComponentName() {
        return ConfigurationManagerImpl.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return null;//new ConfigKey<?>[] {SystemVMUseLocalStorage};
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
