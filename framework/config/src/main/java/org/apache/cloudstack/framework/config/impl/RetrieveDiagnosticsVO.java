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

import com.cloud.utils.db.GenericDao;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "diagnosticsdata")
public class RetrieveDiagnosticsVO implements RetrieveDiagnostics {

    @Column(name = "role")
    private String role;

    @Column(name = "class")
    private DiagnosticsKey.DiagnosticsEntryType className;

    @Column(name = "value", length = 8191)
    private String value;

    @Column(name = GenericDao.CREATED_COLUMN)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;



    protected RetrieveDiagnosticsVO() {
        this.role.toString();
    }

    public RetrieveDiagnosticsVO(String role, String className, String value) {
        this();
        setRole(role);
        setDiagnosticsType(className);
        setDefaultValue(value);
    }

    public RetrieveDiagnosticsVO(String role, DiagnosticsKey.DiagnosticsEntryType type, String value) {
        this();
        setRole(role);
        setDiagnosticsType(type.toString());
        setDefaultValue(value);
    }

    public RetrieveDiagnosticsVO(String name, String value) {
        this.role = name;
        setDefaultValue(value);
    }

    @Override
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setDiagnosticsType(String diagnosticsType) {
        this.className = DiagnosticsKey.DiagnosticsEntryType.valueOf(diagnosticsType);
    }

    public void setDiagnosticsType(DiagnosticsKey.DiagnosticsEntryType diagnosticsType) {
        this.className = diagnosticsType;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public Date getRemoved() {
        return removed;
    }

    @Override
    public String getDefaultValue() {
        return value;
    }

    public void setDefaultValue(String value) {
        this.value = value;
    }

    @Override
    public DiagnosticsKey.DiagnosticsEntryType getDiagnosticsType() {
        return className;
    }

}
