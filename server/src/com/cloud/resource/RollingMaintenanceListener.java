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
import com.cloud.agent.Listener;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.RollingMaintenanceAnswer;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.storage.DownloadAnswer;
import com.cloud.exception.ConnectionException;
import com.cloud.host.Host;
import com.cloud.host.Status;
import com.cloud.storage.download.DownloadState;
import org.apache.log4j.Logger;

public class RollingMaintenanceListener implements Listener {

    public static final Logger s_logger = Logger.getLogger(RollingMaintenanceListener.class.getName());

    private AgentManager agentManager;
    private RollingMaintenanceManager manager;
    private long hostId;
    private boolean terminated;
    private boolean success;
    private String details;

    public RollingMaintenanceListener(RollingMaintenanceManager manager, AgentManager agentManager, long hostId) {
        this.agentManager = agentManager;
        this.manager = manager;
        this.hostId = hostId;
    }

    @Override
    public boolean processAnswers(long agentId, long seq, Answer[] answers) {
        boolean processed = false;
        if (answers != null & answers.length > 0) {
            if (answers[0] instanceof RollingMaintenanceAnswer) {
                final RollingMaintenanceAnswer answer = (RollingMaintenanceAnswer)answers[0];
                if (hostId != answer.getHostId()) {
                    return false;
                }
                terminated = answer.isTerminated();
                success = answer.isSuccess();
                details = answer.getDetails();
                processed = true;
            }
        }
        return processed;
    }

    @Override
    public boolean processCommands(long agentId, long seq, Command[] commands) {
        return false;
    }

    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
        return null;
    }

    @Override
    public void processHostAdded(long hostId) {

    }

    @Override
    public void processConnect(Host host, StartupCommand cmd, boolean forRebalance) throws ConnectionException {
        int a = 2;
    }

    @Override
    public boolean processDisconnect(long agentId, Status state) {
        return false;
    }

    @Override
    public void processHostAboutToBeRemoved(long hostId) {

    }

    @Override
    public void processHostRemoved(long hostId, long clusterId) {

    }

    @Override
    public boolean isRecurring() {
        return false;
    }

    @Override
    public int getTimeout() {
        return 0;
    }

    @Override
    public boolean processTimeout(long agentId, long seq) {
        return false;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getDetails() {
        return details;
    }
}
