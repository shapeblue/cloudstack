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
package com.cloud.hypervisor.vmware.util;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.vmware.cis.Session;
import com.vmware.content.Library;
import com.vmware.content.LocalLibrary;
import com.vmware.content.library.Item;
import com.vmware.content.library.item.UpdateSession;
import com.vmware.content.library.item.updatesession.File;

import com.vmware.vapi.bindings.Service;
import com.vmware.vapi.bindings.StubConfiguration;
import com.vmware.vapi.bindings.StubFactory;
import com.vmware.vapi.cis.authn.ProtocolFactory;
import com.vmware.vapi.cis.authn.SecurityContextFactory;
import com.vmware.vapi.core.ApiProvider;
import com.vmware.vapi.core.ExecutionContext.SecurityContext;
import com.vmware.vapi.protocol.HttpConfiguration;
import com.vmware.vapi.protocol.ProtocolConnection;
import com.vmware.vapi.security.SessionSecurityContext;

import com.vmware.vcenter.Datastore;
import com.vmware.vcenter.ovf.LibraryItem;

public class ContentLibraryClient implements VmwareClientService {
    private static final Logger s_logger = Logger.getLogger(ContentLibraryClient.class);

    public static final String VAPI_PATH = "/api";

    //Configuration
    public static final String HTTP_CONFIG = "httpconfig";

    private Session sessionService;
    private StubFactory stubFactory;
    private StubConfiguration sessionStubConfig;

    private Library libraryService;
    private LocalLibrary localLibraryService;
    private Item itemService;
    private LibraryItem libraryItemService;
    private Datastore datastoreService;
    private UpdateSession updateSessionService;
    private File fileService;

    public ContentLibraryClient() {
    }

    @Override
    public boolean login(String vCenterAddress, String userName, String password, Map<String, Object> configProperties) throws Exception {
        if(StringUtils.isBlank(vCenterAddress) || StringUtils.isBlank(userName) || StringUtils.isBlank(password)) {
            s_logger.debug("Invalid vCenter credentials");
            return false;
        }

        if (configProperties == null || configProperties.isEmpty()) {
            s_logger.debug("Login failed, configuration properties required");
            return false;
        }

        HttpConfiguration httpConfig = (HttpConfiguration) configProperties.get(HTTP_CONFIG);
        if (httpConfig == null) {
            s_logger.debug("Login failed, http configuration not found");
            return false;
        }

        stubFactory = createApiStubFactory(vCenterAddress, httpConfig);
        SecurityContext securityContext = SecurityContextFactory.createUserPassSecurityContext(userName, password.toCharArray());

        sessionStubConfig = new StubConfiguration(securityContext);
        Session session = stubFactory.createStub(Session.class, sessionStubConfig);

        char[] sessionId = session.create();
        SessionSecurityContext sessionSecurityContext = new SessionSecurityContext(sessionId);
        sessionStubConfig.setSecurityContext(sessionSecurityContext);
        sessionService = stubFactory.createStub(Session.class, sessionStubConfig);

        // Library services
        libraryService = getService(Library.class);
        localLibraryService = getService(LocalLibrary.class);

        // Library item services
        itemService = getService(Item.class);
        libraryItemService = getService(LibraryItem.class);

        datastoreService = getService(Datastore.class);
        fileService = getService(File.class);
        updateSessionService = getService(UpdateSession.class);

        return true;
    }

    @Override
    public boolean logout() throws Exception {
        if (sessionService != null) {
            sessionService.delete();
        }

        return true;
    }

    private StubFactory createApiStubFactory(String server, HttpConfiguration httpConfig)  throws Exception {
        // Create a https connection with the vapi url
        ProtocolFactory pf = new ProtocolFactory();
        String apiUrl = "https://" + server + VAPI_PATH;

        // Get a connection to the vapi url
        ProtocolConnection connection = pf.getHttpConnection(apiUrl, null, httpConfig);

        // Initialize the stub factory with the api provider
        ApiProvider provider = connection.getApiProvider();
        StubFactory stubFactory = new StubFactory(provider);
        return stubFactory;
    }

    public Library getLibrary() {
        return this.libraryService;
    }

    public LocalLibrary getLocalLibrary() {
        return this.localLibraryService;
    }

    public Item getItem() {
        return this.itemService;
    }

    public LibraryItem getLibraryItem() {
        return this.libraryItemService;
    }

    public Datastore getDatastore() {
        return this.datastoreService;
    }

    public File getFile() {
        return this.fileService;
    }

    public UpdateSession getUpdateSession() {
        return this.updateSessionService;
    }

    private <T extends Service> T getService(Class<T> serviceClass) {
        return (T)  stubFactory.createStub(serviceClass, sessionStubConfig);
    }
}
