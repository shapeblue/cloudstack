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

public class VpcPeeringResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    private String id;

    @SerializedName("groupuuid")
    private String groupUuid;

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

    @SerializedName(ApiConstants.STATE)
    private String state;

    @SerializedName(ApiConstants.CREATED)
    private Date created;

    public void setId(String id) {
        this.id = id;
    }

    public void setGroupUuid(String groupUuid) {
        this.groupUuid = groupUuid;
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

    public void setState(String state) {
        this.state = state;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}
