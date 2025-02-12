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
package org.apache.cloudstack.command;


import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import org.apache.cloudstack.framework.config.ConfigKey;

public interface ReconcileCommandService {

    ConfigKey<Boolean> ReconcileCommandsEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "reconcile.commands.enabled", "true",
            "Indicates whether the background task to reconcile the commands is enabled or not",
            false);

    ConfigKey<Integer> ReconcileCommandsInterval = new ConfigKey<>("Advanced", Integer.class,
            "reconcile.commands.interval", "60",
            "Interval (in seconds) for the background task to reconcile the commands",
            false);

    ConfigKey<Integer> ReconcileCommandsWorkers = new ConfigKey<>("Advanced", Integer.class,
            "reconcile.commands.workers", "100",
            "The Number of worker threads to reconcile the commands",
            false);

    void persistReconcileCommands(Long hostId, Long requestSequence, Command[] cmd);

    boolean updateReconcileCommand(long requestSeq, Command command, Answer answer, Command.State newStateByManagement, Command.State newStateByAgent);

    void processCommand(Command pingCommand, Answer pingAnswer);

    void processAnswers(long requestSeq, Command[] commands, Answer[] answers);

    void updateReconcileCommandToInterruptedByManagementServerId(long managementServerId);

    void updateReconcileCommandToInterruptedByHostId(long hostId);
}
