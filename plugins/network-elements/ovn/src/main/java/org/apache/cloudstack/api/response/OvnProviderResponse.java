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
package org.apache.cloudstack.api.response;

import com.cloud.network.ovn.OvnProvider;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

@EntityReference(value = {OvnProvider.class})
public class OvnProviderResponse extends BaseResponse {
    @SerializedName(ApiConstants.NAME)
    @Param(description = "OVN provider name")
    private String name;

    @SerializedName(ApiConstants.UUID)
    @Param(description = "OVN provider UUID")
    private String uuid;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "Zone ID to which the OVN provider is associated")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "Zone name to which the OVN provider is associated")
    private String zoneName;

    @SerializedName(ApiConstants.OVN_NB_CONNECTION)
    @Param(description = "OVN Northbound database connection string")
    private String nbConnection;

    @SerializedName(ApiConstants.OVN_SB_CONNECTION)
    @Param(description = "OVN Southbound database connection string")
    private String sbConnection;

    @SerializedName(ApiConstants.OVN_CA_CERT_PATH)
    @Param(description = "OVN TLS CA certificate path")
    private String caCertPath;

    @SerializedName(ApiConstants.OVN_CLIENT_CERT_PATH)
    @Param(description = "OVN TLS client certificate path")
    private String clientCertPath;

    @SerializedName(ApiConstants.OVN_CLIENT_PRIVATE_KEY_PATH)
    @Param(description = "OVN TLS client private key path")
    private String clientPrivateKeyPath;

    @SerializedName(ApiConstants.OVN_EXTERNAL_BRIDGE)
    @Param(description = "OVN external bridge used for provider network access")
    private String externalBridge;

    @SerializedName(ApiConstants.OVN_LOCALNET_NAME)
    @Param(description = "OVN localnet name used for provider network mapping")
    private String localnetName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getNbConnection() {
        return nbConnection;
    }

    public void setNbConnection(String nbConnection) {
        this.nbConnection = nbConnection;
    }

    public String getSbConnection() {
        return sbConnection;
    }

    public void setSbConnection(String sbConnection) {
        this.sbConnection = sbConnection;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientPrivateKeyPath() {
        return clientPrivateKeyPath;
    }

    public void setClientPrivateKeyPath(String clientPrivateKeyPath) {
        this.clientPrivateKeyPath = clientPrivateKeyPath;
    }

    public String getExternalBridge() {
        return externalBridge;
    }

    public void setExternalBridge(String externalBridge) {
        this.externalBridge = externalBridge;
    }

    public String getLocalnetName() {
        return localnetName;
    }

    public void setLocalnetName(String localnetName) {
        this.localnetName = localnetName;
    }
}
