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
package com.cloud.resource.icon;

import com.cloud.server.ResourceIcon;
import com.cloud.server.ResourceTag;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;

import java.util.UUID;

@Entity
@Table(name = "resource_icon")
public class ResourceIconVO implements ResourceIcon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "resource_id")
    long resourceId;

    @Column(name = "resource_uuid")
    private String resourceUuid;

    @Column(name = "resource_type")
    @Enumerated(value = EnumType.STRING)
    private ResourceTag.ResourceObjectType resourceType;

    @Column(name = "resource_icon", length = 65535 )
    private String resourceIcon;

    protected ResourceIconVO() {
        uuid = UUID.randomUUID().toString();
    }

    public ResourceIconVO(long resourceId, ResourceTag.ResourceObjectType resourceType, String resourceUuid, String resourceIcon) {
        super();
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        uuid = UUID.randomUUID().toString();
        this.resourceUuid = resourceUuid;
        this.resourceIcon = resourceIcon;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getResourceId() {
        return resourceId;
    }

    public void setResourceId(long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceUuid() {
        return resourceUuid;
    }

    public void setResourceUuid(String resourceUuid) {
        this.resourceUuid = resourceUuid;
    }

    public ResourceTag.ResourceObjectType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceTag.ResourceObjectType resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceIcon() {
        return resourceIcon;
    }

    public void setResourceIcon(String resourceIcon) {
        this.resourceIcon = resourceIcon;
    }
}
