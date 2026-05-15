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

import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import java.util.Date;
import java.util.List;

public class MeshNetworkResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    private String id;

    @SerializedName("meshuuid")
    private String meshUuid;

    @SerializedName(ApiConstants.NAME)
    private String name;

    @SerializedName(ApiConstants.DESCRIPTION)
    private String description;

    @SerializedName(ApiConstants.VPC_ID)
    private String vpcId;

    @SerializedName("vpcname")
    private String vpcName;

    @SerializedName("vpccidr")
    private String vpcCidr;

    @SerializedName("peervpcid")
    private String peerVpcId;

    @SerializedName("peervpcname")
    private String peerVpcName;

    @SerializedName("peervpccidr")
    private String peerVpcCidr;

    @SerializedName(ApiConstants.ZONE_ID)
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    private String zoneName;

    @SerializedName("linklocalip")
    private String linkLocalIp;

    @SerializedName("aclid")
    private String aclId;

    @SerializedName("aclname")
    private String aclName;

    @SerializedName(ApiConstants.STATE)
    private String state;

    @SerializedName(ApiConstants.CREATED)
    private Date created;

    /**
     * Number of VPCs in the mesh network. Set on aggregated (group-level) responses.
     */
    @SerializedName("vpccount")
    private Integer vpcCount;

    /**
     * Comma-separated list of VPC names in the group. Convenient for the list-view column.
     */
    @SerializedName("vpcnames")
    private String vpcNames;

    /**
     * Per-member detail. Populated only on group-level responses (id == meshuuid).
     * Each entry corresponds to one VPC's row in the mesh network DB and is enough to drive
     * the "VPC Peers" detail tab without an extra round-trip.
     */
    @SerializedName("members")
    private List<MeshNetworkMemberResponse> members;

    public void setId(String id) {
        this.id = id;
    }

    public void setMeshUuid(String meshUuid) {
        this.meshUuid = meshUuid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }

    public void setVpcName(String vpcName) {
        this.vpcName = vpcName;
    }

    public void setVpcCidr(String vpcCidr) {
        this.vpcCidr = vpcCidr;
    }

    public void setPeerVpcId(String peerVpcId) {
        this.peerVpcId = peerVpcId;
    }

    public void setPeerVpcName(String peerVpcName) {
        this.peerVpcName = peerVpcName;
    }

    public void setPeerVpcCidr(String peerVpcCidr) {
        this.peerVpcCidr = peerVpcCidr;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public void setLinkLocalIp(String linkLocalIp) {
        this.linkLocalIp = linkLocalIp;
    }

    public void setAclId(String aclId) {
        this.aclId = aclId;
    }

    public void setAclName(String aclName) {
        this.aclName = aclName;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setVpcCount(Integer vpcCount) {
        this.vpcCount = vpcCount;
    }

    public void setVpcNames(String vpcNames) {
        this.vpcNames = vpcNames;
    }

    public void setMembers(List<MeshNetworkMemberResponse> members) {
        this.members = members;
    }
}
