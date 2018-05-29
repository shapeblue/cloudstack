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

package org.apache.cloudstack.diagnostics.dao;

import com.cloud.utils.component.ComponentLifecycle;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.cloud.utils.db.*;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.naming.ConfigurationException;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrieveDiagnosticsDaoImpl extends GenericDaoBase<RetrieveDiagnosticsVO, String> implements RetrieveDiagnosticsDao
{
    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsDaoImpl.class);
    private Map<String, Map<String,String>> _diagnosticsDetails = null;

    final SearchBuilder<RetrieveDiagnosticsVO> RoleSearch;
    final SearchBuilder<RetrieveDiagnosticsVO> ClassNameSearch;
    final SearchBuilder<RetrieveDiagnosticsVO> ValueSearch;

    public static final String UPDATE_DIAGNOSTICSDATA_SQL = "UPDATE diagnosticsdata SET value = ? WHERE name = ?";

    public RetrieveDiagnosticsDaoImpl() {
        RoleSearch = createSearchBuilder();
        RoleSearch.and("role", RoleSearch.entity().getRole(), SearchCriteria.Op.EQ);

        ClassNameSearch = createSearchBuilder();
        ClassNameSearch.and("class", ClassNameSearch.entity().getClassName(), SearchCriteria.Op.EQ);

        ValueSearch = createSearchBuilder();
        ValueSearch.and("value", ValueSearch.entity().getValue(), SearchCriteria.Op.EQ);
        setRunLevel(ComponentLifecycle.RUN_LEVEL_SYSTEM_BOOTSTRAP);
    }

    @PostConstruct
    public void init() throws ConfigurationException {
        configure(getName(), getConfigParams());
    }

    @Override
    public boolean update(String name, String category, String value) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        value = ("Hidden".equals(category) || "Secure".equals(category)) ? DBEncryptionUtil.encrypt(value) : value;
        try (PreparedStatement stmt = txn.prepareStatement(UPDATE_DIAGNOSTICSDATA_SQL);){
            stmt.setString(1, value);
            stmt.setString(2, name);
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            s_logger.warn("Unable to update Diagnostics default value", e);
        }
        return false;
    }

    @Override
    public String getValue(String name) {
        RetrieveDiagnosticsVO diagnostics = findByName(name);
        return (diagnostics == null) ? null : diagnostics.getValue();
    }

    @Override
    @DB
    public String getValueAndInitIfNotExist(String name, String className, String initValue) {
        String returnValue = initValue;
        try {
            RetrieveDiagnosticsVO diagnosticsDetail = findByName(name);
            if (diagnosticsDetail != null) {
                if (diagnosticsDetail.getValue() != null) {
                    returnValue = diagnosticsDetail.getValue();
                } else {
                    update(name, className, initValue);
                }
            } else {
                RetrieveDiagnosticsVO newDiagnostics = new RetrieveDiagnosticsVO(name, className, initValue);
                persist(newDiagnostics);
            }
            return returnValue;
        } catch (Exception e) {
            s_logger.warn("Unable to update Diagnostics default value", e);
            throw new CloudRuntimeException("Unable to initialize Diagnostics default variable: " + name);

        }
    }

    @Override
    public Map<String, String> getDiagnosticsDetails() {
        if (_diagnosticsDetails == null) {
            _diagnosticsDetails = new HashMap<String, Map<String, String>>();

            SearchCriteria<RetrieveDiagnosticsVO> sc = RoleSearch.create();
            sc.setParameters("role", "class", "value");
            List<RetrieveDiagnosticsVO> results = search(sc, null);
            Map<String, String> details = new HashMap<String, String>(results.size());
            for (RetrieveDiagnosticsVO result : results) {
                if ("password".equals(result.getClassName())) {
                    details.put(result.getClassName(), result.getValue());
                } else {
                    details.put(result.getClassName(), result.getValue());
                }
            }

            return details;

        }
        return null;
    }

    @Override
    public RetrieveDiagnosticsVO findByName(String name) {
        SearchCriteria<RetrieveDiagnosticsVO> sc = RoleSearch.create();
        sc.setParameters("name", name);
        return findOneIncludingRemovedBy(sc);
    }


    @Override
    public void invalidateCache() {
        _diagnosticsDetails = null;

    }





}
