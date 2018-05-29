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

public class DiagnosticsKey<T> {
    public static enum DiagnosticsEntryType {
        IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove
    }

    private String name;
    private final Class<T> type;
    private String description;
    private String detail;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    private String className;

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

    private String role;
    T value = null;

    public Class<T> type() {
        return type;
    }

    public final String key() {
        return name;
    }


    public String description() {
        return description;
    }



    @Override
    public String toString()
    {
        return name;
    }

    static DiagnosticsConfigDepotImpl s_depot = null;

    static public void init(DiagnosticsConfigDepotImpl depot) {
        s_depot = depot;
    }

   /* public DiagnosticsKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }*/

    public DiagnosticsKey(Class<T> type, String name, String description, String detail, String role) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.detail = detail;
        this.role = role;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiagnosticsKey) {
            DiagnosticsKey that = (DiagnosticsKey)obj;
            return this.name.equals(that.name);
        }
        return false;
    }

    public boolean isSameKeyAs(Object obj) {
        if(this.equals(obj)) {
            return true;
        } else if (obj instanceof String) {
            String key = (String)obj;
            return key.equals(name);
        }

        throw new CloudRuntimeException("Comparing Diagnostics key to " + obj.toString());
    }

    public String value() {
        if (name == null) {
            RetrieveDiagnosticsVO vo = s_depot != null ? s_depot.global().findById(key()) : null;
            final String value = (vo != null && vo.getValue() != null) ? vo.getValue() : null;
            name = (String)((value == null) ? null : valueOf(value));
        }

        return name;
    }

    public String valueIn(Long id) {
        if (id == null) {
            return value();
        }

        String value = s_depot != null ? s_depot.global().getValue(key()) : null;
        if (value == null) {
            return value();
        } else {
            return valueOf(value);
        }
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

/*    public static DiagnosticsKey<?> getDiagnosticsClassKeys() {
        return new DiagnosticsKey<?>[] { IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove};
    }*/

}
