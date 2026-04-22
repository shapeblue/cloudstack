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
package com.cloud.network.element;

import com.cloud.network.ovn.OvnProvider;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "ovn_providers")
public class OvnProviderVO implements OvnProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "zone_id")
    private long zoneId;

    @Column(name = "host_id")
    private Long hostId;

    @Column(name = "name")
    private String name;

    @Column(name = "nb_connection")
    private String nbConnection;

    @Column(name = "sb_connection")
    private String sbConnection;

    @Column(name = "ca_cert_path")
    private String caCertPath;

    @Column(name = "client_cert_path")
    private String clientCertPath;

    @Column(name = "client_private_key_path")
    private String clientPrivateKeyPath;

    @Column(name = "external_bridge")
    private String externalBridge;

    @Column(name = "localnet_name")
    private String localnetName;

    @Column(name = "created")
    private Date created;

    @Column(name = "removed")
    private Date removed;

    public OvnProviderVO() {
        uuid = UUID.randomUUID().toString();
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public long getZoneId() {
        return zoneId;
    }

    public void setZoneId(long zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getNbConnection() {
        return nbConnection;
    }

    public void setNbConnection(String nbConnection) {
        this.nbConnection = nbConnection;
    }

    @Override
    public String getSbConnection() {
        return sbConnection;
    }

    public void setSbConnection(String sbConnection) {
        this.sbConnection = sbConnection;
    }

    @Override
    public String getCaCertPath() {
        return caCertPath;
    }

    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }

    @Override
    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    @Override
    public String getClientPrivateKeyPath() {
        return clientPrivateKeyPath;
    }

    public void setClientPrivateKeyPath(String clientPrivateKeyPath) {
        this.clientPrivateKeyPath = clientPrivateKeyPath;
    }

    @Override
    public String getExternalBridge() {
        return externalBridge;
    }

    public void setExternalBridge(String externalBridge) {
        this.externalBridge = externalBridge;
    }

    @Override
    public String getLocalnetName() {
        return localnetName;
    }

    public void setLocalnetName(String localnetName) {
        this.localnetName = localnetName;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getRemoved() {
        return removed;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }

    public static final class Builder {
        private long zoneId;
        private Long hostId;
        private String name;
        private String nbConnection;
        private String sbConnection;
        private String caCertPath;
        private String clientCertPath;
        private String clientPrivateKeyPath;
        private String externalBridge;
        private String localnetName;

        public Builder setZoneId(long zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        public Builder setHostId(Long hostId) {
            this.hostId = hostId;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setNbConnection(String nbConnection) {
            this.nbConnection = nbConnection;
            return this;
        }

        public Builder setSbConnection(String sbConnection) {
            this.sbConnection = sbConnection;
            return this;
        }

        public Builder setCaCertPath(String caCertPath) {
            this.caCertPath = caCertPath;
            return this;
        }

        public Builder setClientCertPath(String clientCertPath) {
            this.clientCertPath = clientCertPath;
            return this;
        }

        public Builder setClientPrivateKeyPath(String clientPrivateKeyPath) {
            this.clientPrivateKeyPath = clientPrivateKeyPath;
            return this;
        }

        public Builder setExternalBridge(String externalBridge) {
            this.externalBridge = externalBridge;
            return this;
        }

        public Builder setLocalnetName(String localnetName) {
            this.localnetName = localnetName;
            return this;
        }

        public OvnProviderVO build() {
            OvnProviderVO provider = new OvnProviderVO();
            provider.setZoneId(zoneId);
            provider.setHostId(hostId);
            provider.setName(name);
            provider.setNbConnection(nbConnection);
            provider.setSbConnection(sbConnection);
            provider.setCaCertPath(caCertPath);
            provider.setClientCertPath(clientCertPath);
            provider.setClientPrivateKeyPath(clientPrivateKeyPath);
            provider.setExternalBridge(externalBridge);
            provider.setLocalnetName(localnetName);
            return provider;
        }
    }
}
