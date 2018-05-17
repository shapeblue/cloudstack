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

package org.apache.cloudstack.api.command.admin.diagnostics;

import com.cloud.event.EventTypes;
import com.google.common.base.Strings;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.*;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.diagnostics.RetrieveDiagnosticsManager;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = RetrieveDiagnosticsCmd.APINAME,
        description = "Retrieves diagnostics files from System VMs",
        responseObject = RetrieveDiagnosticsResponse.class,
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        since = "4.11.0",
        authorized = {RoleType.Admin})
public class RetrieveDiagnosticsCmd extends BaseAsyncCmd {

    private static final Logger s_logger = Logger.getLogger(RetrieveDiagnosticsCmd.class);

    public static final String APINAME = "retrieveDiagnostics";

    private boolean retrieveDefaultFiles = false;
    @Inject
    private RetrieveDiagnosticsManager retrieveDiagnosticsManager;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = RetrieveDiagnosticsResponse.class,
            required = true,
            description = "The System VM type that the diagnostics files requested are to be retrieved from")
    private Long id;


    @Parameter(name = ApiConstants.DIAGNOSTICS_TYPE,
            type = BaseAsyncCmd.CommandType.STRING,
            description = "The type of diagnostics files requested, if DIAGNOSTICS_TYPE is not provided then the default files specified in the database will be retrieved")
    private String diagnosticsType;

    @Parameter(name = ApiConstants.DETAIL,
            type = BaseAsyncCmd.CommandType.STRING,
            description = "Optional comma separated list of diagnostics files or items, can be specified as filenames only or full file path. These come in addition to the defaults set in diagnosticstype")
    private String optionalListOfFiles;

    // Configuration parameters //////

    @Parameter(name = ApiConstants.TIMEOUT,
            type = BaseAsyncCmd.CommandType.STRING,
            required = false,
            description = "Time out setting in seconds for the overall API call.")
    private String timeOut;

    @Parameter(name = ApiConstants.DISABLE_THRESHOLD,
            type = BaseAsyncCmd.CommandType.STRING,
            required = false,
            description = "Percentage disk space cut off before API will fail.")
    private String disableThreshold;

    @Parameter(name = ApiConstants.FILE_AGE,
            type = BaseAsyncCmd.CommandType.STRING,
            required = false,
            description = "Diagnostics file age in seconds before considered for garbage collection")
    private String fileAge;

    public static Logger getS_logger() {
        return s_logger;
    }

    public static String getAPINAME() {
        return APINAME;
    }

    public boolean isRetrieveDefaultFiles() {
        return retrieveDefaultFiles;
    }

    public void setRetrieveDefaultFiles(boolean retrieveDefaultFiles) {
        this.retrieveDefaultFiles = retrieveDefaultFiles;
    }

    public RetrieveDiagnosticsManager getRetrieveDiagnosticsManager() {
        return retrieveDiagnosticsManager;
    }

    public void setRetrieveDiagnosticsManager(RetrieveDiagnosticsManager retrieveDiagnosticsManager) {
        this.retrieveDiagnosticsManager = retrieveDiagnosticsManager;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDiagnosticsType(String diagnosticsType) {
        this.diagnosticsType = diagnosticsType;
    }

    public String getOptionalListOfFiles() {
        return optionalListOfFiles;
    }

    public void setOptionalListOfFiles(String optionalListOfFiles) {
        this.optionalListOfFiles = optionalListOfFiles;
    }

    public String getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    public String getDisableThreshold() {
        return disableThreshold;
    }

    public void setDisableThreshold(String disableThreshold) {
        this.disableThreshold = disableThreshold;
    }

    public String getFileAge() {
        return fileAge;
    }

    public void setFileAge(String fileAge) {
        this.fileAge = fileAge;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getIntervalGC() {
        return intervalGC;
    }

    public void setIntervalGC(String intervalGC) {
        this.intervalGC = intervalGC;
    }

    public String getEnabledGC() {
        return enabledGC;
    }

    public void setEnabledGC(String enabledGC) {
        this.enabledGC = enabledGC;
    }

    public String getCfgName() {
        return cfgName;
    }

    public void setCfgName(String cfgName) {
        this.cfgName = cfgName;
    }

    @Parameter(name = ApiConstants.NAME,
            type = CommandType.STRING,
            required = true,
            description = "the name of the configuration")
    private String cfgName;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Parameter(name = ApiConstants.VALUE,
            type = CommandType.STRING,
            description = "the value of the configuration",
            length = 4095)
    private String value;

    @Parameter(name = ApiConstants.FILE_PATH,
            type = BaseAsyncCmd.CommandType.STRING,
            required = false,
            description = "File path to use on the management server for all temporary files.")
    private String filePath;

    @Parameter(name = ApiConstants.INTERVAL,
            type = BaseAsyncCmd.CommandType.STRING,
            required = false,
            description = "Interval between garbage collection executions in seconds.")
    private String intervalGC;

    @Parameter(name = ApiConstants.ENABLED,
            type = CommandType.BOOLEAN,
            required = false,
            description = "Garbage Collection on/off switch (true|false).")
    private String enabledGC;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    private List<String> processListOfDiagnosticsFiles(final String string) {
        final List<String> listOfDiagnosticsFiles = new ArrayList<>();
        if (!Strings.isNullOrEmpty(string)) {
            for (final String file: string.split(",")) {
                listOfDiagnosticsFiles.add(file.trim());
            }
        }
        return listOfDiagnosticsFiles;
    }

    public List<String> getListOfDiagnosticsFiles() {
        if (optionalListOfFiles != null) {
            return processListOfDiagnosticsFiles(optionalListOfFiles);
        }
        return null;
    }

    public String getDiagnosticsType() {
        return diagnosticsType;
    }


    //Logic to retrieve the list of default diagnostic files from the database
    //public String getDefaultListOfDiagnosticsFiles() { return null; }

    @Override
    public void execute() {
        if (Strings.isNullOrEmpty(getDiagnosticsType()) || Strings.isNullOrEmpty(optionalListOfFiles) ) {
//              String defaultListOfdiagnosticsFiles = getDefaultListOfDiagnosticsFiles();
//              List<String> listOfDefaultDiagnosticsFiles = processListOfDiagnosticsFiles(defaultListOfdiagnosticsFiles);
            retrieveDefaultFiles = true;
        }



        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = new RetrieveDiagnosticsResponse();
        try {
            if (retrieveDiagnosticsManager == null)
                throw new IOException();

        } catch (final IOException e) {
            s_logger.error("Failed to retrieve diagnostics files from ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to retrieve diagnostics files");
        }
        retrieveDiagnosticsResponse.setResponseName(getCommandName());
        setResponseObject(retrieveDiagnosticsResponse);

        retrieveDiagnosticsManager.updateConfiguration(this);


    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + BaseAsyncCmd.RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VM_DIAGNOSTICS;
    }

    @Override
    public String getEventDescription() {
        return "Retrieved diagnostics files from System VM =" + id;
    }
}
