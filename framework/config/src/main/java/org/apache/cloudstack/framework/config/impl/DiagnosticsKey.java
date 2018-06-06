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


package org.apache.cloudstack.framework.config.impl;

import com.cloud.utils.exception.CloudRuntimeException;

import java.util.List;

public class DiagnosticsKey {
    public static enum DiagnosticsEntryType {
        IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove;
        @Override
        public String toString() {
            return "IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove";
        }
    }

    private String role;
    private String diagnosticsClassType;
    private String description;
    private String detail;

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDiagnosticsClassType() {
        return diagnosticsClassType;
    }

    public void setDiagnosticsClassType(String diagnosticsClassType) {
        this.diagnosticsClassType = diagnosticsClassType;
    }

    public final String key() {
        return diagnosticsClassType;
    }


    public String description() {
        return description;
    }



    @Override
    public String toString()
    {
        return diagnosticsClassType;
    }

    static DiagnosticsConfigDepotImpl s_depot = new DiagnosticsConfigDepotImpl();


   public DiagnosticsKey() {

   }

    public DiagnosticsKey(String role, String diagnosticsType, String detail, String description) {
        this.diagnosticsClassType = diagnosticsType;
        this.description = description;
        this.detail = detail;
        this.role = role;
    }

    @Override
    public int hashCode()
    {
        return diagnosticsClassType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiagnosticsKey) {
            DiagnosticsKey that = (DiagnosticsKey)obj;
            return this.diagnosticsClassType.equals(that.diagnosticsClassType);
        }
        return false;
    }

    public boolean isSameKeyAs(Object obj) {
        if(this.equals(obj)) {
            return true;
        } else if (obj instanceof String) {
            String key = (String)obj;
            return key.equals(diagnosticsClassType);
        }

        throw new CloudRuntimeException("Comparing Diagnostics key to " + obj.toString());
    }

    public String value() {
        if (diagnosticsClassType == null) {
            RetrieveDiagnosticsVO vo = s_depot != null ? s_depot.global().findById(key()) : null;
            final String value = (vo != null && vo.getDefaultValue() != null) ? vo.getDefaultValue() : null;
            diagnosticsClassType = (String)((value == null) ? null : valueOf(value));
        }

        return diagnosticsClassType;
    }

    public String valueIn(String id) {
        if (id == null) {
            return value();
        }

        List<RetrieveDiagnosticsVO> valueVO = s_depot != null ? s_depot.global().findByEntityType((key())) : null;
        for (RetrieveDiagnosticsVO vo : valueVO) {
            String value = vo.getType().toString();
            if (value == null) {
                return value();
            } else {
                return valueOf(value);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected String valueOf(String value) {
        Class type = value.getClass();
        if (!type.isAssignableFrom(String.class)) {
            throw new CloudRuntimeException("Unsupported data type for config values: " + type);
        } else {
            return  String.valueOf(value);
        }
    }

    public DiagnosticsEntryType[] getDiagnosticsTypeKeys() {
        return DiagnosticsEntryType.values();
    }

}
