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
package com.cloud.resource;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.RollingMaintenanceCommand;
import com.cloud.host.Host;
import com.cloud.utils.Pair;
import org.apache.log4j.Logger;

import javax.inject.Inject;

public class RollingMaintenanceMonitorImpl implements RollingMaintenanceMonitor {

    private static long SLEEP_STEP = 100L;
    private RollingMaintenanceListener listener;

    public static final Logger s_logger = Logger.getLogger(RollingMaintenanceMonitorImpl.class.getName());

    @Inject
    private AgentManager agentManager;

    @Override
    public Pair<Boolean, String> startRollingMaintenance(Host host, long timeout) throws InterruptedException {
        listener = new RollingMaintenanceListener(null, null, host.getId());
        Command[] cmds = new Command[1];
        cmds[0] = new RollingMaintenanceCommand("cmd", "stage");
        agentManager.send(host.getId(), cmds, listener);
        long sleepTime = 0;
        while (!listener.isTerminated()) {
            Thread.sleep(SLEEP_STEP);
            sleepTime += SLEEP_STEP;
            if (sleepTime % 1000 == 0L) {
                s_logger.debug("Waiting for rolling maintenance step to be completed on host " + host);
            }
            if (sleepTime >= timeout) {
                s_logger.info("Rolling maintenance timeout for host " + host + ", aborting");
                return new Pair<>(false, listener.getDetails());
            }
        }
        return new Pair<>(listener.isSuccess(), listener.getDetails());
    }
}
