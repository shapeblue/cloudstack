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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.vmware.vim25.ManagedObjectReference;

import com.vmware.content.library.ItemModel;
import com.vmware.content.LibraryModel;
import com.vmware.content.LibraryTypes;
import com.vmware.content.library.ItemTypes;
import com.vmware.content.library.StorageBacking;
import com.vmware.content.library.item.TransferEndpoint;
import com.vmware.content.library.item.UpdateSessionModel;

import com.vmware.vapi.std.LocalizableMessage;

import com.vmware.vcenter.Datastore;
import com.vmware.vcenter.DatastoreTypes;
import com.vmware.vcenter.ovf.OvfError;
import com.vmware.vcenter.ovf.OvfInfo;
import com.vmware.vcenter.ovf.OvfMessage;
import com.vmware.vcenter.ovf.OvfWarning;
import com.vmware.vcenter.ovf.ParseIssue;
import com.vmware.vcenter.ovf.LibraryItemTypes;
import com.vmware.vcenter.ovf.LibraryItemTypes.DeploymentResult;
import com.vmware.vcenter.ovf.LibraryItemTypes.DeploymentTarget;
import com.vmware.vcenter.ovf.LibraryItemTypes.ResourcePoolDeploymentSpec;

public class ContentLibraryHelper {
    private static final Logger s_logger = Logger.getLogger(ContentLibraryHelper.class);
    private static final String HEADING_ADDITIONAL_INFO = "Additional information :";

    public static boolean createContentLibrary(VmwareContext context, String datastoreName, String libraryName) throws Exception {
        if (StringUtils.isBlank(datastoreName) || StringUtils.isBlank(libraryName)) {
            return false;
        }

        if (getContentLibraryByName(context, libraryName) != null) {
            s_logger.debug("Failed to create, content library with the given name: " + libraryName + " already exists");
            return false;
        }

        // Build the storage backing for the library to be created
        StorageBacking storageBacking = createStorageBacking(context, datastoreName);

        // Build the specification for the library to be created
        LibraryModel createSpec = new LibraryModel();
        createSpec.setName(libraryName);
        createSpec.setDescription("Local content library for datastore " + datastoreName);
        createSpec.setType(LibraryModel.LibraryType.LOCAL);
        createSpec.setStorageBackings(Collections.singletonList(storageBacking));

        // Create a content library
        String clientToken = UUID.randomUUID().toString();
        String libraryId = context.getVimClient().getContentLibrary().getLocalLibrary().create(clientToken, createSpec);
        if (StringUtils.isBlank(libraryId)) {
            return false;
        }

        s_logger.debug("Content library created: " + libraryName + " on the datastore: " + datastoreName);
        return true;
    }

    public static boolean deleteContentLibrary(VmwareContext context, String datastoreName, String libraryName) throws Exception {
        if (StringUtils.isBlank(datastoreName) || StringUtils.isBlank(libraryName)) {
            return false;
        }

        String libraryId = getContentLibraryByName(context, libraryName);
        if (libraryId == null) {
            s_logger.debug("Failed to delete, content library with the given name: " + libraryName + " doesn't exists");
            return false;
        }

        LibraryModel localLibrary = context.getVimClient().getContentLibrary().getLocalLibrary().get(libraryId);
        if (localLibrary == null) {
            s_logger.debug("Failed to delete, library: " + libraryName + " not found");
            return false;
        }

        //Get the storage backing on the datastore
        StorageBacking dsStorageBacking = createStorageBacking(context, datastoreName);
        boolean canDelete = false;
        for (Iterator<StorageBacking> iterator = localLibrary.getStorageBackings().iterator(); iterator.hasNext();) {
            StorageBacking storageBacking = (StorageBacking) iterator.next();
            if(dsStorageBacking.equals(storageBacking)) {
                canDelete = true;
                break;
            }
        }

        if(!canDelete) {
            s_logger.debug("Can not delete, library: " + libraryName + " not found in datastore: " + datastoreName);
            return false;
        }

        // Delete the content library
        context.getVimClient().getContentLibrary().getLocalLibrary().delete(localLibrary.getId());
        s_logger.debug("Deleted content library : " + libraryName + " on the datastore: " + datastoreName);
        return true;
    }

    public static boolean importOvfFromDatastore(VmwareContext context, String sourceOvfFileUri, String sourceOvfFileName, String targetLibraryName, String targetOvfName) throws InterruptedException, ExecutionException {
        if (StringUtils.isBlank(sourceOvfFileUri) || StringUtils.isBlank(sourceOvfFileName)
                || StringUtils.isBlank(targetLibraryName) || StringUtils.isBlank(targetOvfName)) {
            return false;
        }

        String libraryId = getContentLibraryByName(context, targetLibraryName);
        if (libraryId == null) {
            s_logger.error("Failed to import ovf, library: " + targetLibraryName + " doesn't exists");
            return false;
        }

        String itemId = createOvfItem(context, libraryId, targetOvfName);

        UpdateSessionModel updateSessionModel = new UpdateSessionModel();
        updateSessionModel.setLibraryItemId(itemId);
        String sessionId = context.getVimClient().getContentLibrary().getUpdateSession().create(UUID.randomUUID().toString(), updateSessionModel);

        com.vmware.content.library.item.updatesession.FileTypes.AddSpec file = new com.vmware.content.library.item.updatesession.FileTypes.AddSpec();
        file.setName(sourceOvfFileName);
        file.setSourceType(com.vmware.content.library.item.updatesession.FileTypes.SourceType.PULL);

        String sourceOvfUri = sourceOvfFileUri + sourceOvfFileName;
        s_logger.debug("Source ovf uri: " + sourceOvfUri + " to be imported to library: " + targetLibraryName + ", with name: " + targetOvfName);

        TransferEndpoint sourceEndPoint = new TransferEndpoint();
        sourceEndPoint.setUri(URI.create(sourceOvfUri));
        file.setSourceEndpoint(sourceEndPoint);

        context.getVimClient().getContentLibrary().getFile().add(sessionId, file);
        context.getVimClient().getContentLibrary().getUpdateSession().complete(sessionId);
        s_logger.debug("Ovf: " + sourceOvfUri + " import initiated to library: " + targetLibraryName + ", with session: " + sessionId);
        boolean status = waitForUpdateSession(context, sessionId);
        s_logger.debug("Ovf: " + sourceOvfUri + " import completed to library: " + targetLibraryName + ", with session: " + sessionId);

        return status;
    }

    private static boolean waitForUpdateSession(VmwareContext context, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return false;
        }

        UpdateSessionModel updateSessionModel = context.getVimClient().getContentLibrary().getUpdateSession().get(sessionId);
        UpdateSessionModel.State state = updateSessionModel.getState();

        while (state == UpdateSessionModel.State.ACTIVE) {
            updateSessionModel = context.getVimClient().getContentLibrary().getUpdateSession().get(sessionId);
            state = updateSessionModel.getState();
        }

        if (state == UpdateSessionModel.State.DONE) {
            s_logger.debug("Ovf importing completed for sessionId: " + sessionId);
            return true;
        } else if (state == UpdateSessionModel.State.ERROR) {
            s_logger.debug("Ovf importing failed for sessionId: " + sessionId);
            return false;
        }

        return false;
    }

    public static boolean deployOvf(VmwareContext context, String sourcelibraryName, String sourceovfName, String vmName, ManagedObjectReference resourcePoolMor, ManagedObjectReference datastoreMor) throws Exception {
        String libraryId = getContentLibraryByName(context, sourcelibraryName);
        if (libraryId == null) {
            return false;
        }

        String itemId = getContentLibraryItemByName(context, libraryId, sourceovfName);
        if (itemId == null) {
            return false;
        }

        DeploymentTarget target = new DeploymentTarget();
        target.setResourcePoolId(resourcePoolMor.getValue());

        // Create a resource pool deployment spec
        ResourcePoolDeploymentSpec spec = createResourcePoolDeploymentSpec(vmName, datastoreMor);

        // Deploy the OVF library item with the spec, on the target
        DeploymentResult result = context.getVimClient().getContentLibrary().getLibraryItem().deploy(null, itemId, target, spec);

        displayOperationResult(result.getSucceeded(), result.getError());

        return result.getSucceeded();
    }

    private static ResourcePoolDeploymentSpec createResourcePoolDeploymentSpec(String entityName, ManagedObjectReference datastoreMor) {
        ResourcePoolDeploymentSpec spec = new ResourcePoolDeploymentSpec();
        spec.setAcceptAllEULA(true);
        spec.setName(entityName);
        spec.setAnnotation("CloudStack VM:" + entityName);

        String datastoreId = datastoreMor.getValue();
        if (StringUtils.isNotBlank(datastoreId)) {
            spec.setDefaultDatastoreId(datastoreId);
        }
        return spec;
    }

    public static void displayOperationResult(boolean operationSucceeded, LibraryItemTypes.ResultInfo operationResult) {
        boolean displayHeader = true;
        LibraryItemTypes.ResultInfo info = operationResult;

        if (operationSucceeded) {
            s_logger.debug("OVF item deployment succeeded");
        } else {
            s_logger.debug("OVF item deployment failed");
            // print only failure information here
            if (info != null) {
                List<OvfError> errors = info.getErrors();
                if (!errors.isEmpty() /* to decide if header needs to be printed */ ) {
                    s_logger.debug(HEADING_ADDITIONAL_INFO);
                    displayHeader = false;

                    for (OvfError error : errors) {
                        printOvfMessage(error._convertTo(OvfMessage.class));
                    }
                }
            }
        }

        // display information in both the success and failure cases
        if (info != null) {
            List<OvfWarning> warnings = info.getWarnings();
            List<OvfInfo> additionalInfo = info.getInformation();

            // little bit of pretty print
            if (!warnings.isEmpty() || !additionalInfo.isEmpty()) {
                s_logger.debug(HEADING_ADDITIONAL_INFO);
                displayHeader = false; // for completeness
            }
            // display warnings
            for (OvfWarning warning : warnings) {
                printOvfMessage(warning._convertTo(OvfMessage.class));
            }
            // display addition info
            for (OvfInfo information : additionalInfo) {
                List<LocalizableMessage> messages =
                        information.getMessages();
                for (LocalizableMessage message : messages) {
                    s_logger.debug("Information: " + message.getDefaultMessage());
                }
            }
        }
    }

    private static void printOvfMessage(OvfMessage ovfMessage) {
        if (ovfMessage.getCategory().equals(OvfMessage.Category.SERVER)) {
            List<LocalizableMessage> messages =
                    ovfMessage.getError()._convertTo(com.vmware.vapi.std.errors.Error.class).getMessages();
            for (LocalizableMessage message : messages) {
                s_logger.debug("Server error message: " + message);
            }
        } else if (ovfMessage.getCategory().equals(
                OvfMessage.Category.VALIDATION)) {
            for (ParseIssue issue : ovfMessage.getIssues()) {
                s_logger.debug("Issue message: " + issue.getMessage());
            }
        } else if (ovfMessage.getCategory().equals(OvfMessage.Category.INPUT)) {
            s_logger.debug("Input validation message: " + ovfMessage.getMessage());
        }
    }

    private static String getContentLibraryByName(VmwareContext context, String libraryName) {
        LibraryTypes.FindSpec findSpec = new LibraryTypes.FindSpec();
        findSpec.setName(libraryName);
        List<String> libraryIds = context.getVimClient().getContentLibrary().getLibrary().find(findSpec);
        if (!libraryIds.isEmpty()) {
            s_logger.debug("Found content library with name: " + libraryName);
            String libraryId = libraryIds.get(0);
            return libraryId;
        }

        s_logger.debug("Couldn't find the content library with name: " + libraryName);
        return null;
    }

    private static String getContentLibraryItemByName(VmwareContext context, String libraryId, String libraryItemName) {
        ItemTypes.FindSpec findSpec = new ItemTypes.FindSpec();
        findSpec.setLibraryId(libraryId);
        findSpec.setName(libraryItemName);
        List<String> itemIds = context.getVimClient().getContentLibrary().getItem().find(findSpec);
        if (!itemIds.isEmpty()) {
            String itemId = itemIds.get(0);
            s_logger.debug("Found library item : " + libraryItemName);
            return itemId;
        }

        s_logger.debug("Couldn't find the content library item with name: " + libraryItemName);
        return null;
    }

    private static String createOvfItem(VmwareContext context, String libraryId, String itemName) {
        return createLibraryItem(context, libraryId, itemName, "ovf");
    }

    private static String createLibraryItem(VmwareContext context, String libraryId, String itemName, String type) {
        ItemModel item = new ItemModel();
        item.setName(itemName);
        item.setLibraryId(libraryId);
        item.setType(type);
        return context.getVimClient().getContentLibrary().getItem().create(UUID.randomUUID().toString(), item);
    }

    private static StorageBacking createStorageBacking(VmwareContext context, String datastoreName) {
        String dsId = getDatastoreId(context, datastoreName);
        if (StringUtils.isBlank(dsId)) {
            return null;
        }

        //Build the storage backing with the datastore Id
        StorageBacking storageBacking = new StorageBacking();
        storageBacking.setType(StorageBacking.Type.DATASTORE);
        storageBacking.setDatastoreId(dsId);
        return storageBacking;
    }

    private static String getDatastoreId(VmwareContext context, String datastoreName) {
        Datastore datastoreService = context.getVimClient().getContentLibrary().getDatastore();
        Set<String> datastores = Collections.singleton(datastoreName);
        List<DatastoreTypes.Summary> datastoreSummaries = null;
        DatastoreTypes.FilterSpec datastoreFilterSpec = null;

        datastoreFilterSpec = new DatastoreTypes.FilterSpec.Builder().setNames(datastores).build();
        datastoreSummaries = datastoreService.list(datastoreFilterSpec);
        if (datastoreSummaries == null || datastoreSummaries.isEmpty()) {
            s_logger.debug("Couldn't find the datastore with name: " + datastoreName);
            return null;
        }

        return datastoreSummaries.get(0).getDatastore();
    }
}
