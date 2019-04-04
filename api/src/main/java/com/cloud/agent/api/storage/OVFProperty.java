//
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

package com.cloud.agent.api.storage;

/**
 * <Property ovf:key="RouteDefault" ovf:type="string" ovf:qualifiers="ValueMap{&quot;Default Route&quot;,&quot;Remote HTTP and SSH Client Routes&quot;}" ovf:value="Default Route" ovf:userConfigurable="true">
 *         <Label>Select Route Type</Label>
 *         <Description>Select the route/gateway type.
 * Choose "Default Route" to route all traffic through the Management gateway. Use this option when enabling Smart Licensing registration at initial deployment.
 * Choose "Remote HTTP and SSH Client Routes" to route only traffic destined for the management client(s), when they are on remote networks.</Description>
 *       </Property>
 */
public class OVFProperty {

    private String key;
    private String type;
    private String value;
    private String qualifiers;
    private boolean userConfigurable;
    private String label;
    private String description;

    public OVFProperty(String key, String type, String value, String qualifiers, boolean userConfigurable,
                String label, String description) {
        this.key = key;
        this.type = type;
        this.value = value;
        this.qualifiers = qualifiers;
        this.userConfigurable = userConfigurable;
        this.label = label;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getQualifiers() {
        return qualifiers;
    }

    public void setQualifiers(String qualifiers) {
        this.qualifiers = qualifiers;
    }

    public boolean isUserConfigurable() {
        return userConfigurable;
    }

    public void setUserConfigurable(boolean userConfigurable) {
        this.userConfigurable = userConfigurable;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}