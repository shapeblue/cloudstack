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
package com.cloud.hypervisor.kvm.resource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


import com.cloud.agent.api.to.HostTO;

public class KVMHAChecker extends KVMHABase implements Callable<Boolean> {
    private List<HAStoragePool> storagePools;
    private HostTO host;
    private boolean reportIfHeartBeatFailedForOneStoragePool;

    public KVMHAChecker(List<HAStoragePool> pools, HostTO host, boolean reportIfHeartBeatFailedForOneStoragePool) {
        this.storagePools = pools;
        this.host = host;
        this.reportIfHeartBeatFailedForOneStoragePool = reportIfHeartBeatFailedForOneStoragePool;
    }

    /*
     * True means heart beating is on going.
     * False means heart beating is stopped definitely.
     * Null means that its status can not be told (e.g. the host monitors no storage pool, or Ceph can not be reached),
     * which is not a dead host: the answer is not determined, and another host can be asked.
     *
     * The answer comes from the heartbeats only: it is what VM HA uses (KVMInvestigator, CheckOnHostCommand), which needs no out-of-band management.
     * The check of the activity of the VMs on their volumes (KVMHAVMActivityChecker) is only done by Host HA, which needs it.
     *
     * If the host is checked on more than one storage pool, it is dead if its heartbeat is stopped on one of them when
     * reportIfHeartBeatFailedForOneStoragePool is set, or else only if its heartbeat is stopped on all of them. It is
     * alive if its heartbeat is on going on one of them, and it is not told if that is not the case, and there is a pool
     * which can not tell.
     */
    @Override
    public Boolean hasHeartBeat() {
        String hostAndPools = String.format("host IP [%s] in pools [%s]", host.getPrivateNetwork().getIp(),
                storagePools.stream().map(pool -> pool.getPoolUUID()).collect(Collectors.joining(", ")));
        logger.debug("Checking heart beat with KVMHAChecker for {}", hostAndPools);

        if (storagePools.isEmpty()) {
            // this host monitors no storage pool, e.g. its agent is not upgraded yet and does not monitor the pools of the type
            // which a newer version does: it has nothing to tell about the host, which is not a dead host
            logger.debug("KVMHAChecker has no storage pool to check the heart beat for {}, so it can not tell whether the host is alive.", hostAndPools);
            return null;
        }

        boolean alive = false;
        boolean dead = false;
        boolean undetermined = false;
        for (HAStoragePool pool : storagePools) {
            Boolean poolResult = pool.getPool().hasHeartBeat(pool, host);
            if (poolResult == null) {
                undetermined = true;
            } else if (poolResult) {
                alive = true;
            } else {
                dead = true;
                if (reportIfHeartBeatFailedForOneStoragePool) {
                    break;
                }
            }
        }

        if (dead && (reportIfHeartBeatFailedForOneStoragePool || (!alive && !undetermined))) {
            logger.warn("All checks with KVMHAChecker for {} considered it as dead, which may trigger HA actions, such as fencing it and restarting its VMs on other hosts.", hostAndPools);
            return false;
        }
        if (alive) {
            return true;
        }
        logger.debug("KVMHAChecker can not tell whether the host is alive for {}.", hostAndPools);
        return null;
    }

    @Override
    public Boolean call() throws Exception {
        // logger.addAppender(new org.apache.log4j.ConsoleAppender(new
        // org.apache.log4j.PatternLayout(), "System.out"));
        return hasHeartBeat();
    }
}
