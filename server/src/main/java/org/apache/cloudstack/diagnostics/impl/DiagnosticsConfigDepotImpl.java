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
package org.apache.cloudstack.diagnostics.impl;

import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.diagnostics.DiagnosticsKey;
import org.apache.cloudstack.diagnostics.dao.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigDepotAdmin;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;

import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;


import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.*;

public class DiagnosticsConfigDepotImpl implements ConfigDepot, ConfigDepotAdmin {

    private final static Logger s_logger = Logger.getLogger(ConfigDepotImpl.class);
    @Inject
    RetrieveDiagnosticsDao _diagnosticsDao;
    List<Configurable> _configurables;
    List<ScopedConfigStorage> _scopedStorages;
    Set<Configurable> _configured = Collections.synchronizedSet(new HashSet<Configurable>());

    HashMap<String, Pair<String, DiagnosticsKey<?>>> _allKeys = new HashMap<String, Pair<String, DiagnosticsKey<?>>>();

    HashMap<DiagnosticsKey.DiagnosticsType, Set<DiagnosticsKey<?>>> _diagnosticsTypeLevelsMap = new HashMap<DiagnosticsKey.DiagnosticsType, Set<DiagnosticsKey<?>>>();

    public DiagnosticsConfigDepotImpl() {
        DiagnosticsKey.init(this);
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.CONFIGURATIONFILES, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.DHCPFILES, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.DNS, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.LOGFILES, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.PROPERTYFILES, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.LB, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.USERDATA, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.VPN, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.IPTABLESretrieve, new HashSet<DiagnosticsKey<?>>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsType.IPTABLESremove, new HashSet<DiagnosticsKey<?>>());

    }

    public DiagnosticsKey<?> getKey(String key) {
        Pair<String, DiagnosticsKey<?>> value = _allKeys.get(key);
        return value != null ? value.second() : null;
    }

    @PostConstruct
    @Override
    public void populateConfigurations() {
        Date date = new Date();
        for (Configurable configurable : _configurables) {
            populateConfiguration(date, configurable);
        }
    }

    protected void populateConfiguration(Date date, Configurable configurable) {
        if (_configured.contains(configurable))
            return;

        s_logger.debug("Retrieving keys from " + configurable.getClass().getSimpleName());

        for (DiagnosticsKey<?> key : configurable..etConfigKeys()) {
            Pair<String, DiagnosticsKey<?>> previous = _allKeys.get(key.key());
            if (previous != null && !previous.first().equals(configurable.getConfigComponentName())) {
                throw new CloudRuntimeException("Configurable " + configurable.getConfigComponentName() + " is adding a key that has been added before by " +
                        previous.first() + ": " + key.toString());
            }
            _allKeys.put(key.key(), new Pair<String, DiagnosticsKey<?>>(configurable.getConfigComponentName(), key));

            createOrupdateConfigObject(date, configurable.getConfigComponentName(), key, null);

            if ((key.scope() != null) && (key.scope() != global())) {
                Set<DiagnosticsKey<?>> currentConfigs = _diagnosticsTypeLevelsMap.get(key.scope());
                currentConfigs.add(key);
            }
        }

        _configured.add(configurable);
    }

    private void createOrupdateConfigObject(Date date, String componentName, DiagnosticsKey<?> key, String value) {
        RetrieveDiagnosticsVO vo = _diagnosticsDao.findById(key.key());
        if (vo == null) {
            vo = new RetrieveDiagnosticsVO(componentName, key);
            vo.setUpdated(date);
            if (value != null) {
                vo.setValue(value);
            }
            _diagnosticsDao.persist(vo);
        } else {
            if (vo.isDynamic() != key.isDynamic() || !ObjectUtils.equals(vo.getDescription(), key.description()) || !ObjectUtils.equals(vo.getDefaultValue(), key.defaultValue()) ||
                    !ObjectUtils.equals(vo.getScope(), key.scope().toString()) ||
                    !ObjectUtils.equals(vo.getComponent(), componentName)) {
                vo.setDynamic(key.isDynamic());
                vo.setDescription(key.description());
                vo.setDefaultValue(key.defaultValue());
                vo.setScope(key.scope().toString());
                vo.setComponent(componentName);
                vo.setUpdated(date);
                _configDao.persist(vo);
            }
        }
    }

    @Override
    public void populateConfiguration(Configurable configurable) {
        populateConfiguration(new Date(), configurable);
    }

    @Override
    public List<String> getComponentsInDepot() {
        return new ArrayList<String>();
    }

    public RetrieveDiagnosticsDao global() {
        return _diagnosticsDao;
    }

    public ScopedConfigStorage findScopedConfigStorage(ConfigKey<?> config) {
        for (ScopedConfigStorage storage : _scopedStorages) {
            if (storage.getScope() == config.scope()) {
                return storage;
            }
        }

        throw new CloudRuntimeException("Unable to find config storage for this scope: " + config.scope() + " for " + config.key());
    }

    public List<ScopedConfigStorage> getScopedStorages() {
        return _scopedStorages;
    }

    @Inject
    public void setScopedStorages(List<ScopedConfigStorage> scopedStorages) {
        _scopedStorages = scopedStorages;
    }

    public List<Configurable> getConfigurables() {
        return _configurables;
    }

    @Inject
    public void setConfigurables(List<Configurable> configurables) {
        _configurables = configurables;
    }

    @Override
    public Set<ConfigKey<?>> getConfigListByScope(String scope) {
        return _scopeLevelConfigsMap.get(ConfigKey.Scope.valueOf(scope));
    }

    @Override
    public <T> void set(ConfigKey<T> key, T value) {
        _configDao.update(key.key(), value.toString());
    }

    @Override
    public <T> void createOrUpdateConfigObject(String componentName, ConfigKey<T> key, String value) {
        createOrupdateConfigObject(new Date(), componentName, key, value);

    }


}
