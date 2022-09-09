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
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import com.cloud.baremetal.manager.VlanType;

/**
 * Created by frank on 9/2/14.
 */
public class BaremetalVlanStruct {
    private String switchType;
    private String switchIp;
    private String switchUsername;
    private String switchPassword;
    private String hostMac;
    private String port;
    private int vlan;
    private VlanType type;
    private boolean removeAll;

    public String getSwitchType() {
        return switchType;
    }

    public void setSwitchType(String switchType) {
        this.switchType = switchType;
    }

    public String getSwitchIp() {
        return switchIp;
    }

    public void setSwitchIp(String switchIp) {
        this.switchIp = switchIp;
    }

    public String getSwitchUsername() {
        return switchUsername;
    }

    public void setSwitchUsername(String switchUsername) {
        this.switchUsername = switchUsername;
    }

    public String getSwitchPassword() {
        return switchPassword;
    }

    public void setSwitchPassword(String switchPassword) {
        this.switchPassword = switchPassword;
    }

    public String getHostMac() {
        return hostMac;
    }

    public void setHostMac(String hostMac) {
        this.hostMac = hostMac;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public int getVlan() {
        return vlan;
    }

    public void setVlan(int vlan) {
        this.vlan = vlan;
    }

    public void setVlanType(VlanType type){
        this.type = type;
    }

    public VlanType getVlanType(){
        return type;
    }

    public boolean isRemoveAll() {
        return removeAll;
    }

    public void setRemoveAll(boolean removeAll) {
        this.removeAll = removeAll;
    }
}
