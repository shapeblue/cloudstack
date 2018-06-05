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

import org.apache.cloudstack.framework.config.DiagnosticsConfigDepot;
import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DiagnosticsConfigDepotImpl implements DiagnosticsConfigDepot {

    private final static Logger s_logger = Logger.getLogger(DiagnosticsConfigDepotImpl.class);
    @Inject
    RetrieveDiagnosticsDao _diagnosticsDao;
    HashSet<DiagnosticsKey> diagnosticsKeyHashSet = new HashSet<>();

    DiagnosticsKey IPTablesRemove = new DiagnosticsKey("SecondaryStorageVm", "IPTables.remove", "IPTablesremove.sh", "Remove IP table rules");
    DiagnosticsKey IPTablesRetrieve = new DiagnosticsKey("SecondaryStorageVm", "IPTables.remove", "IPTablesremove.sh", "Remove IP table rules");
    DiagnosticsKey LOGFILES = new DiagnosticsKey( "ConsoleProxy", "LOGFILES", "agent.log,management.log,cloud.log", "Log files on Console Proxy VM");
    DiagnosticsKey PROPERTYFILES = new DiagnosticsKey("VR", "PROPERTYFILES", "console.property,cloud.property,server.property",  "Property files to be retrieved");
    DiagnosticsKey DNSFILES = new DiagnosticsKey("VR", "DNSFILES", "", "Dns files to be retrieved");
    DiagnosticsKey DHCPFILES = new DiagnosticsKey("VR", "DHCPFILES", "DHCPmasq",  "Dhcp files to be retrieved");
    DiagnosticsKey USERDATA = new DiagnosticsKey("ConsoleProxy", "USERDATA", "", "User data to be retrieved");
    DiagnosticsKey LB = new DiagnosticsKey("SecondaryStorageVm", "LOADBALANCING", "", "Load balancing files to be retrieved");
    DiagnosticsKey VPN = new DiagnosticsKey("VR", "VPN", "", "VPN diagnostics files to be retrieved");

    DiagnosticsKey[] _diagnosticsTypeConfigurable = new DiagnosticsKey[] { IPTablesRemove, IPTablesRetrieve, LOGFILES, DNSFILES, DHCPFILES, LB, VPN, USERDATA, PROPERTYFILES };
    //Set<DiagnosticsKey> _diagnosticsTypesConfigured = Collections.synchronizedSet(new HashSet<DiagnosticsKey>());

    //HashMap<String, Pair<String, DiagnosticsKey>> _allKeys = new HashMap<String, Pair<String, DiagnosticsKey>>();


    HashMap<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey> diagnosticsKeyHashMap = new HashMap<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey>();

    public DiagnosticsConfigDepotImpl() {
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.IPTABLESremove, IPTablesRemove);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.IPTABLESretrieve, IPTablesRetrieve);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.LOGFILES, LOGFILES);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.PROPERTYFILES, PROPERTYFILES);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.DNS, DNSFILES);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.DHCPFILES, DHCPFILES);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.USERDATA, USERDATA);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.LB, LB);
        diagnosticsKeyHashMap.put(DiagnosticsKey.DiagnosticsEntryType.VPN, VPN);
    }

    @Override
    public DiagnosticsKey getKey(DiagnosticsKey.DiagnosticsEntryType key) {
        if (diagnosticsKeyHashMap.containsKey(key)) {
            diagnosticsKeyHashMap.get(key);
        }
        return null;
    }

    @Override
    public void populateDiagnostics() {
        for (Map.Entry<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey> entry : diagnosticsKeyHashMap.entrySet()) {
            populateDiagnostics(entry.getValue());
        }
    }

    @Override
    public void populateDiagnostics(DiagnosticsKey clazz) {
        boolean diagnosticsTypeExists = false;
        for (Map.Entry<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey> key : diagnosticsKeyHashMap.entrySet()) {
            if (key.equals(clazz) && key.getValue().getRole().equals(clazz.getRole()) && key.getValue().getDiagnosticsClassType().equals(clazz.getDiagnosticsClassType())) {
                if (!key.getValue().getDetail().equals(clazz.getDetail())) {
                    key.getValue().setDetail(clazz.getDetail());
                }
                diagnosticsTypeExists = true;
            }
        }
        if (!diagnosticsTypeExists) {
            String type = clazz.getDiagnosticsClassType();
            DiagnosticsKey.DiagnosticsEntryType key = DiagnosticsKey.DiagnosticsEntryType.valueOf(type);
            DiagnosticsKey newDiagnosticsType = new DiagnosticsKey(clazz.getRole(), clazz.getDiagnosticsClassType(), clazz.getDetail(), clazz.description());
            diagnosticsKeyHashMap.put(key, newDiagnosticsType);
            createOrUpdateDiagnosticObject(key, newDiagnosticsType );
        }

    }

    @Override
    public void createOrUpdateDiagnosticObject(DiagnosticsKey.DiagnosticsEntryType type, DiagnosticsKey diagnosticsType) {
        List<RetrieveDiagnosticsVO> voList = _diagnosticsDao.findByEntityType(type.toString());
        //DiagnosticsKey diagnosticsKey = new DiagnosticsKey(diagnosticsType.getClass(), diagnosticsType.key(), "new diagnostics")
        for (RetrieveDiagnosticsVO vo : voList) {
            if (vo == null) {
                vo = new RetrieveDiagnosticsVO(diagnosticsType.getRole(), diagnosticsType.getDiagnosticsClassType(), diagnosticsType.getDetail());
                vo.setDiagnosticsType(diagnosticsType.getRole());
                vo.setRole(diagnosticsType.getDiagnosticsClassType());//to be given SystemVM type
                vo.setDefaultValue(diagnosticsType.getDetail());//to be populated
                _diagnosticsDao.persist(vo);

            } else {
                if (vo.getDefaultValue() != diagnosticsType.getDiagnosticsClassType() || !ObjectUtils.equals(vo.getRole(), diagnosticsType.getRole()) || !ObjectUtils.equals(vo.getDefaultValue(),
                        diagnosticsType.getDetail())) {
                    vo.setRole(diagnosticsType.value()); //to be changed
                    vo.setDiagnosticsType(diagnosticsType.key());
                    vo.setDefaultValue(diagnosticsType.getDetail()); //to be changed
                    _diagnosticsDao.persist(vo);
                }
            }
        }
    }

    @Override
    public HashMap<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey> getDiagnosticsTypeLevelsMap() {
        return diagnosticsKeyHashMap;
    }

    public void setDiagnosticsTypeLevelsMap(HashMap<DiagnosticsKey.DiagnosticsEntryType, DiagnosticsKey> diagnosticsTypeLevelsMap) {
        this.diagnosticsKeyHashMap = diagnosticsTypeLevelsMap;
    }

    public RetrieveDiagnosticsDao global() {
        return _diagnosticsDao;
    }

    @Override
    public void set(DiagnosticsKey key, String value) {

    }

}
