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

import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;
import org.apache.cloudstack.api.command.user.remoteregion.AddRemoteRegionCmd;
import org.apache.cloudstack.api.command.user.remoteregion.DeleteRemoteRegionCmd;
import org.apache.cloudstack.api.command.user.remoteregion.ListRemoteRegionsCmd;
import org.apache.cloudstack.api.command.user.remoteregion.RemoteExecuteCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.RemoteRegionResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.remoteregion.dao.RemoteRegionDao;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class RemoteRegionManagerImpl extends ManagerBase implements RemoteRegionManager {

    protected static final ConfigKey<Boolean> AllowRemoteRegions = new ConfigKey<Boolean>("Advanced", Boolean.class,
            "remote.region.enabled", "true", "Whether remote region functionality is enabled", true);
    protected org.apache.logging.log4j.Logger logger = LogManager.getLogger(getClass());
    @Inject
    private RemoteRegionDao remoteRegionDao;


    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(AddRemoteRegionCmd.class);
        cmdList.add(DeleteRemoteRegionCmd.class);
        cmdList.add(ListRemoteRegionsCmd.class);
        cmdList.add(RemoteExecuteCmd.class);
        return cmdList;
    }

    @Override
    public RemoteRegionResponse addRemoteRegion(String description, String endpoint, String apiKey,
                                                String secretKey, Boolean sslVerify, String scope) {
        RemoteRegionVO region = new RemoteRegionVO(description, endpoint, apiKey, secretKey);
        if (sslVerify != null) {
            region.setSslVerify(sslVerify);
        }
        if (scope != null) {
            region.setScope(scope);
        }

        try {
            region = remoteRegionDao.persist(region);
        } catch (Exception e) {
            logger.error("Failed to add remote region", e);
            return null;
        }
        return new RemoteRegionResponse(region);
    }

    @Override
    public boolean deleteRemoteRegion(String id) {
        try {
            RemoteRegionVO region = remoteRegionDao.findByUuid(id);
            if (region == null) {
                return false;
            }
            return remoteRegionDao.remove(region.getId());
        } catch (Exception e) {
            logger.error("Failed to delete remote region", e);
            return false;
        }
    }

    @Override
    public ListResponse<RemoteRegionResponse> listRemoteRegions(String id, String endpoint) {
        ListResponse<RemoteRegionResponse> responseList = new ListResponse<>();
        try {
            if (id != null) {
                RemoteRegionVO region = remoteRegionDao.findByUuid(id);
                responseList.setResponses(List.of(new RemoteRegionResponse(region)), 1);
            } else if (endpoint != null) {
                List<RemoteRegionVO> remoteRegions = remoteRegionDao.listByEndpoint(endpoint);
                responseList.setResponses(remoteRegions.stream()
                        .map(RemoteRegionResponse::new)
                        .collect(Collectors.toList()), remoteRegions.size());
            }

            return responseList;
        } catch (Exception e) {
            logger.error("Failed to list remote regions", e);
            throw new CloudRuntimeException("Failed to list remote regions", e);
        }
    }

    @Override
    public Object executeRemoteCommand(String regionId, String command, Map<String, String> parameters) throws Exception {
        RemoteRegionVO region = remoteRegionDao.findByUuid(regionId);
        if (region == null) {
            throw new CloudRuntimeException("Remote region not found: " + regionId);
        }

        // Create a sorted map of all parameters
        TreeMap<String, String> sortedParams = new TreeMap<>();
        if (parameters != null) {
            sortedParams.putAll(parameters);
        }
        sortedParams.put("command", command);
        sortedParams.put("apiKey", region.getApiKey());
        sortedParams.put("response", "json");

        // Generate signature
        StringBuilder signatureStr = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            signatureStr.append(entry.getKey().toLowerCase())
                    .append('=')
                    .append(entry.getValue())
                    .append('&');
        }
        // Remove last &
        if (signatureStr.length() > 0) {
            signatureStr.setLength(signatureStr.length() - 1);
        }

        // Calculate signature
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(region.getSecretKey().getBytes(), "HmacSHA1");
        mac.init(keySpec);
        byte[] signatureBytes = mac.doFinal(signatureStr.toString().getBytes());
        String signature = Base64.getEncoder().encodeToString(signatureBytes);

        sortedParams.put("signature", signature);

        // Execute HTTP request
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(region.getEndpoint());

            // Convert parameters to JSON
            String jsonParams = new Gson().toJson(sortedParams);
            StringEntity entity = new StringEntity(jsonParams);
            post.setEntity(entity);
            post.setHeader("Content-Type", "application/json");

            // Execute and get response
            String response = EntityUtils.toString(client.execute(post).getEntity());
            return new Gson().fromJson(response, Object.class);
        } catch (Exception e) {
            logger.error("Failed to execute remote command", e);
            throw e;
        }
    }
}
