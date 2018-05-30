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
package org.apache.cloudstack.framework.config.impl;

import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigDepotAdmin;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

public class DiagnosticsConfigDepotImpl implements ConfigDepot, ConfigDepotAdmin {

    private final static Logger s_logger = Logger.getLogger(DiagnosticsConfigDepotImpl.class);
    @Inject
    RetrieveDiagnosticsDao _diagnosticsDao;
    List<DiagnosticsKey> _diagnosticsTypeConfigurable;
    Set<DiagnosticsKey> _diagnosticsTypesConfigured = Collections.synchronizedSet(new HashSet<DiagnosticsKey>());

    Map<String, DiagnosticsKey> _allKeys = new HashMap<String, DiagnosticsKey>();


    HashMap<DiagnosticsKey.DiagnosticsEntryType, Set<DiagnosticsKey>> _diagnosticsTypeLevelsMap = new HashMap<DiagnosticsKey.DiagnosticsEntryType, Set<DiagnosticsKey>>();

    public DiagnosticsConfigDepotImpl() {
        DiagnosticsKey.init(this);
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.IPTABLES, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.DHCPFILES, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.DNS, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.IPTABLESremove, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.IPTABLESretrieve, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.LOGFILES, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.LB, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.PROPERTYFILES, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.USERDATA, new HashSet<DiagnosticsKey>());
        _diagnosticsTypeLevelsMap.put(DiagnosticsKey.DiagnosticsEntryType.VPN, new HashSet<DiagnosticsKey>());
    }

    public DiagnosticsKey getKey(String key) {
        DiagnosticsKey value = _allKeys.get(key);
        return value != null ? value : null;
    }

    @PostConstruct
    @Override
    public void populateConfigurations() {
        for (DiagnosticsKey diagnosticsClassType : _diagnosticsTypeConfigurable) {
            populateConfiguration(diagnosticsClassType);
        }
    }

    protected void populateConfiguration(DiagnosticsKey clazz) {
        if (_diagnosticsTypeConfigurable.contains(clazz))
            return;
        boolean diagnosticsTypeExists = false;

        s_logger.debug("Retrieving keys from " + clazz.getClass().getSimpleName());

        for (int i = 0; _diagnosticsTypeConfigurable != null; i++) {
            DiagnosticsKey previous = _allKeys.get(_diagnosticsTypeConfigurable.get(i));
            if (previous != null && previous.key().equals(clazz.key())) {
                diagnosticsTypeExists = true;
            }
            if (!diagnosticsTypeExists) {
                //Pair<String, DiagnosticsKey<?>> newDiagnosticsType = new Pair<String, DiagnosticsKey<?>>(clazz.key(), clazz);
                DiagnosticsKey newDiagnosticsType = new DiagnosticsKey(clazz.key(), clazz.getDiagnosticsClassType(), clazz.getDetail(), clazz.description());//?>>(clazz.key(), clazz);
                _allKeys.put(clazz.key(), newDiagnosticsType);
                createOrupdateDiagnosticsObject(clazz.key(), newDiagnosticsType );
            }

        }


    }

    private void createOrupdateDiagnosticsObject(String componentName,  DiagnosticsKey diagnosticsType) {
        RetrieveDiagnosticsVO vo = _diagnosticsDao.findById(diagnosticsType.key());
        //DiagnosticsKey diagnosticsKey = new DiagnosticsKey(diagnosticsType.getClass(), diagnosticsType.key(), "new diagnostics")
        if (vo == null) {
            vo = new RetrieveDiagnosticsVO(componentName, diagnosticsType);
            vo.setDiagnosticsType(diagnosticsType.key());
            vo.setRole(diagnosticsType.getRole());//to be given SystemVM type
            vo.setValue(diagnosticsType.getDetail());//to be populated
            _diagnosticsDao.persist(vo);
        } else {
            if (vo.getValue() != diagnosticsType.key() || !ObjectUtils.equals(vo.getRole(), diagnosticsType.getRole()) || !ObjectUtils.equals(vo.getDefaultValue(),
                    diagnosticsType.getDetail())) {
                vo.setRole(diagnosticsType.value()); //to be changed
                vo.setDiagnosticsType(diagnosticsType.key());
                vo.setValue(diagnosticsType.getDetail()); //to be changed
                _diagnosticsDao.persist(vo);
            }
        }
    }

    @Override
    public void populateConfiguration(Configurable configurable) {

    }

    @Override
    public List<String> getComponentsInDepot() {
        return new ArrayList<String>();
    }

    public RetrieveDiagnosticsDao global() {
        return _diagnosticsDao;
    }


    public List<DiagnosticsKey> getConfigurables() {
        return _diagnosticsTypeConfigurable;
    }

    @Inject
    public void setConfigurables(List<DiagnosticsKey> diagnosticsConfigurables) {
        _diagnosticsTypeConfigurable = diagnosticsConfigurables;
    }

    @Override
    public Set<ConfigKey<?>> getConfigListByScope(String scope) {
        return null;
    }

    @Override
    public <T> void set(ConfigKey<T> key, T value) {
        //_diagnosticsDao.update(key.key(), value.toString());

    }

    @Override
    public <T> void createOrUpdateConfigObject(String componentName, ConfigKey<T> key, String value) {
        //createOrupdateConfigObject(new Date(), componentName, key, value);

    }

    @Override
    public ConfigKey<?> get(String key) {
        return null;
    }


}
