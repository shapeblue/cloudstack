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
package com.cloud.agent.api.storage;

import com.cloud.agent.api.to.DataTO;

import java.util.Map;

public class CopyRetrieveZipFilesCommand extends StorageNfsVersionCommand {
    private String copyCommand;
    private Boolean secCleanup = null;
    private String content;
    private String controlIp;
    private final String tmpZipFilePath = "/tmp/diagnostics_*";
    private DataTO srcData;
    private Map<String, String> srcDetails;
    private boolean executeInSequence;

    public CopyRetrieveZipFilesCommand(String copyCommand, String content, Boolean secCleanup, boolean executeInSequence) {
        this.copyCommand = copyCommand;
        this.secCleanup = secCleanup;
        this.content = content;
        this.executeInSequence = executeInSequence;
    }

    @Override
    public boolean executeInSequence() {
        return executeInSequence;
    }

    public String getCopyCommand() {
        return copyCommand;
    }

    public void setCopyCommand(String copyCommand) {
        this.copyCommand = copyCommand;
    }

    public Boolean getSecCleanup() {
        return secCleanup;
    }

    public void setSecCleanup(Boolean secCleanup) {
        this.secCleanup = secCleanup;
    }

    public void setSrcData(DataTO srcData) {
        this.srcData = srcData;
    }

    public DataTO getSrcData() {
        return srcData;
    }

    public void setSrcDetails(Map<String, String> srcDetails) {
        this.srcDetails = srcDetails;
    }

    public String getControlIp() {
        return controlIp;
    }

    public void setControlIp(String controlIp) {
        this.controlIp = controlIp;
    }

    public String getTmpZipFilePath() {
        return tmpZipFilePath;
    }

    public String getContent() {
        return content;
    }

}
