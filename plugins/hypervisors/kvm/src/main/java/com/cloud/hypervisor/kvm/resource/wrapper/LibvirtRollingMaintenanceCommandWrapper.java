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

package com.cloud.hypervisor.kvm.resource.wrapper;

import java.io.File;

import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.host.RollingMaintenanceCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.common.base.Strings;

@ResourceWrapper(handles =  RollingMaintenanceCommand.class)
public final class LibvirtRollingMaintenanceCommandWrapper extends CommandWrapper<RollingMaintenanceCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = Logger.getLogger(LibvirtRollingMaintenanceCommandWrapper.class);

    @Override
    public Answer execute(RollingMaintenanceCommand command, LibvirtComputingResource serverResource) {
        final String payload = command.getCommand();
        File folder = new File("/etc/cloudstack/agent/rolling.d");
        for (File f : folder.listFiles()) {
            final Script script = new Script(f.getAbsolutePath(), 60000L, s_logger);
            final OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
            script.add("noop"); // POC this can be received in command
            script.add(command.getType());
            if (!Strings.isNullOrEmpty(payload)) {
                script.add(payload);
            }
            String details = script.execute(parser);
            if (details == null) {
                details = parser.getLines();
            }
            s_logger.debug("Executing script in: " + details);
        }
        return new Answer(command);
    }
}
