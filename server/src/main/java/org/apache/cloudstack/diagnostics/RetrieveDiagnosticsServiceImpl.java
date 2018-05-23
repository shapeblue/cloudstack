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
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterDetailsDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.domain.dao.DomainDetailsDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.host.dao.HostDao;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.StorageManager;
import com.cloud.storage.secondary.SecondaryStorageListener;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.user.AccountDetailsDao;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.vm.VirtualMachineManager;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.diagnostics.dao.RetrieveDiagnosticsDao;
import org.apache.cloudstack.diagnostics.impl.RetrieveDiagnosticsVO;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDao;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDetailsDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.*;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    private String _instance;
    private int _mgmtPort = 8250;
    private boolean editConfiguration = false;

    public Map<String, Object> get_configParams() {
        return _configParams;
    }

    public void set_configParams(Map<String, Object> _configParams) {
        this._configParams = _configParams;
    }

    protected Map<String, Object> _configParams = new HashMap<String, Object>();

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

        return true;
    }

    public boolean loadDiagnosticsConfiguration(final RetrieveDiagnosticsVO retrieveDiagnosticsVO) {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Retrieving configuring values for retrieve diagnostics api : " + getConfigComponentName());
        }
        Map<String, String> configDetails = _retrieveDiagnosticsDao.getDiagnosticsDetails();
        if (configDetails != null) {

        }
        return false;
    }

    @Override
    public RetrieveDiagnosticsResponse getDiagnosticsFiles(RetrieveDiagnosticsCmd cmd) throws AgentUnavailableException, ConfigurationException {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Initialising configuring values for retrieve diagnostics api : " + getConfigComponentName());
        }
        boolean enableGCollector = false;
        if (_configParams == null) {
            _configParams = new HashMap<>();
        }
        if (configure(getConfigComponentName(), _configParams)) {
            if (cmd != null) {
                String _timeOut = cmd.getTimeOut();
                String _disableThreshold = cmd.getDisableThreshold();
                String _enableGC = cmd.getEnabledGC();
                String _intervalGC = cmd.getIntervalGC();
                String _fileAge = cmd.getFileAge();
                final String _filePath = cmd.getFilePath();
                if (!_disableThreshold.isEmpty() || !_timeOut.isEmpty() || !_enableGC.isEmpty()
                            || !_intervalGC.isEmpty() || !_fileAge.isEmpty() || !_filePath.isEmpty()) {
                    final Long _ttimeOut = NumbersUtil.parseLong(_timeOut, 3600);
                    final Float _ddisableThreshold = NumbersUtil.parseFloat(_disableThreshold, 0.95f);
                    final Long _ffileAge = NumbersUtil.parseLong(_fileAge, 86400);
                    final Long _iintervalGC = NumbersUtil.parseLong(_intervalGC, 86400);

                    if ("true".equalsIgnoreCase(_enableGC)) {
                        enableGCollector = true;
                    }

                }
            }

        }
        Long instanceId = cmd.getId();
        List<String> diagnosticsfilesToRetrieve = cmd.getListOfDiagnosticsFiles();
        if (diagnosticsfilesToRetrieve != null) {
            for (String file : diagnosticsfilesToRetrieve) {

            }
        } else {
            //get list of default files from the database table for this diagnostics type


        }



        return null;
    }


    @Override
    public String getConfigComponentName() {
        return RetrieveDiagnosticsServiceImpl.class.getSimpleName();
    }


    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { RetrieveDiagnosticsTimeOut, enabledGCollector, RetrieveDiagnosticsFilePath, RetrieveDiagnosticsDisableThreshold,
                RetrieveDiagnosticsFileAge, RetrieveDiagnosticsInterval };
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
