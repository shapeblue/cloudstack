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



}
