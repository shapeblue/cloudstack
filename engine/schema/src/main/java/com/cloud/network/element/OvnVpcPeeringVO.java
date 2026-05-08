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

import org.apache.cloudstack.api.InternalIdentity;
import org.apache.cloudstack.api.Identity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "ovn_vpc_peerings")
public class OvnVpcPeeringVO implements InternalIdentity, Identity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "group_uuid")
    private String groupUuid;

    @Column(name = "vpc_id")
    private long vpcId;

    @Column(name = "zone_id")
    private long zoneId;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "domain_id")
    private long domainId;

    @Column(name = "link_local_ip")
    private String linkLocalIp;

    @Column(name = "acl_id")
    private Long aclId;

    @Column(name = "state")
    private String state;

    @Column(name = "created")
    private Date created;

    @Column(name = "removed")
    private Date removed;

    public OvnVpcPeeringVO() {
        uuid = UUID.randomUUID().toString();
    }

    public OvnVpcPeeringVO(String groupUuid, long vpcId, long zoneId, long accountId, long domainId, String linkLocalIp) {
        this.uuid = UUID.randomUUID().toString();
        this.groupUuid = groupUuid;
        this.vpcId = vpcId;
        this.zoneId = zoneId;
        this.accountId = accountId;
        this.domainId = domainId;
        this.linkLocalIp = linkLocalIp;
        this.state = "Active";
        this.created = new Date();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public String getGroupUuid() {
        return groupUuid;
    }

    public long getVpcId() {
        return vpcId;
    }

    public long getZoneId() {
        return zoneId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getDomainId() {
        return domainId;
    }

    public String getLinkLocalIp() {
        return linkLocalIp;
    }

    public Long getAclId() {
        return aclId;
    }

    public void setAclId(Long aclId) {
        this.aclId = aclId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }
}
