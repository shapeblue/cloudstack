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

    private String _name;
    private final Class<T> _type;
    private String _description;
    private String _detail;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    private String className;

    public String get_detail() {
        return _detail;
    }

    public void set_detail(String _detail) {
        this._detail = _detail;
    }

    public String get_role() {
        return _role;
    }

    public void set_role(String _role) {
        this._role = _role;
    }

    private String _role;
    T _value = null;

    public Class<T> type() {
        return _type;
    }

    public final String key() {
        return _name;
    }


    public String description() {
        return _description;
    }



    @Override
    public String toString()
    {
        return _name;
    }

    static DiagnosticsConfigDepotImpl s_depot = null;

    static public void init(DiagnosticsConfigDepotImpl depot) {
        s_depot = depot;
    }

   /* public DiagnosticsKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }*/

    public DiagnosticsKey(Class<T> type, String name, String description, String detail, String role) {
        _type = type;
        _name = name;
        _description = description;
        _detail = detail;
        _role = role;
    }

    @Override
    public int hashCode()
    {
        return _name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiagnosticsKey) {
            DiagnosticsKey that = (DiagnosticsKey)obj;
            return this._name.equals(that._name);
        }
        return false;
    }

    public boolean isSameKeyAs(Object obj) {
        if(this.equals(obj)) {
            return true;
        } else if (obj instanceof String) {
            String key = (String)obj;
            return key.equals(_name);
        }

        throw new CloudRuntimeException("Comparing Diagnostics key to " + obj.toString());
    }

    public String value() {
        if (_name == null) {
            RetrieveDiagnosticsVO vo = s_depot != null ? s_depot.global().findById(key()) : null;
            final String value = (vo != null && vo.getValue() != null) ? vo.getValue() : null;
            _name = (String)((value == null) ? null : valueOf(value));
        }

        return _name;
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
