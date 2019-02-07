/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.utils.imagestore;

import com.cloud.utils.script.Script;
import org.apache.commons.lang.StringUtils;
import org.apache.cloudstack.utils.log.Logger;
import org.apache.cloudstack.utils.log.LogFactory;

public class ImageStoreUtil {
    public static final Logger LOG = LogFactory.getLogger(ImageStoreUtil.class);

    public static String generatePostUploadUrl(String ssvmUrlDomain, String ipAddress, String uuid) {
        String hostname = ipAddress;

        //if ssvm url domain is present, use it to construct hostname in the format 1-2-3-4.domain
        // if the domain name is not present, ssl validation fails and has to be ignored
        if(StringUtils.isNotBlank(ssvmUrlDomain)) {
            hostname = ipAddress.replace(".", "-");
            hostname = hostname + ssvmUrlDomain.substring(1);
        }

        //only https works with postupload and url format is fixed
        return "https://" + hostname + "/upload/" + uuid;
    }

    // given a path, returns empty if path is supported image, and the file type if unsupported
    // this is meant to catch things like accidental upload of ASCII text .vmdk descriptor
    public static String checkTemplateFormat(String path, String uripath) {
        // note 'path' was generated by us so it should be safe on the cmdline, be wary of 'url'
        String command = "file ";
        if (isCompressedExtension(uripath)) {
            command = "file -z ";
        }
        String output = Script.runSimpleBashScript(command + path + " | cut -d: -f2", 60000);

        // vmdk
        if ((output.contains("VMware") || output.contains("data")) && isCorrectExtension(uripath, "vmdk")) {
            LOG.debug("File at path " + path + " looks like a vmware image :" + output);
            return "";
        }
        // raw
        if ((output.contains("x86 boot") || output.contains("data")) && (isCorrectExtension(uripath, "raw") || isCorrectExtension(uripath, "img"))) {
            LOG.debug("File at path " + path + " looks like a raw image :" + output);
            return "";
        }
        // qcow2
        if (output.contains("QEMU QCOW") && isCorrectExtension(uripath, "qcow2")) {
            LOG.debug("File at path " + path + " looks like QCOW2 : " + output);
            return "";
        }
        // vhd
        if (output.contains("Microsoft Disk Image") && (isCorrectExtension(uripath, "vhd") || isCorrectExtension(uripath, "vhdx"))) {
            LOG.debug("File at path " + path + " looks like vhd : " + output);
            return "";
        }
        // ova
        if (output.contains("POSIX tar") && isCorrectExtension(uripath, "ova")) {
            LOG.debug("File at path " + path + " looks like ova : " + output);
            return "";
        }

        //lxc
        if (output.contains("POSIX tar") && isCorrectExtension(uripath, "tar")) {
            LOG.debug("File at path " + path + " looks like just tar : " + output);
            return "";
        }

        if ((output.startsWith("ISO 9660") || output.startsWith("DOS/MBR")) && isCorrectExtension(uripath, "iso")) {
            LOG.debug("File at path " + path + " looks like an iso : " + output);
            return "";
        }
        return output;
    }

    private static boolean isCorrectExtension(String path, String ext) {
        if (path.toLowerCase().endsWith(ext)
            || path.toLowerCase().endsWith(ext + ".gz")
            || path.toLowerCase().endsWith(ext + ".bz2")
            || path.toLowerCase().endsWith(ext + ".zip")) {
            return true;
        }
        return false;
    }

    private static boolean isCompressedExtension(String path) {
        if (path.toLowerCase().endsWith(".gz")
            || path.toLowerCase().endsWith(".bz2")
            || path.toLowerCase().endsWith(".zip")) {
            return true;
        }
        return false;
    }
}

