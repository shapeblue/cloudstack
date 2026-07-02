//Licensed to the Apache Software Foundation (ASF) under one
//or more contributor license agreements.  See the NOTICE file
//distributed with this work for additional information
//regarding copyright ownership.  The ASF licenses this file
//to you under the Apache License, Version 2.0 (the
//"License"); you may not use this file except in compliance
//the License.  You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,
//software distributed under the License is distributed on an
//"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//KIND, either express or implied.  See the License for the
//specific language governing permissions and limitations
//under the License.

package com.cloud.hypervisor.kvm.resource.wrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.cloudstack.backup.StartBackupAnswer;
import org.apache.cloudstack.backup.StartBackupCommand;
import org.apache.cloudstack.utils.cryptsetup.KeyFile;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.StringUtils;
import com.cloud.utils.script.Script;

@ResourceWrapper(handles = StartBackupCommand.class)
public class LibvirtStartBackupCommandWrapper extends CommandWrapper<StartBackupCommand, Answer, LibvirtComputingResource> {
    protected Logger logger = LogManager.getLogger(getClass());

    @Override
    public Answer execute(StartBackupCommand cmd, LibvirtComputingResource resource) {
        if (cmd.isStoppedVM()) {
            return handleStoppedVmBackup(cmd, cmd.getToCheckpointId());
        }
        return handleRunningVmBackup(cmd, resource);
    }

    public Answer handleRunningVmBackup(StartBackupCommand cmd, LibvirtComputingResource resource) {
        String vmName = cmd.getVmName();
        String toCheckpointId = cmd.getToCheckpointId();
        String fromCheckpointId = cmd.getFromCheckpointId();
        Long fromCheckpointCreateTime = cmd.getFromCheckpointCreateTime();
        String socket = cmd.getSocket();

        try {
            if (StringUtils.isNotBlank(fromCheckpointId)) {
                Answer redefineAnswer = ensureFromCheckpointExists(cmd, fromCheckpointId, fromCheckpointCreateTime);
                if (redefineAnswer != null) {
                    return redefineAnswer;
                }
            }

            File dir = new File("/tmp/imagetransfer");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Create backup XML
            String backupXml = createBackupXml(cmd, fromCheckpointId, socket, resource);
            String checkpointXml = createCheckpointXml(toCheckpointId);

            // Write XMLs to temp files
            File backupXmlFile = File.createTempFile("backup-", ".xml");
            File checkpointXmlFile = File.createTempFile("checkpoint-", ".xml");

            try (FileWriter writer = new FileWriter(backupXmlFile)) {
                writer.write(backupXml);
            }
            try (FileWriter writer = new FileWriter(checkpointXmlFile)) {
                writer.write(checkpointXml);
            }

            // Execute virsh backup-begin
            String backupCmd = String.format("virsh backup-begin %s %s --checkpointxml %s",
                vmName, backupXmlFile.getAbsolutePath(), checkpointXmlFile.getAbsolutePath());

            Script script = new Script("/bin/bash");
            script.add("-c");
            script.add(backupCmd);
            String result = script.execute();

            backupXmlFile.delete();
            checkpointXmlFile.delete();

            if (result != null) {
                return new StartBackupAnswer(cmd, false, "Backup begin failed: " + result);
            }

            long checkpointCreateTime = getCheckpointCreateTime();
            return new StartBackupAnswer(cmd, true, "Backup started successfully", checkpointCreateTime);

        } catch (Exception e) {
            return new StartBackupAnswer(cmd, false, "Error starting backup: " + e.getMessage());
        }
    }

    private Answer ensureFromCheckpointExists(StartBackupCommand cmd, String fromCheckpointId, Long fromCheckpointCreateTime) {
        String vmName = cmd.getVmName();
        if (LibvirtBackupHelper.checkpointExists(vmName, fromCheckpointId)) {
            return null;
        }
        if (fromCheckpointCreateTime == null) {
            return new StartBackupAnswer(cmd, false, "From checkpoint create time is null for checkpoint " + fromCheckpointId);
        }

        String result = LibvirtBackupHelper.redefineCheckpoint(vmName, fromCheckpointId, fromCheckpointCreateTime);
        if (result != null) {
            return new StartBackupAnswer(cmd, false, "Failed to redefine from-checkpoint " + fromCheckpointId + ": " + result);
        }
        return null;
    }

    private String createBackupXml(StartBackupCommand cmd, String fromCheckpointId, String socket, LibvirtComputingResource resource) {
        StringBuilder xml = new StringBuilder();
        xml.append("<domainbackup mode=\"pull\">\n");

        xml.append(String.format("  <server transport=\"unix\" socket=\"/tmp/imagetransfer/%s.sock\"/>\n", socket));

        xml.append("  <disks>\n");

        Map<String, String> diskPathUuidMap = cmd.getDiskPathUuidMap();
        Map<String, String> diskPathLabelMap = resource.getDiskPathLabelMap(cmd.getVmName());
        Map<String, Boolean> diskPathHasFromCheckpointMap = new HashMap<>();
        if (StringUtils.isNotBlank(fromCheckpointId)) {
            diskPathHasFromCheckpointMap = LibvirtBackupHelper.getVmDiskPathHasFromCheckpointMap(resource, cmd.getVmName(), fromCheckpointId);
        }

        for (Map.Entry<String, String> entry : diskPathLabelMap.entrySet()) {
            String diskPath = entry.getKey();
            if (!diskPathUuidMap.containsKey(diskPath)) {
                continue;
            }
            String diskName = entry.getValue();
            String export = diskPathUuidMap.get(diskPath);
            String scratchFile = "/var/tmp/scratch-" + export + ".qcow2";
            xml.append("    <disk name=\"").append(diskName).append("\" type=\"file\" exportname=\"").append(export);
            if (StringUtils.isNotBlank(fromCheckpointId) && Boolean.TRUE.equals(diskPathHasFromCheckpointMap.get(diskPath))) {
                xml.append("\" backupmode=\"incremental\"")
                        .append(" incremental=\"").append(fromCheckpointId)
                        .append("\" exportbitmap=\"").append(fromCheckpointId);
            }
            xml.append("\">\n");
            xml.append("      <scratch file=\"").append(scratchFile).append("\"/>\n");
            xml.append("    </disk>\n");
        }

        xml.append("  </disks>\n");
        xml.append("</domainbackup>");

        return xml.toString();
    }

    private String createCheckpointXml(String checkpointId) {
        return "<domaincheckpoint>\n" +
               "  <name>" + checkpointId + "</name>\n" +
               "</domaincheckpoint>";
    }

    private Answer handleStoppedVmBackup(StartBackupCommand cmd, String toCheckpointId) {
        Map<String, String> diskPathUuidMap = cmd.getDiskPathUuidMap();
        Map<String, byte[]> diskPathPassphraseMap = cmd.getDiskPathPassphraseMap();
        for (Map.Entry<String, String> entry : diskPathUuidMap.entrySet()) {
            String diskPath = entry.getKey();
            Script script = new Script("qemu-img");
            byte[] passphrase = diskPathPassphraseMap.get(diskPath);

            script.add("bitmap");
            script.add("--add");

            if (passphrase != null && passphrase.length > 0) {
                KeyFile srcKey;
                try {
                    srcKey = new KeyFile(passphrase);
                } catch (IOException ex) {
                    return new StartBackupAnswer(cmd, false, "Failed to create KeyFile while adding bitmap " + toCheckpointId + " to disk " + diskPath);
                }
                script.add("--object");
                script.add(String.format("secret,id=sec0,file=%s", srcKey));
                script.add("--image-opts");
                script.add(String.format("driver=qcow2,file.driver=file,file.filename=%s,encrypt.key-secret=sec0", diskPath));
            } else {
                script.add(diskPath);
            }

            script.add(toCheckpointId);
            String result = script.execute();
            if (result != null) {
                return new StartBackupAnswer(cmd, false,
                    "Failed to add bitmap " + toCheckpointId + " to disk " + diskPath + ": " + result);
            }
        }
        long checkpointCreateTime = getCheckpointCreateTime();
        return new StartBackupAnswer(cmd, true, "Stopped VM backup: checkpoint bitmap added successfully",
            checkpointCreateTime);
    }

    private long getCheckpointCreateTime() {
        return System.currentTimeMillis() / 1000;
    }
}
