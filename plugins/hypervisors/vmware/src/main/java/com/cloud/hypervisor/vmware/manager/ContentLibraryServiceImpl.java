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
package com.cloud.hypervisor.vmware.manager;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.hypervisor.vmware.mo.DatastoreMO;
import com.cloud.hypervisor.vmware.mo.VirtualMachineMO;
import com.cloud.hypervisor.vmware.mo.VmwareHypervisorHost;
import com.cloud.hypervisor.vmware.util.VmwareContext;
import com.cloud.hypervisor.vmware.util.ContentLibraryHelper;
import com.vmware.vim25.ManagedObjectReference;

@Component
public class ContentLibraryServiceImpl implements ContentLibraryService {
    private static final Logger s_logger = Logger.getLogger(ContentLibraryServiceImpl.class);

    public ContentLibraryServiceImpl() {
    }

    public boolean createContentLibrary(VmwareContext context, String primaryDatastoreName) throws Exception {
        return ContentLibraryHelper.createContentLibrary(context, primaryDatastoreName, primaryDatastoreName);
    }

    public boolean deleteContentLibrary(VmwareContext context, String primaryDatastoreName) throws Exception {
        return ContentLibraryHelper.deleteContentLibrary(context, primaryDatastoreName, primaryDatastoreName);
    }

    public boolean importOvf(VmwareContext context, String sourceOvfTemplateUri, String sourceOvfTemplateName, String targetDatastoreName, String targetOvfTemplateName) throws Exception {
        return ContentLibraryHelper.importOvfFromDatastore(context, sourceOvfTemplateUri, sourceOvfTemplateName, targetDatastoreName, targetOvfTemplateName);
    }

    public VirtualMachineMO deployOvf(VmwareContext context, String sourceovfTemplateName, String vmNameToDeploy, VmwareHypervisorHost targetHypervisorHost, DatastoreMO primaryDataStoreMO) throws Exception {
        String dsName = primaryDataStoreMO.getName();
        ManagedObjectReference morDatastore = primaryDataStoreMO.getMor();
        ManagedObjectReference morHostResourcePool = targetHypervisorHost.getHyperHostOwnerResourcePool();
        boolean deployResult = ContentLibraryHelper.deployOvf(context, dsName, sourceovfTemplateName, vmNameToDeploy, morHostResourcePool, morDatastore);
        if (!deployResult) {
            s_logger.debug("Deployment failed for the VM: " + vmNameToDeploy);
            return null;
        }

        VirtualMachineMO vmMo = targetHypervisorHost.findVmOnHyperHost(vmNameToDeploy);
        return vmMo;
    }
}
