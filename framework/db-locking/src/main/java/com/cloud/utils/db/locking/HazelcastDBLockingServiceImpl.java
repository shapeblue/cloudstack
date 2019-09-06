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
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.cloud.utils.component.AdapterBase;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

public class HazelcastDBLockingServiceImpl extends AdapterBase implements DBLockingService {
    private static final Logger LOG = Logger.getLogger(DBLockingManagerImpl.class);
    private final static int NETWORK_PORT = 22818; // non-standard
    private final static int PORT_COUNT = 50;
    private int tickTime = 2000;
    private String tempDirectory = System.getProperty("java.io.tmpdir");
    private HashMap<String, FencedLock> locks;

    private static HazelcastInstance hazelcastInstance = null;

    @Override
    public void init() throws IOException {
        Config config = new Config();
        config.getNetworkConfig().setPort(29101);
        config.getNetworkConfig().setPortAutoIncrement(true);
        config.getNetworkConfig().setPortCount(50);

        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        networkConfig.getInterfaces().setEnabled(true);
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true);

        joinConfig.getTcpIpConfig().addMember("127.0.0.1");
        networkConfig.getInterfaces().addInterface("127.0.0.1");

        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @Override
    public boolean lock(String name, int timeoutSeconds) {
        FencedLock lock = hazelcastInstance.getCPSubsystem().getLock("name");
        try {
            if (lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
                locks.put(name, lock);
                return true;
            }
        } catch (Exception e) {
            LOG.debug(String.format("Unable to acquire Hazelcast lock, %s!\n", name) + e);
        }
        return false;
    }

    @Override
    public boolean release(String name) {
        boolean released = false;
        if (locks.containsKey(name)) {
            FencedLock lock = locks.get(name);
            if (lock != null) {
                try {
                    lock.unlock();
                    locks.remove(name);
                    released = true;
                } catch (Exception e) {
                    LOG.debug(String.format("Unable to release Hazelcast lock, %s!\n", name) + e);
                }
            }
        }
        LOG.debug(String.format("Release %s: %s", name, released));
        return released;
    }

    @Override
    public void stopService() {

    }

    @Override
    public String getName() {
        return "hazelcast";
    }
}
