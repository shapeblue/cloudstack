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

package com.cloud.upgrade.dao;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.log4j.Logger;

public class Upgrade41200to41201 implements DbUpgrade {

    final static Logger LOG = Logger.getLogger(Upgrade41200to41201.class);

    @Override
    public String[] getUpgradableVersionRange() {
        return new String[] {"4.12.0.0", "4.12.0.1"};
    }

    @Override
    public String getUpgradedVersion() {
        return "4.12.0.1";
    }

    @Override
    public boolean supportsRollingUpgrade() {
        return false;
    }

    @Override
    public InputStream[] getPrepareScripts() {
        final String scriptFile = "META-INF/db/schema-41200to41201.sql";
        final InputStream script = Thread.currentThread().getContextClassLoader().getResourceAsStream(scriptFile);
        if (script == null) {
            throw new CloudRuntimeException("Unable to find " + scriptFile);
        }

        return new InputStream[] {script};
    }

    @Override
    public void performDataMigration(Connection conn) {
        populateGuestOsDetails(conn);
    }

    @Override
    public InputStream[] getCleanupScripts() {
        final String scriptFile = "META-INF/db/schema-41200to41201-cleanup.sql";
        final InputStream script = Thread.currentThread().getContextClassLoader().getResourceAsStream(scriptFile);
        if (script == null) {
            throw new CloudRuntimeException("Unable to find " + scriptFile);
        }

        return new InputStream[] {script};
    }

    private void populateGuestOsDetails(Connection conn){
        final HashMap<String, MemoryValues> xenServerGuestOsMemoryMap = new HashMap<String, MemoryValues>(70);

        xenServerGuestOsMemoryMap.put("Ubuntu 18.04 (32-bit)", new MemoryValues(512l, 32 * 1024l));
        xenServerGuestOsMemoryMap.put("Ubuntu 18.04 (64-bit)", new MemoryValues(512l, 128 * 1024l));
        xenServerGuestOsMemoryMap.put("Ubuntu 18.10 (32-bit)", new MemoryValues(512l, 32 * 1024l));
        xenServerGuestOsMemoryMap.put("Ubuntu 18.10 (64-bit)", new MemoryValues(512l, 128 * 1024l));
        xenServerGuestOsMemoryMap.put("Ubuntu 19.04 (32-bit)", new MemoryValues(512l, 32 * 1024l));
        xenServerGuestOsMemoryMap.put("Ubuntu 19.04 (64-bit)", new MemoryValues(512l, 128 * 1024l));

        final String insertDynamicMemoryVal = "insert into guest_os_details(guest_os_id, name, value, display) select id,?, ?, 0 from guest_os where display_name = ?";

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(insertDynamicMemoryVal);

            for (String key: xenServerGuestOsMemoryMap.keySet()){
                ps.setString(1,"xenserver.dynamicMin");
                ps.setString(2,String.valueOf(xenServerGuestOsMemoryMap.get(key).getMin()));
                ps.setString(3, key);
                ps.executeUpdate();

                ps.setString(1,"xenserver.dynamicMax");
                ps.setString(2,String.valueOf(xenServerGuestOsMemoryMap.get(key).getMax()));
                ps.setString(3, key);
                ps.executeUpdate();
            }
        } catch(SQLException e) {
            throw new CloudRuntimeException("Unable to update guestOs details", e);
        } finally {
            try {
                if (ps != null && !ps.isClosed())  {
                    ps.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private static class MemoryValues {
        long max;
        long min;

        public MemoryValues(final long min, final long max) {
            this.min = min * 1024 * 1024;
            this.max = max * 1024 * 1024;
        }

        public long getMax() {
            return max;
        }

        public long getMin() {
            return min;
        }
    }

}
