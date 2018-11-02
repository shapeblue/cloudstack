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
package com.cloud.storage.download.managementserver;

import com.cloud.template.VirtualMachineTemplate;
import com.cloud.utils.UriUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import org.apache.cloudstack.utils.security.DigestHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public abstract class TemplateDownloaderImpl implements TemplateDownloader {

    private String destPoolPath;
    private String downloadedFilePath;
    private String installPath;
    private VirtualMachineTemplate template;
    private boolean redownload = false;
    private String srcHost;
    private String srcPath;

    public static final Logger s_logger = Logger.getLogger(TemplateDownloaderImpl.class.getName());
    private static String systemInstallDir = "template" + File.separator + "tmpl" + File.separator + "1";

    protected TemplateDownloaderImpl(final VirtualMachineTemplate template, final String destPoolPath) {
        this.template = template;
        this.destPoolPath = destPoolPath;
        parseUrl();
    }

    /**
     * Return download path to download template
     */
    protected static String getDownloadPath(Long templateId) {
        return systemInstallDir + File.separator + templateId;
    }

    /**
     * Create folder on path if it does not exist
     */
    protected void createFolder(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            s_logger.error(String.format("Unable to create directory %s for template %s: %s%n", path, getTemplate().getUrl(), e.toString()));
            throw new CloudRuntimeException(e);
        }
    }

    public String getDestPoolPath() {
        return destPoolPath;
    }


    public String getDownloadedFilePath() {
        return downloadedFilePath;
    }

    public void setDownloadedFilePath(String filePath) {
        this.downloadedFilePath = filePath;
    }

    public VirtualMachineTemplate getTemplate() {
        return template;
    }

    public boolean isRedownload() {
        return redownload;
    }

    /**
     * Return filename from url
     */
    public String getFileNameFromUrl() {
        String[] urlParts = template.getUrl().split("/");
        return urlParts[urlParts.length - 1];
    }

    public String getInstallFileName() {
        return template.getUuid() + "." + template.getFormat().toString().toLowerCase();
    }

    /**
     * Checks if downloaded template is extractable
     * @return true if it should be extracted, false if not
     */
    private boolean isTemplateExtractable() {
        String type = Script.runSimpleBashScript("file " + downloadedFilePath + " | awk -F' ' '{print $2}'");
        return type.equalsIgnoreCase("bzip2") || type.equalsIgnoreCase("gzip") || type.equalsIgnoreCase("zip");
    }

    @Override
    public boolean extractAndInstallDownloadedTemplate() {
        installPath = UUID.randomUUID().toString();
        createFolder(getInstallFullPath());
        if (isTemplateExtractable()) {
            extractDownloadedTemplate();
        } else {
            s_logger.error(String.format("The downloaded template from %s is not extractable.%n", template.getUrl()));
            return false;
        }
        return true;
    }

    /**
     * Return install full path
     */
    private String getInstallFullPath() {
        return destPoolPath + File.separator + systemInstallDir + File.separator + String.valueOf(template.getId());
    }

    /**
     * Return extract command to execute given downloaded file
     */
    private String getExtractCommandForDownloadedFile() {
        if (downloadedFilePath.endsWith(".zip")) {
            return "unzip -p " + downloadedFilePath + " | cat > " + getInstallFullPath() + File.separator + getInstallFileName();
        } else if (downloadedFilePath.endsWith(".bz2")) {
            return "bunzip2 -c " + downloadedFilePath + " > " + getInstallFullPath() + File.separator + getInstallFileName();
        } else if (downloadedFilePath.endsWith(".gz")) {
            return "gunzip -c " + downloadedFilePath + " > " + getInstallFullPath() + File.separator + getInstallFileName();
        } else {
            throw new CloudRuntimeException("Unable to extract template " + template.getId() + " on " + downloadedFilePath);
        }
    }

    /**
     * Extract downloaded template into installPath, remove compressed file
     */
    private void extractDownloadedTemplate() {
        String extractCommand = getExtractCommandForDownloadedFile();
        Script.runSimpleBashScript(extractCommand);
        Script.runSimpleBashScript("rm -f " + downloadedFilePath);
    }

    @Override
    public TemplateInformation getTemplateInformation() {
        String sizeResult = Script.runSimpleBashScript("ls -als " + getInstallFullPath() + File.separator + getInstallFileName() + " | awk '{print $1}'");
        long size = Long.parseLong(sizeResult);
        return new TemplateInformation(systemInstallDir + File.separator + String.valueOf(template.getId()) + File.separator + getInstallFileName(),
                getTemplate().getUuid(), size, template.getChecksum());
    }

    @Override
    public boolean validateChecksum() {
        if (StringUtils.isNotBlank(template.getChecksum())) {
            int retry = 3;
            boolean valid = false;
            try {
                while (!valid && retry > 0) {
                    retry--;
                    s_logger.info("Performing checksum validation for downloaded template " + template.getId() + " using " + template.getChecksum() + ", retries left: " + retry);
                    valid = DigestHelper.check(template.getChecksum(), new FileInputStream(downloadedFilePath));
                    if (!valid && retry > 0) {
                        s_logger.info("Checksum validation failded, re-downloading template");
                        redownload = true;
                        resetDownloadFile();
                        downloadTemplate();
                    }
                }
                s_logger.info("Checksum validation for template " + template.getId() + ": " + (valid ? "succeeded" : "failed"));
                return valid;
            } catch (IOException e) {
                throw new CloudRuntimeException("could not check sum for file: " + downloadedFilePath, e);
            } catch (NoSuchAlgorithmException e) {
                throw new CloudRuntimeException("Unknown checksum algorithm: " + template.getChecksum(), e);
            }
        }
        s_logger.info("No checksum provided, skipping checksum validation");
        return true;
    }

    /**
     * Delete and create download file
     */
    private void resetDownloadFile() {
        File f = new File(getDownloadedFilePath());
        s_logger.info("Resetting download file: " + getDownloadedFilePath() + ", in order to re-download and persist template " + template.getId() + " on it");
        try {
            if (f.exists()) {
                f.delete();
            }
            f.createNewFile();
        } catch (IOException e) {
            s_logger.error("Error creating file to download on: " + getDownloadedFilePath() + " due to: " + e.getMessage());
            throw new CloudRuntimeException("Failed to create download file for direct download");
        }
    }

    /**
     * Parse url and set srcHost and srcPath
     */
    private void parseUrl() {
        try {
            URI uri = new URI(UriUtils.encodeURIComponent(template.getUrl()));
            if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("nfs")) {
                srcHost = uri.getHost();
                srcPath = uri.getPath();
            }
        } catch (URISyntaxException e) {
            throw new CloudRuntimeException("Invalid NFS url " + template.getUrl() + " caused error: " + e.getMessage());
        }
    }
}
