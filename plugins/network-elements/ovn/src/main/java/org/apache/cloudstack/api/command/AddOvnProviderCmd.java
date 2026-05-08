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
package org.apache.cloudstack.api.command;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.network.ovn.OvnProvider;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.OvnProviderResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnProviderService;

import javax.inject.Inject;

@APICommand(name = AddOvnProviderCmd.APINAME, description = "Add OVN provider to CloudStack",
        responseObject = OvnProviderResponse.class, requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false, authorized = {RoleType.Admin}, since = "4.23.0")
public class AddOvnProviderCmd extends BaseCmd {
    public static final String APINAME = "addOvnProvider";

    @Inject
    OvnProviderService ovnProviderService;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, required = true,
            description = "the ID of zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "OVN provider name")
    private String name;

    @Parameter(name = ApiConstants.OVN_NB_CONNECTION, type = CommandType.STRING, required = true,
            description = "OVN Northbound database connection string. Supported formats: tcp:host:6641, ssl:host:6641, unix:/path/to/ovnnb_db.sock")
    private String nbConnection;

    @Parameter(name = ApiConstants.OVN_SB_CONNECTION, type = CommandType.STRING,
            description = "OVN Southbound database connection string for diagnostics and binding checks")
    private String sbConnection;

    @Parameter(name = ApiConstants.OVN_CA_CERT_PATH, type = CommandType.STRING, description = "OVN TLS CA certificate path")
    private String caCertPath;

    @Parameter(name = ApiConstants.OVN_CLIENT_CERT_PATH, type = CommandType.STRING, description = "OVN TLS client certificate path")
    private String clientCertPath;

    @Parameter(name = ApiConstants.OVN_CLIENT_PRIVATE_KEY_PATH, type = CommandType.STRING, description = "OVN TLS client private key path")
    private String clientPrivateKeyPath;

    @Parameter(name = ApiConstants.OVN_EXTERNAL_BRIDGE, type = CommandType.STRING, description = "OVN external bridge used for provider network access")
    private String externalBridge;

    @Parameter(name = ApiConstants.OVN_LOCALNET_NAME, type = CommandType.STRING, description = "OVN localnet name used for provider network mapping")
    private String localnetName;

    @Parameter(name = ApiConstants.OVN_IC_NB_CONNECTION, type = CommandType.STRING,
            description = "OVN-IC Northbound database connection string (e.g. tcp:host:6645). Required to enable cross-zone VPC peering via OVN Interconnection.")
    private String icNbConnection;

    @Parameter(name = ApiConstants.OVN_IC_SB_CONNECTION, type = CommandType.STRING,
            description = "OVN-IC Southbound database connection string (e.g. tcp:host:6646) for diagnostics")
    private String icSbConnection;

    @Parameter(name = ApiConstants.OVN_AVAILABILITY_ZONE_NAME, type = CommandType.STRING,
            description = "Availability zone name registered in NB_Global for OVN-IC. Must be unique across all peered zones.")
    private String availabilityZoneName;

    public Long getZoneId() {
        return zoneId;
    }

    public String getName() {
        return name;
    }

    public String getNbConnection() {
        return nbConnection;
    }

    public String getSbConnection() {
        return sbConnection;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public String getClientPrivateKeyPath() {
        return clientPrivateKeyPath;
    }

    public String getExternalBridge() {
        return externalBridge;
    }

    public String getLocalnetName() {
        return localnetName;
    }

    public String getIcNbConnection() {
        return icNbConnection;
    }

    public String getIcSbConnection() {
        return icSbConnection;
    }

    public String getAvailabilityZoneName() {
        return availabilityZoneName;
    }

    @Override
    public void execute() throws ServerApiException, ConcurrentOperationException {
        OvnProvider provider = ovnProviderService.addProvider(this);
        OvnProviderResponse response = ovnProviderService.createOvnProviderResponse(provider);
        if (response == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to add OVN provider");
        }
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
