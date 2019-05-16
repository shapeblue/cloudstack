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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.log4j.Logger;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;

import com.cloud.utils.component.AdapterBase;

public class ZooKeeperDBLockingService extends AdapterBase implements DBLockingService {
    private static final Logger LOG = Logger.getLogger(DBLockingManagerImpl.class);
    private int clientPort = 21818; // none-standard
    private int numConnections = 5000;
    private int tickTime = 2000;
    private String tempDirectory = System.getProperty("java.io.tmpdir");
    private ZooKeeperServer zooKeeperServer;
    private NIOServerCnxnFactory serverFactory;
    private CuratorFramework curatorClient;
    private HashMap<String, InterProcessMutex> locks;

    ZooKeeperDBLockingService() {
        locks = new HashMap<>();
    }

    @Override
    public void init() throws IOException {

        File dir = new File(tempDirectory, "zookeeper").getAbsoluteFile();
        try {
            zooKeeperServer = new ZooKeeperServer(dir, dir, tickTime);
            serverFactory = new NIOServerCnxnFactory();
            serverFactory.configure(new InetSocketAddress(clientPort), numConnections);

            serverFactory.startup(zooKeeperServer);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        curatorClient = CuratorFrameworkFactory.newClient(String.format("127.0.0.1:%d", clientPort), retryPolicy);
        curatorClient.start();
    }

    @Override
    public boolean lock(String name, int timeoutSeconds) {
        boolean locked = false;
        if (!locks.containsKey(name)) {
            InterProcessMutex lock = new InterProcessMutex(curatorClient, String.format("%s%s", tempDirectory, name));
            try {
                locks.put(name, lock);
                locked = lock.acquire(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                locks.remove(name);
                LOG.debug(String.format("Unable to acquire ZooKeeper lock, %s!\n", name) + e);
            }
        }
        LOG.debug(String.format("Lock %s, Curator state: %s - %s", name, curatorClient.getState().toString(), locked));
        return locked;
    }

    @Override
    public boolean release(String name) {
        boolean released = false;
        if (locks.containsKey(name)) {
            InterProcessMutex lock = locks.get(name);
            if (lock != null) {
                try {
                    lock.release();
                    locks.remove(name);
                    released = true;
                } catch (Exception e) {
                    LOG.debug(String.format("Unable to release ZooKeeper lock, %s!\n", name) + e);
                }
            }
        }
        LOG.debug(String.format("Release %s, Curator state: %s - %s", name, curatorClient.getState().toString(), released));
        return released;
    }

    @Override
    public void stopService() {
        if (curatorClient != null) {
            curatorClient.close();
        }
        curatorClient = null;
        if (serverFactory != null) {
            serverFactory.shutdown();
        }
        serverFactory = null;
        zooKeeperServer = null;
    }

    @Override
    public String getName() {
        return "zooKeeper";
    }
}