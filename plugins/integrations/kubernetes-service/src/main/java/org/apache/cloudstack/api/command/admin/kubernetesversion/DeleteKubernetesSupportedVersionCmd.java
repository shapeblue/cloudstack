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

package org.apache.cloudstack.api.command.admin.kubernetesversion;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.KubernetesSupportedVersionResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.kubernetesversion.KubernetesSupportedVersion;
import com.cloud.kubernetesversion.KubernetesVersionEventTypes;
import com.cloud.kubernetesversion.KubernetesVersionService;

@APICommand(name = DeleteKubernetesSupportedVersionCmd.APINAME,
        description = "Deletes a Kubernetes cluster",
        responseObject = SuccessResponse.class,
        entityType = {KubernetesSupportedVersion.class},
        authorized = {RoleType.Admin})
public class DeleteKubernetesSupportedVersionCmd extends BaseAsyncCmd {
    public static final Logger LOGGER = Logger.getLogger(DeleteKubernetesSupportedVersionCmd.class.getName());
    public static final String APINAME = "deleteKubernetesSupportedVersion";

    @Inject
    private KubernetesVersionService kubernetesVersionService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID,
            entityType = KubernetesSupportedVersionResponse.class,
            description = "the ID of the Kubernetes supported version",
            required = true)
    private Long id;
    @Parameter(name = "deleteIso", type = CommandType.BOOLEAN,
            description = "true if ISO associated with the Kubernetes version to be deleted else false. Default is false")
    private Boolean deleteIso;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getId() {
        return id;
    }

    public Boolean isDeleteIso() {
        return deleteIso;
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public String getEventType() {
        return KubernetesVersionEventTypes.EVENT_KUBERNETES_VERSION_DELETE;
    }

    @Override
    public String getEventDescription() {
        return "Deleting Kubernetes supported version " + getId();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        boolean result = kubernetesVersionService.deleteKubernetesSupportedVersion(this);
        if (result) {
            SuccessResponse response = new SuccessResponse(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to delete supported Kubernetes version");
        }
    }
}
