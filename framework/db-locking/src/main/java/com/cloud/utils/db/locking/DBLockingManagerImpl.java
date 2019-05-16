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

package com.cloud.utils.db.locking;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.log4j.Logger;

import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.DbUtil;
import com.cloud.utils.exception.CloudRuntimeException;

public class DBLockingManagerImpl extends ManagerBase implements DBLockingManager, Configurable, PluggableService {
    private static final Logger LOG = Logger.getLogger(DBLockingManagerImpl.class);

    private static final ConfigKey<String> DBLockingServicePlugin = new ConfigKey<String>("Advanced", String.class,
            "db.locking.service.plugin", "default",
            "The database locking service plugin that will be used for concurrent DB read/write.",
            true, ConfigKey.Scope.Global);

    private List<DBLockingService> lockingServices = new ArrayList<>();
    // Define map of string -> plugin
    private Map<String, DBLockingService> lockingServiceMap = new HashMap<>();

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        // Add code on how to handle when this is configured
        return true;
    }

    @Override
    public boolean start() {
        // Add code on how to handle when this is started
        lockingServiceMap.clear();
        for (final DBLockingService lockingService : lockingServices) {
            lockingServiceMap.put(lockingService.getName(), lockingService);
        }
        DbUtil.setDBLockingManager(this);
        try {
            getDBLockingService().init();
        } catch (IOException e) {
            LOG.debug(String.format("Unable to init db-locking service! %s", getDBLockingService().getName()));
        }
        return true;
    }

    @Override
    public boolean stop() {
        // Add code on how to handle when this is stopped
        getDBLockingService().stopService();
        return true;
    }

    @Override
    public String getConfigComponentName() {
        return DBLockingManager.class.getSimpleName();
    }

    public void setLockingServices(List<DBLockingService> lockingServices) {
        this.lockingServices = lockingServices;
    }

    private DBLockingService getDBLockingService() {
        final String lockingServicePlugin = DBLockingServicePlugin.value();
        if (lockingServiceMap.containsKey(lockingServicePlugin)) {
            return lockingServiceMap.get(lockingServicePlugin);
        }
        throw new CloudRuntimeException("Invalid DB locking service configured!");
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[]{
                DBLockingServicePlugin
        };
    }

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<Class<?>>();
        // add API cmd classes here
        return cmdList;
    }

    @Override
    public boolean getGlobalLock(String name, int timeoutSeconds) {
        return getDBLockingService().lock(name, timeoutSeconds);
    }

    @Override
    public boolean releaseGlobalLock(String name) {
        return getDBLockingService().release(name);
    }

    @Override
    public String getLockingServiceName() {
        return getDBLockingService().getName();
    }
}
