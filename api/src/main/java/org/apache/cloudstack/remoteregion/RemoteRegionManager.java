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
package org.apache.cloudstack.remoteregion;

import java.util.Map;

import com.cloud.utils.component.Manager;
import com.cloud.utils.component.PluggableService;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.RemoteRegionResponse;

public interface RemoteRegionManager extends PluggableService, Manager {
    /**
     * Add a new remote region
     *
     * @param description Description of the remote region
     * @param endpoint    Endpoint URL of the remote region
     * @param apiKey      API key for authentication
     * @param secretKey   Secret key for authentication
     * @param sslVerify   Whether to verify SSL certificates
     * @param scope       Access scope for the remote region
     * @return RemoteRegion object if successful, null otherwise
     */
    RemoteRegionResponse addRemoteRegion(String description, String endpoint, String apiKey,
                                         String secretKey, Boolean sslVerify, String scope);

    /**
     * Delete a remote region by ID
     *
     * @param id UUID of the remote region to delete
     * @return true if successful, false otherwise
     */
    boolean deleteRemoteRegion(String id);

    /**
     * List remote regions with optional filtering
     *
     * @param id       Optional UUID to filter by
     * @param endpoint Optional endpoint URL to filter by
     * @return List of matching RemoteRegion objects
     */
    ListResponse<RemoteRegionResponse> listRemoteRegions(String id, String endpoint);

    /**
     * Executes an API command on a remote region
     *
     * @param regionId UUID of the remote region
     * @param command API command to execute
     * @param parameters Optional parameters for the command
     * @return Response object from the remote API call
     * @throws Exception if the execution fails
     */
    Object executeRemoteCommand(String regionId, String command, Map<String, String> parameters) throws Exception;
}
