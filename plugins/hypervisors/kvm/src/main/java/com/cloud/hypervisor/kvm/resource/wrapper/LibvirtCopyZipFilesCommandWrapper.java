//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.Script2;
import org.apache.cloudstack.diagnostics.CopyZipFilesCommand;
import org.apache.log4j.Logger;

@ResourceWrapper(handles = CopyZipFilesCommand.class)
public class LibvirtCopyZipFilesCommandWrapper extends CommandWrapper<CopyZipFilesCommand, Answer, LibvirtComputingResource> {
    private static final Logger LOGGER = Logger.getLogger(LibvirtCopyZipFilesCommandWrapper.class);

    public final Logger logger = null;

    @Override
    public Answer execute(CopyZipFilesCommand command, LibvirtComputingResource libvirtComputingResource) {
        final String permKey = "/root/.ssh/id_rsa.cloud";
        String vmIp = command.getRouterIP();
        String filename = command.getZipFilesDir();
        boolean success = true;
        String details = "Copying zip files: " + vmIp + ", file: " + filename;
        LOGGER.info(details);
        String cmdLine = String.format("/usr/bin/scp -P 3922 -o StrictHostKeyChecking=no -i %s root@%s:%s %s", permKey, vmIp, filename, filename);
        Script2 cmd = new Script2("/bin/bash", LOGGER);
        cmd.add("-c");
        cmd.add(cmdLine);
        String result = cmd.execute();
        return new Answer(command, true, result);
    }
}