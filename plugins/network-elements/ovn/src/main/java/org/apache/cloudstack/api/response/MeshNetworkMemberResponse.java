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

/**
 * One member's record inside a mesh network, embedded under
 * {@link MeshNetworkResponse#setMembers(java.util.List)} on group-level responses.
 * The member is either a VPC or an Isolated guest network — {@code kind} carries
 * that distinction, and exactly one of {@code vpcid}/{@code networkid} is set.
 * Carries the per-member row identity so the UI can drive add/remove actions
 * without a separate listMeshNetworks round-trip.
 */
public class MeshNetworkMemberResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    private String id;

    /**
     * "vpc" or "network" — distinguishes whether this row's member is a VPC or
     * an isolated guest network. Drives the UI rendering choice (link target,
     * icon, allowed actions).
     */
    @SerializedName("kind")
    private String kind;

    @SerializedName(ApiConstants.VPC_ID)
    private String vpcId;

    @SerializedName("vpcname")
    private String vpcName;

    @SerializedName("vpccidr")
    private String vpcCidr;

    @SerializedName(ApiConstants.NETWORK_ID)
    private String networkId;

    @SerializedName("networkname")
    private String networkName;

    @SerializedName("networkcidr")
    private String networkCidr;

    /**
     * Convenience name field — copy of vpcname or networkname depending on
     * kind, so UI columns and dropdowns can render member names without
     * branching on kind.
     */
    @SerializedName("membername")
    private String memberName;

    @SerializedName("membercidr")
    private String memberCidr;

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

    public void setId(String id) { this.id = id; }
    public void setKind(String kind) { this.kind = kind; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }
    public void setVpcName(String vpcName) { this.vpcName = vpcName; }
    public void setVpcCidr(String vpcCidr) { this.vpcCidr = vpcCidr; }
    public void setNetworkId(String networkId) { this.networkId = networkId; }
    public void setNetworkName(String networkName) { this.networkName = networkName; }
    public void setNetworkCidr(String networkCidr) { this.networkCidr = networkCidr; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public void setMemberCidr(String memberCidr) { this.memberCidr = memberCidr; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public void setLinkLocalIp(String linkLocalIp) { this.linkLocalIp = linkLocalIp; }
    public void setAclId(String aclId) { this.aclId = aclId; }
    public void setAclName(String aclName) { this.aclName = aclName; }
    public void setState(String state) { this.state = state; }
}
