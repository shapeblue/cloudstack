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
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.router.RouterControlHelper;
import com.cloud.resource.ResourceManager;
import com.cloud.user.AccountManager;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.DiagnosticsConfigDepot;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RetrieveDiagnosticsServiceImpl extends ManagerBase implements RetrieveDiagnosticsService, Configurable {

    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsServiceImpl.class);

    private Long _timeOut;

    protected Map<String, Object> configParams = new HashMap<String, Object>();

    HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();

    @Inject
    private AgentManager _agentMgr;

    @Inject
    public AccountManager _accountMgr;

    @Inject
    protected ConfigurationDao _configDao;

    @Inject
    protected ResourceManager _resourceMgr;

    @Inject
    RetrieveDiagnosticsDao _retrieveDiagnosticsDao;

    @Inject
    ConfigDepot _configDepot;

    @Inject
    DiagnosticsConfigDepot _diagnosticsDepot;

    @Inject
    ConfigDepot configDepot;

    @Inject
    RouterControlHelper routerControlHelper;

    @Inject
    HostDetailsDao _hostDetailDao;

    @Inject
    protected VMInstanceDao _vmDao;

    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);

    public RetrieveDiagnosticsServiceImpl() {
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Initialising configuring values for retrieve diagnostics api : " + name);
        }

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
        if (s_logger.isInfoEnabled()) {
            s_logger.info("Retrieving diagnostics data values for retrieve diagnostics api : " + getConfigComponentName());
        }
        List<RetrieveDiagnosticsVO> listVO = _retrieveDiagnosticsDao.retrieveAllDiagnosticsData();
        DiagnosticsKey diagnosticsKey = null;
        List<DiagnosticsKey> arrDiagnosticsKeys = null;
        for (RetrieveDiagnosticsVO vo : listVO) {
            if (allDefaultDiagnosticsTypeKeys != null) {
                List<DiagnosticsKey> value = get(vo.getType());
                if (value == null) {
                    diagnosticsKey = new DiagnosticsKey(vo.getRole(), vo.getType(), vo.getDefaultValue(), "");
                    arrDiagnosticsKeys = new ArrayList<>();
                    arrDiagnosticsKeys.add(diagnosticsKey);
                    allDefaultDiagnosticsTypeKeys.put(vo.getType(), arrDiagnosticsKeys);
                } else {
                    for (DiagnosticsKey keyValue : value) {
                        if (!keyValue.getRole().equalsIgnoreCase(vo.getRole()) && !keyValue.getDiagnosticsClassType().equalsIgnoreCase(vo.getType())) {
                            arrDiagnosticsKeys.add(keyValue);
                            allDefaultDiagnosticsTypeKeys.put(vo.getType(), arrDiagnosticsKeys);
                        }
                    }
                }
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
                   s_logger.warn("ConfigDepot could not find parameter " + param.getName());
               }
           } else {
               s_logger.warn("Configuration item  " + param.getName() + " not found.");
           }
       }
       return new Pair<List<? extends Configuration>, Integer>(configVOList, configVOList.size());
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
        String[] listOfDiagnosticsFiles = null;
        List<String> diagnosticsFiles = new ArrayList<>();
        if (configParams == null) {
            configParams = new HashMap<>();
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
                    listOfDiagnosticsFiles = getAllDefaultFilesForEachSystemVm(diagnosticsType,systemVmType);
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
                final VMInstanceVO instance = _vmDao.findById(cmd.getId());
                retrieveDiagnosticsFiles(cmd.getId(), diagnosticsType, diagnosticsFiles, instance);
            }
        }
        return null;

    }

    @Override
    public RetrieveDiagnosticsResponse createRetrieveDiagnosticsResponse(Host host) {
        Map<String, String> rdDetails = _hostDetailDao.findDetails(host.getId());
        RetrieveDiagnosticsResponse response = new RetrieveDiagnosticsResponse();
        response.setSuccess(true);
        response.setTimeout(rdDetails.get("timeout"));
        return response;
    }

    protected String[] getAllDefaultFilesForEachSystemVm(String diagnosticsType, String systemVmType) {
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

}
