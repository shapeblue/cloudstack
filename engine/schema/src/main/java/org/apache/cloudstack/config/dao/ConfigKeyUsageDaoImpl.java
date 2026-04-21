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
package org.apache.cloudstack.config.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.cloudstack.config.ConfigKeyUsageGroupVO;
import org.apache.cloudstack.config.ConfigKeyUsageVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.cloud.utils.Pair;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class ConfigKeyUsageDaoImpl extends GenericDaoBase<ConfigKeyUsageVO, Long> implements ConfigKeyUsageDao {

    private static final String GROUPED_SELECT_PREFIX =
            "SELECT api_name, config_key, COUNT(*) AS usage_count FROM config_key_usage";
    private static final String GROUPED_COUNT_PREFIX =
            "SELECT COUNT(*) FROM (SELECT 1 FROM config_key_usage";

    private final SearchBuilder<ConfigKeyUsageVO> apiNameAndDifferentContextSearch;

    public ConfigKeyUsageDaoImpl() {
        super();
        apiNameAndDifferentContextSearch = createSearchBuilder();
        apiNameAndDifferentContextSearch.and("apiName", apiNameAndDifferentContextSearch.entity().getApiName(), SearchCriteria.Op.EQ);
        apiNameAndDifferentContextSearch.and("contextId", apiNameAndDifferentContextSearch.entity().getContextId(), SearchCriteria.Op.NEQ);
        apiNameAndDifferentContextSearch.done();
    }

    @Override
    public int removeByApiNameAndDifferentContext(String apiName, String currentContextId) {
        SearchCriteria<ConfigKeyUsageVO> sc = apiNameAndDifferentContextSearch.create();
        sc.setParameters("apiName", apiName);
        if (currentContextId != null) {
            sc.setParameters("contextId", currentContextId);
        }
        return expunge(sc);
    }

    @Override
    public Pair<List<ConfigKeyUsageVO>, Integer> searchAndCount(SearchCriteria<ConfigKeyUsageVO> sc, Filter filter) {
        return super.searchAndCount(sc, filter);
    }

    @Override
    public Pair<List<ConfigKeyUsageGroupVO>, Integer> searchGroupedByApiNameAndConfigKey(String apiName, String configKey,
            Long startIndex, Long pageSize) {
        StringBuilder whereClause = new StringBuilder();
        List<Object> whereParameters = new ArrayList<>();

        if (StringUtils.isNotBlank(apiName)) {
            whereClause.append(whereClause.length() == 0 ? " WHERE " : " AND ");
            whereClause.append("api_name = ?");
            whereParameters.add(apiName);
        }

        if (StringUtils.isNotBlank(configKey)) {
            whereClause.append(whereClause.length() == 0 ? " WHERE " : " AND ");
            whereClause.append("config_key = ?");
            whereParameters.add(configKey);
        }

        String groupedCountSql = GROUPED_COUNT_PREFIX + whereClause + " GROUP BY api_name, config_key) grouped";

        StringBuilder groupedSelectSql = new StringBuilder(GROUPED_SELECT_PREFIX)
                .append(whereClause)
                .append(" GROUP BY api_name, config_key ORDER BY api_name ASC, config_key ASC");

        boolean paginate = pageSize != null && pageSize > 0;
        long offset = startIndex != null && startIndex >= 0 ? startIndex : 0L;
        if (paginate) {
            groupedSelectSql.append(" LIMIT ? OFFSET ?");
        }

        TransactionLegacy txn = TransactionLegacy.currentTxn();
        try {
            int totalGroupCount;
            try (PreparedStatement countStmt = txn.prepareAutoCloseStatement(groupedCountSql)) {
                setStatementParameters(countStmt, whereParameters);
                try (ResultSet rs = countStmt.executeQuery()) {
                    totalGroupCount = rs.next() ? rs.getInt(1) : 0;
                }
            }

            List<ConfigKeyUsageGroupVO> groups = new ArrayList<>();
            try (PreparedStatement groupedStmt = txn.prepareAutoCloseStatement(groupedSelectSql.toString())) {
                int parameterIndex = setStatementParameters(groupedStmt, whereParameters);
                if (paginate) {
                    groupedStmt.setLong(parameterIndex++, pageSize);
                    groupedStmt.setLong(parameterIndex, offset);
                }

                try (ResultSet rs = groupedStmt.executeQuery()) {
                    while (rs.next()) {
                        groups.add(new ConfigKeyUsageGroupVO(rs.getString("api_name"), rs.getString("config_key"),
                                rs.getLong("usage_count")));
                    }
                }
            }

            return new Pair<>(groups, totalGroupCount);
        } catch (SQLException e) {
            throw new CloudRuntimeException("Failed to search grouped config key usage records", e);
        }
    }

    private int setStatementParameters(PreparedStatement stmt, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            stmt.setObject(index++, parameter);
        }
        return index;
    }
}
