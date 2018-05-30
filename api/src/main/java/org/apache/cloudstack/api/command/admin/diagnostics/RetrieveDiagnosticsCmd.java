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

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.vm.VirtualMachine;
import com.google.common.base.Strings;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.acl.SecurityChecker;
import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.api.response.SystemVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.diagnostics.RetrieveDiagnosticsService;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
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
    private RetrieveDiagnosticsService retrieveDiagnosticsService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @ACL(accessType = SecurityChecker.AccessType.OperateEntry)
    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = SystemVmResponse.class,
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

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "lists configuration by name")
    private String configName;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    @Parameter(name = ApiConstants.KEYWORD, type = CommandType.STRING, description = "List by keyword")
    private String keyword;


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

    public RetrieveDiagnosticsService getRetrieveDiagnosticsService() {
        return retrieveDiagnosticsService;
    }

    public void setRetrieveDiagnosticsService(RetrieveDiagnosticsService retrieveDiagnosticsService) {
        this.retrieveDiagnosticsService = retrieveDiagnosticsService;
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

    @Override
    public String getEventType() {
        VirtualMachine.Type type = _mgr.findSystemVMTypeById(getId());
        return type.toString();
    }
    public String getDiagnosticsType() {
        return diagnosticsType;
    }


    @Override
    public void execute() {
        CallContext.current().setEventDetails("Vm Id: " + this._uuidMgr.getUuid(VirtualMachine.class, getId()));
        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = new RetrieveDiagnosticsResponse();
        try {
            retrieveDiagnosticsService.getDiagnosticsFiles(this);
            retrieveDiagnosticsResponse.setObjectName("retrievediagnostics");
            retrieveDiagnosticsResponse.setResponseName(getCommandName());
            this.setResponseObject(retrieveDiagnosticsResponse);
        } catch (InvalidParameterValueException ipve) {
            s_logger.error("Failed to retrieve diagnostics files from ", ipve);
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ipve.getMessage());
        } catch (ConfigurationException cre) {
            s_logger.error("Failed to retrieve diagnostics files from ", cre);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, cre.getMessage());
        }
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
    public String getEventDescription() {
        return "Retrieved diagnostics files from System VM =" + id;
    }
}
