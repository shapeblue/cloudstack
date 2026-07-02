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

import org.apache.cloudstack.utils.qemu.QemuCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.utils.StringUtils;
import com.cloud.utils.script.Script;

public class LibvirtBackupHelper {

    private static final Logger LOG = LogManager.getLogger(LibvirtBackupHelper.class);

    private LibvirtBackupHelper() {
    }

    public static Map<String, Boolean> getVmDiskPathHasFromCheckpointMap(LibvirtComputingResource resource, String vmName, String checkpointId) {
        Map<String, Boolean> diskPathHasFromCheckpointMap = new HashMap<>();
        Domain vm = null;
        try {
            vm = resource.getDomain(resource.getLibvirtUtilitiesHelper().getConnection(), vmName);
            if (vm == null) {
                LOG.warn("Failed to get domain for VM [{}] while evaluating checkpoint [{}]. Falling back to full Backup", vmName, checkpointId);
                return diskPathHasFromCheckpointMap;
            }
            String queryBlock = vm.qemuMonitorCommand(QemuCommand.buildQemuCommand("query-block", null), 0);
            JSONObject response = new JSONObject(queryBlock);
            JSONArray blocks = response.optJSONArray("return");
            if (blocks == null) {
                LOG.warn("Couldn't get bitmap information for the VM [{}]. Falling back to full Backup", vmName);
                return diskPathHasFromCheckpointMap;
            }
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                JSONObject inserted = block.optJSONObject("inserted");
                if (inserted == null) {
                    continue;
                }
                String file = inserted.optString("file");
                if (StringUtils.isBlank(file)) {
                    continue;
                }
                JSONArray dirtyBitmaps = inserted.optJSONArray("dirty-bitmaps");
                boolean hasFromCheckpointBitmap = false;
                if (dirtyBitmaps != null) {
                    for (int j = 0; j < dirtyBitmaps.length(); j++) {
                        JSONObject dirtyBitmap = dirtyBitmaps.optJSONObject(j);
                        if (dirtyBitmap == null) {
                            continue;
                        }
                        String bitmapName = dirtyBitmap.optString("name");
                        if (checkpointId.equals(bitmapName)) {
                            hasFromCheckpointBitmap = true;
                            break;
                        }
                    }
                }
                diskPathHasFromCheckpointMap.put(file, hasFromCheckpointBitmap);
            }
            return diskPathHasFromCheckpointMap;
        } catch (LibvirtException e) {
            LOG.warn("Failed to evaluate checkpoint [{}] bitmap presence for VM [{}]: {}. Falling back to full Backup",
                    checkpointId, vmName, e.getMessage());
            return new HashMap<>();
        } finally {
            if (vm != null) {
                try {
                    vm.free();
                } catch (LibvirtException e) {
                    LOG.trace("Ignoring libvirt error while freeing domain [{}].", vmName, e);
                }
            }
        }
    }

    public static long countDisksWithCheckpoint(Map<String, Boolean> diskPathHasFromCheckpointMap) {
        return diskPathHasFromCheckpointMap.values().stream().filter(Boolean::booleanValue).count();
    }

    public static boolean checkpointExists(String vmName, String checkpointId) {
        Script dumpScript = new Script("/bin/bash");
        dumpScript.add("-c");
        dumpScript.add(String.format("virsh checkpoint-dumpxml --domain %s --checkpointname %s --no-domain",
                vmName, checkpointId));
        return dumpScript.execute() == null;
    }

    public static String redefineCheckpoint(String vmName, String checkpointId, long createTime) {
        String redefineXml = createCheckpointXmlForRedefine(checkpointId, createTime);
        File redefineFile;
        try {
            redefineFile = File.createTempFile("checkpoint-redefine-", ".xml");
        } catch (IOException e) {
            return "Failed to create temp file for checkpoint redefine: " + e.getMessage();
        }
        try (FileWriter writer = new FileWriter(redefineFile)) {
            writer.write(redefineXml);
        } catch (IOException e) {
            redefineFile.delete();
            return "Failed to write checkpoint redefine XML: " + e.getMessage();
        }
        String createCmd = String.format(LibvirtComputingResource.CHECKPOINT_CREATE_COMMAND, vmName, redefineFile.getAbsolutePath());
        Script createScript = new Script("/bin/bash");
        createScript.add("-c");
        createScript.add(createCmd);
        String result = createScript.execute();
        redefineFile.delete();
        return result;
    }

    public static String ensureCheckpointRegistered(String vmName, String checkpointId, long createTime) {
        if (checkpointExists(vmName, checkpointId)) {
            return null;
        }
        return redefineCheckpoint(vmName, checkpointId, createTime);
    }

    private static String createCheckpointXmlForRedefine(String checkpointName, long createTime) {
        StringBuilder xml = new StringBuilder();
        xml.append("<domaincheckpoint>\n");
        xml.append("  <name>").append(checkpointName).append("</name>\n");
        xml.append("  <creationTime>").append(createTime).append("</creationTime>\n");
        xml.append("</domaincheckpoint>");
        return xml.toString();
    }
}