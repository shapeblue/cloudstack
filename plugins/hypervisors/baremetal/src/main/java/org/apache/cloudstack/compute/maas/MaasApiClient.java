/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.compute.maas;

import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaasApiClient {

    private static final Logger s_logger = Logger.getLogger(MaasApiClient.class);

    private static final String SCHEME_HTTP = "http";
    private static final String HEADER_CONTENT_TYPE = "Content-type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_VALUE_JSON = "application/json";
    private static final String HEADER_VALUE_FORM = "application/x-www-form-urlencoded";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
    private static final int DEFAULT_TIMEOUT_SEC = 600;
    private static final int POLL_TIMEOUT_SEC = 2; //2s single pool timeout
    private static final Gson gson = new GsonBuilder().create();
    private static final String API_PREFIX = "/MAAS/api/2.0";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String HEADER_VALUE_TEXT_PLAIN = "text/plain";
    private static final String MODE_DHCP = "dhcp";
    private static final String ENCODING_UTF8 = "UTF-8";


    private final int timeout;
    private final MaasObject.MaasConnection conn;

    public MaasApiClient(String ip, String key, String secret, String consumerKey, int timeoutSec) {

        this.conn = new MaasObject.MaasConnection(ip, key, secret, consumerKey);
        this.timeout = timeoutSec > DEFAULT_TIMEOUT_SEC ? timeoutSec : DEFAULT_TIMEOUT_SEC;

    }

    private void signRequest(HttpRequest request) {

        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> oauthParams = new HashMap<String, String>();

        //oauthParams.put("realm", "");
        oauthParams.put("oauth_version", "1.0");
        oauthParams.put("oauth_signature_method", "PLAINTEXT");

        oauthParams.put("oauth_nonce",  UUID.randomUUID().toString().replaceAll("-", ""));
        oauthParams.put("oauth_timestamp", Long.toString(timestamp));

        oauthParams.put("oauth_consumer_key", conn.getConsumerKey());
        oauthParams.put("oauth_token", conn.getKey());

        String signature = "";
        try {
            signature = "&" + URLEncoder.encode(conn.getSecret(), ENCODING_UTF8);
        } catch (UnsupportedEncodingException e) {
            s_logger.warn(e.getMessage());
            throw new CloudRuntimeException("Unable to sign request " + e.getMessage());
        }

        oauthParams.put("oauth_signature", signature);

        String oauthHeaderValue = buildOauthHeader(oauthParams);

        request.setHeader(HTTP_HEADER_AUTHORIZATION, oauthHeaderValue);
    }

    private static String buildOauthHeader(Map<String, String> oauthParams) {

        StringBuilder header = new StringBuilder();
        header.append("OAuth ");
        header.append(" realm=\"\", ");

        for (Map.Entry<String, String> entry : oauthParams.entrySet()) {
            header.append(String.format("%s=\"%s\", ", entry.getKey(), URLEncoder.encode(entry.getValue())));
        }

        int len = header.length();
        header.delete(len - 2, len - 1);

        return header.toString();
    }

    public String executeApiRequest(HttpRequest request) throws IOException {

        CloseableHttpClient httpclient = HttpClientBuilder.create().build();
        String response = null;

        if (null == httpclient) {
            throw new RuntimeException("Unable to create httpClient for request");
        }

        try {

            if (request.getFirstHeader(HEADER_CONTENT_TYPE) == null) {
                request.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_JSON);
            }
            request.setHeader(HEADER_ACCEPT, HEADER_VALUE_JSON);
            request.setHeader(HEADER_ACCEPT_ENCODING, HEADER_VALUE_TEXT_PLAIN);

            signRequest(request);

            HttpHost target = new HttpHost(conn.getIp(), DEFAULT_HTTP_PORT, SCHEME_HTTP);

            HttpResponse httpResponse = httpclient.execute(target, request);

            HttpEntity entity = httpResponse.getEntity();
            StatusLine status = httpResponse.getStatusLine();
            response = EntityUtils.toString(entity);

            assert response != null;

            if (status.getStatusCode() >= HttpStatus.SC_BAD_REQUEST) {
                // check if this is an error
                String errMesg = "Error: Non successful response: " + request.getRequestLine();
                s_logger.warn(errMesg);
                s_logger.warn(response);
                throw new CloudRuntimeException(errMesg);
            }
        } catch (IOException e) {
            String errMesg = "Error while trying to get HTTP object: " + request.getRequestLine();
            s_logger.warn(errMesg, e);
            throw new CloudRuntimeException("Error while sending request. Error " + e.getMessage());
        }

        return response;
    }

    public MaasObject.MaasNode addMachine(MaasObject.AddMachineParameters addMachineParameters) throws IOException {

        HttpPost addMachineReq = new HttpPost(getApiUrl("machines"));
        addMachineReq.setEntity(new StringEntity(gson.toJson(addMachineParameters)));

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("architecture", addMachineParameters.getArch()));
        params.add(new BasicNameValuePair("power_type", addMachineParameters.getPowerType()));
        params.add(new BasicNameValuePair("mac_addresses", addMachineParameters.getMacAddress()));
        params.add(new BasicNameValuePair("power_parameters_power_user", addMachineParameters.getPowerUser()));
        params.add(new BasicNameValuePair("power_parameters_power_pass", addMachineParameters.getPowerPassword()));
        params.add(new BasicNameValuePair("power_parameters_power_address", addMachineParameters.getPowerAddress()));
        addMachineReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        addMachineReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String response = executeApiRequest(addMachineReq);

        MaasObject.MaasNode node = gson.fromJson(response, MaasObject.MaasNode.class);

        return waitTillReady(node.systemId);
    }

    public boolean deleteMachine(String systemId) throws IOException {

        HttpDelete deleteMachineReq = new HttpDelete(getApiUrl("machines", systemId));

        String resp = executeApiRequest(deleteMachineReq);

        s_logger.info("deleted MAAS machine " + resp);

        return true;
    }

    public void allocateMachine(MaasObject.AllocateMachineParameters allocateMachineParameters) throws IOException {

        String url = addOperationToApiUrl(getApiUrl("machines"), "allocate");
        HttpPost allocateReq = new HttpPost(url);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("system_id", allocateMachineParameters.getSystemId()));
        allocateReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        allocateReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String resp = executeApiRequest(allocateReq);
        s_logger.debug(resp);
    }

    public MaasObject.MaasNode deployMachine(String systemId, MaasObject.DeployMachineParameters deployMachineParameters) throws IOException {

        String url = addOperationToApiUrl(getApiUrl("machines", systemId), "deploy");
        HttpPost deployMachineReq = new HttpPost(url);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("distro_series", deployMachineParameters.getDistroSeries()));
        deployMachineReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        deployMachineReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String resp = executeApiRequest(deployMachineReq);
        s_logger.debug(resp);

        return waitTillDeployed(systemId);
    }

    public MaasObject.MaasNode releaseMachine(String systemId, boolean eraseDisk, boolean fullErase) throws IOException {

        String url = addOperationToApiUrl(getApiUrl("machines", systemId), "release");
        HttpPost releaseMachineReq = new HttpPost(url);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("erase", Boolean.toString(eraseDisk)));
        params.add(new BasicNameValuePair("quick_erase", Boolean.toString(!fullErase)));
        releaseMachineReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        releaseMachineReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String resp = executeApiRequest(releaseMachineReq);
        s_logger.debug(resp);

        return waitTillReady(systemId);
    }

    public MaasObject.MaasNode getMaasNode(String systemId) throws IOException {

        HttpGet maasNode = new HttpGet(getApiUrl("machines", systemId));
        String response = executeApiRequest(maasNode);

        return gson.fromJson(response, MaasObject.MaasNode.class);
    }

    public MaasObject.MaasNode getMaasNodeByMac(String macAddress) throws IOException {

        for (MaasObject.MaasNode node : getMaasNodes()) {
            if (node.bootInterface.macAddress.equals(macAddress.toLowerCase())) {
                return node;
            }
        }

        return null;
    }

    public List<MaasObject.MaasNode> getMaasNodes() throws IOException {

        HttpGet maasNodeReq = new HttpGet(getApiUrl("machines"));

        String response = executeApiRequest(maasNodeReq);

        Type listType = new TypeToken<ArrayList<MaasObject.MaasNode>>(){}.getType();
        return gson.fromJson(response, listType);
    }

    public MaasObject.MaasNode waitTillReady(String systemId) throws IOException {

        int to = this.timeout;
        MaasObject.MaasNode maasNode = null;
        do {
            maasNode = getMaasNode(systemId);
            try {
                Thread.sleep(POLL_TIMEOUT_SEC*1000);
            } catch (InterruptedException e) {
                return null;
            }
            to -= POLL_TIMEOUT_SEC;
        } while ((maasNode != null && !maasNode.statusName.equals(MaasObject.MaasState.Ready.toString())) && to>0);

        if (maasNode == null || (!maasNode.statusName.equals(MaasObject.MaasState.Ready.toString()))) {
            throw new CloudRuntimeException("Operation Timed out: Unable to add node to MAAS with SystemID " + systemId);
        }

        return maasNode;
    }

    private MaasObject.MaasNode waitTillDeployed(String systemId) throws IOException {

        int to = this.timeout;
        MaasObject.MaasNode maasNode = null;
        do {
            maasNode = getMaasNode(systemId);
            try {
                Thread.sleep(POLL_TIMEOUT_SEC*1000);
            } catch (InterruptedException e) {
                return null;
            }
            to-=POLL_TIMEOUT_SEC;
        } while ((maasNode != null && !maasNode.statusName.equals(MaasObject.MaasState.Deployed.toString())) && to>0);

        if (maasNode == null || (!maasNode.statusName.equals(MaasObject.MaasState.Deployed.toString()))) {
            throw new CloudRuntimeException("Unable to deploy node to MAAS with SystemID " + systemId);
        }

        return maasNode;
    }

    public void setDhcpInterface(String systemId, int interfaceId, int linkId, int subnetId) throws IOException {

        String url = addOperationToApiUrl(
                getApiUrl("nodes", systemId, "interfaces", Integer.toString(interfaceId)),
                "unlink_subnet"
        );

        HttpPost unlinkReq = new HttpPost(url);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("id", Integer.toString(linkId)));
        unlinkReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        unlinkReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);
        executeApiRequest(unlinkReq);

        url = addOperationToApiUrl(
                getApiUrl("nodes", systemId, "interfaces", Integer.toString(interfaceId)),
                "link_subnet"
        );

        HttpPost linkReq = new HttpPost(url);
        params = new ArrayList<>();
        params.add(new BasicNameValuePair("mode", MODE_DHCP));
        params.add(new BasicNameValuePair("subnet", Integer.toString(subnetId)));
        linkReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        linkReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);
        executeApiRequest(linkReq);
    }

    public MaasObject.MaasSubnet getDhcpSubnet() throws IOException {
        HttpGet subnetReq = new HttpGet(getApiUrl("subnets"));
        String response = executeApiRequest(subnetReq);

        Type listType = new TypeToken<ArrayList<MaasObject.MaasSubnet>>(){}.getType();
        List<MaasObject.MaasSubnet> subnets = gson.fromJson(response, listType);

        for (MaasObject.MaasSubnet subnet : subnets) {
            if(subnet.vlan.dhcpOn){
                return subnet;
            }
        }
        return null;
    }

    public MaasObject.MaasInterface createBondInterface(String systemId, List<Integer> phyInterfaceIds) throws IOException {
        String url = addOperationToApiUrl(getApiUrl("nodes", systemId, "interfaces"), "create_bond");
        HttpPost createBondReq = new HttpPost(url);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("system_id", systemId));
        params.add(new BasicNameValuePair("name", "bond0"));
        for (Integer phyId : phyInterfaceIds) {
            params.add(new BasicNameValuePair("parents", Integer.toString(phyId)));
        }

        createBondReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        createBondReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String resp = executeApiRequest(createBondReq);

        return gson.fromJson(resp, MaasObject.MaasInterface.class);
    }

    public void updateInterfaceMac(String systemId, int interfaceId, String mac) throws IOException {
        String url = getApiUrl("nodes", systemId, "interfaces", Integer.toString(interfaceId));
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("mac_address", mac));
        HttpPut updateMacReq = new HttpPut(url);

        updateMacReq.setEntity(new UrlEncodedFormEntity(params, ENCODING_UTF8));
        updateMacReq.setHeader(HEADER_CONTENT_TYPE, HEADER_VALUE_FORM);

        String response = executeApiRequest(updateMacReq);
        s_logger.debug("updated interface mac on " + systemId + " to " + mac + " resp " + response);
    }

    public void updateHostname(String systemId, String newHostName) throws IOException {
        String url = getApiUrl("machines", systemId);
        HttpPut updateHostnameReq = new HttpPut(url);
        MaasObject.UpdateHostnameParams params = new MaasObject.UpdateHostnameParams(newHostName);
        updateHostnameReq.setEntity(new StringEntity(gson.toJson(params)));

        executeApiRequest(updateHostnameReq);

    }

    private String getApiUrl(String... args) {

        ArrayList<String> urlList = new ArrayList<String>(Arrays.asList(args));

        urlList.add(0, API_PREFIX);
        urlList.add(urlList.size(), "");
        return StringUtils.join(urlList, "/");

    }

    private String addOperationToApiUrl(String url, String op) {
        return url + "?op=" + op;
    }
}
