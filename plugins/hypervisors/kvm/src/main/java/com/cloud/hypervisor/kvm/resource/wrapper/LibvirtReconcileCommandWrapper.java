//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KVMPhysicalDisk;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.DataStoreRole;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.command.ReconcileAnswer;
import org.apache.cloudstack.command.ReconcileCommand;
import org.apache.cloudstack.command.ReconcileCopyAnswer;
import org.apache.cloudstack.command.ReconcileCopyCommand;
import org.apache.cloudstack.command.ReconcileMigrateAnswer;
import org.apache.cloudstack.command.ReconcileMigrateCommand;
import org.apache.cloudstack.command.ReconcileMigrateVolumeAnswer;
import org.apache.cloudstack.command.ReconcileMigrateVolumeCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.cloudstack.storage.volume.VolumeOnStorageTO;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.LibvirtException;

import java.util.Arrays;
import java.util.Map;

@ResourceWrapper(handles =  ReconcileCommand.class)
public final class LibvirtReconcileCommandWrapper extends CommandWrapper<ReconcileCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(final ReconcileCommand command, final LibvirtComputingResource libvirtComputingResource) {

        if (command instanceof ReconcileMigrateCommand) {
            return handle((ReconcileMigrateCommand) command, libvirtComputingResource);
        } else if (command instanceof ReconcileCopyCommand) {
            return handle((ReconcileCopyCommand) command, libvirtComputingResource);
        } else if (command instanceof ReconcileMigrateVolumeCommand) {
            return handle((ReconcileMigrateVolumeCommand) command, libvirtComputingResource);
        }
        return new ReconcileAnswer();
    }

    private ReconcileAnswer handle(final ReconcileMigrateCommand reconcileCommand, final LibvirtComputingResource libvirtComputingResource) {
        String vmName = reconcileCommand.getVmName();
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

        ReconcileMigrateAnswer answer;
        boolean isSourceHost = reconcileCommand.isSourceHost();
        try {
            Connect conn = libvirtUtilitiesHelper.getConnectionByVmName(vmName);
            Domain vm = conn.domainLookupByName(vmName);
            DomainState domainState = vm.getInfo().state;
            logger.debug(String.format("Found VM %s with domain state %s", vmName, domainState));
            VirtualMachine.State state = getState(domainState, isSourceHost);
            answer = new ReconcileMigrateAnswer(isSourceHost ? state : null, isSourceHost ? null : state);
        } catch (LibvirtException e) {
            logger.debug(String.format("Failed to get state of VM %s, assume it is Stopped", vmName));
            VirtualMachine.State state = VirtualMachine.State.Stopped;
            answer = new ReconcileMigrateAnswer(isSourceHost ? state : null, isSourceHost ? null : state);
        }
        return answer;
    }

    static VirtualMachine.State getState(DomainState domainState, boolean isSourceHost) {
        VirtualMachine.State state;
        if (domainState == DomainState.VIR_DOMAIN_RUNNING) {
            state = VirtualMachine.State.Running;
        } else if (Arrays.asList(DomainState.VIR_DOMAIN_SHUTDOWN, DomainState.VIR_DOMAIN_SHUTOFF, DomainState.VIR_DOMAIN_CRASHED).contains(domainState)) {
            state = VirtualMachine.State.Stopped;
        } else if (domainState == DomainState.VIR_DOMAIN_PAUSED) {
            state = isSourceHost ? VirtualMachine.State.Unknown : VirtualMachine.State.Migrating;
        } else {
            state = VirtualMachine.State.Unknown;
        }
        return state;
    }

    private ReconcileAnswer handle(final ReconcileCopyCommand reconcileCommand, final LibvirtComputingResource libvirtComputingResource) {
        DataTO srcData = reconcileCommand.getSrcData();
        DataTO destData = reconcileCommand.getDestData();
        DataStoreTO srcDataStore = srcData.getDataStore();
        DataStoreTO destDataStore = destData.getDataStore();

        // consistent with StorageSubsystemCommandHandlerBase.execute(CopyCommand cmd)
        if (srcData.getObjectType() == DataObjectType.TEMPLATE &&
                (srcData.getDataStore().getRole() == DataStoreRole.Image || srcData.getDataStore().getRole() == DataStoreRole.ImageCache) &&
                destData.getDataStore().getRole() == DataStoreRole.Primary) {
            String reason = "copy template to primary storage";
            return new ReconcileCopyAnswer(true, reason);
        } else if (srcData.getObjectType() == DataObjectType.TEMPLATE && srcDataStore.getRole() == DataStoreRole.Primary &&
                destDataStore.getRole() == DataStoreRole.Primary) {
            String reason = "clone template to a volume";
            return new ReconcileCopyAnswer(true, reason);
        } else if (srcData.getObjectType() == DataObjectType.VOLUME &&
                (srcData.getDataStore().getRole() == DataStoreRole.ImageCache || srcDataStore.getRole() == DataStoreRole.Image)) {
            logger.debug("Reconciling: copy volume from image cache to primary");
            return reconcileCopyVolumeFromImageCacheToPrimary(srcData, destData, reconcileCommand.getOption2(), libvirtComputingResource.getStoragePoolMgr());
        } else if (srcData.getObjectType() == DataObjectType.VOLUME && srcData.getDataStore().getRole() == DataStoreRole.Primary) {
            if (destData.getObjectType() == DataObjectType.VOLUME) {
                if ((srcData instanceof VolumeObjectTO && ((VolumeObjectTO)srcData).isDirectDownload()) ||
                        destData.getDataStore().getRole() == DataStoreRole.Primary) {
                    logger.debug("Reconciling: copy volume from primary to primary");
                    return reconcileCopyVolumeFromPrimaryToPrimary(srcData, destData, libvirtComputingResource.getStoragePoolMgr());
                } else {
                    logger.debug("Reconciling: copy volume from primary to secondary");
                    return reconcileCopyVolumeFromPrimaryToSecondary(srcData, destData, reconcileCommand.getOption(), libvirtComputingResource.getStoragePoolMgr());
                }
            } else if (destData.getObjectType() == DataObjectType.TEMPLATE) {
                String reason = "create volume from template";
                return new ReconcileCopyAnswer(true, reason);
            }
        } else if (srcData.getObjectType() == DataObjectType.SNAPSHOT && destData.getObjectType() == DataObjectType.SNAPSHOT &&
                srcData.getDataStore().getRole() == DataStoreRole.Primary) {
            String reason = "backup snapshot from primary";
            return new ReconcileCopyAnswer(true, reason);
        } else if (srcData.getObjectType() == DataObjectType.SNAPSHOT && destData.getObjectType() == DataObjectType.VOLUME) {
            String reason = "create volume from snapshot";
            return new ReconcileCopyAnswer(true, reason);
        } else if (srcData.getObjectType() == DataObjectType.SNAPSHOT && destData.getObjectType() == DataObjectType.TEMPLATE) {
            String  reason = "create template from snapshot";
            return new ReconcileCopyAnswer(true, reason);
        }

        return new ReconcileCopyAnswer(true, "not implemented yet");
    }

    private ReconcileCopyAnswer reconcileCopyVolumeFromImageCacheToPrimary(DataTO srcData, DataTO destData, Map<String, String> details, KVMStoragePoolManager storagePoolManager) {
        // consistent with KVMStorageProcessor.copyVolumeFromImageCacheToPrimary
        final DataStoreTO srcStore = srcData.getDataStore();
        if (!(srcStore instanceof NfsTO)) {
            return new ReconcileCopyAnswer(true, "can only handle nfs storage as source");
        }
        final DataStoreTO destStore = destData.getDataStore();
        final PrimaryDataStoreTO primaryStore = (PrimaryDataStoreTO)destStore;
        String path = details != null ? details.get(DiskTO.PATH) : null;
        if (path == null) {
            path = details != null ? details.get(DiskTO.IQN) : null;
            if (path == null) {
                return new ReconcileCopyAnswer(true, "path and iqn on destination storage are null");
            }
        }
        try {
            VolumeOnStorageTO volumeOnDestination = getVolumeOnStorage(primaryStore, path, storagePoolManager);
            return new ReconcileCopyAnswer(null, volumeOnDestination);
        } catch (final CloudRuntimeException e) {
            logger.debug("Failed to reconcile CopyVolumeFromImageCacheToPrimary: ", e);
            return new ReconcileCopyAnswer(false, false, e.toString());
        }
    }
    private ReconcileCopyAnswer reconcileCopyVolumeFromPrimaryToPrimary(DataTO srcData, DataTO destData, KVMStoragePoolManager storagePoolManager) {
        // consistent with KVMStorageProcessor.copyVolumeFromPrimaryToPrimary
        final String srcVolumePath = srcData.getPath();
        final String destVolumePath = destData.getPath();
        final DataStoreTO srcStore = srcData.getDataStore();
        final DataStoreTO destStore = destData.getDataStore();
        final PrimaryDataStoreTO srcPrimaryStore = (PrimaryDataStoreTO)srcStore;
        final PrimaryDataStoreTO destPrimaryStore = (PrimaryDataStoreTO)destStore;

        VolumeOnStorageTO volumeOnSource = null;
        VolumeOnStorageTO volumeOnDestination = null;
        try {
            volumeOnSource = getVolumeOnStorage(srcPrimaryStore, srcVolumePath, storagePoolManager);
            if (destPrimaryStore.isManaged() || destVolumePath != null) {
                volumeOnDestination = getVolumeOnStorage(destPrimaryStore, destVolumePath, storagePoolManager);
            }
            return new ReconcileCopyAnswer(volumeOnSource, volumeOnDestination);
        } catch (final CloudRuntimeException e) {
            logger.debug("Failed to reconcile CopyVolumeFromPrimaryToPrimary: ", e);
            return new ReconcileCopyAnswer(false, false, e.toString());
        }
    }

    private ReconcileCopyAnswer reconcileCopyVolumeFromPrimaryToSecondary(DataTO srcData, DataTO destData, Map<String, String> details, KVMStoragePoolManager storagePoolManager) {
        // consistent with KVMStorageProcessor.copyVolumeFromPrimaryToSecondary
        final String srcVolumePath = srcData.getPath();
        final DataStoreTO srcStore = srcData.getDataStore();
        final DataStoreTO destStore = destData.getDataStore();
        final PrimaryDataStoreTO primaryStore = (PrimaryDataStoreTO)srcStore;
        if (!(destStore instanceof NfsTO)) {
            return new ReconcileCopyAnswer(true, "can only handle nfs storage as destination");
        }
        VolumeOnStorageTO volumeOnSource = getVolumeOnStorage(primaryStore, srcVolumePath, storagePoolManager);
        return new ReconcileCopyAnswer(volumeOnSource, null);
    }

    private VolumeOnStorageTO getVolumeOnStorage(PrimaryDataStoreTO primaryStore, String volumePath, KVMStoragePoolManager storagePoolManager) {
        try {
            if (primaryStore.isManaged()) {
                if (!storagePoolManager.connectPhysicalDisk(primaryStore.getPoolType(), primaryStore.getUuid(), volumePath, primaryStore.getDetails())) {
                    logger.warn(String.format("Failed to connect src volume %s, in storage pool %s", volumePath, primaryStore));
                }
            }
            final KVMPhysicalDisk srcVolume = storagePoolManager.getPhysicalDisk(primaryStore.getPoolType(), primaryStore.getUuid(), volumePath);
            if (srcVolume == null) {
                logger.debug("Failed to get physical disk for volume: " + volumePath);
                throw new CloudRuntimeException("Failed to get physical disk for volume at path: " + volumePath);
            }
            return new VolumeOnStorageTO(Hypervisor.HypervisorType.KVM, srcVolume.getName(), srcVolume.getName(), srcVolume.getPath(),
                    srcVolume.getFormat().toString(), srcVolume.getSize(), srcVolume.getVirtualSize());
        } catch (final CloudRuntimeException e) {
            logger.debug(String.format("Failed to get volume %s on storage %s: %s", volumePath, primaryStore, e));
            return new VolumeOnStorageTO();
        } finally {
            if (primaryStore.isManaged()) {
                storagePoolManager.disconnectPhysicalDisk(primaryStore.getPoolType(), primaryStore.getUuid(), volumePath);
            }
        }
    }

    private ReconcileAnswer handle(final ReconcileMigrateVolumeCommand reconcileCommand, final LibvirtComputingResource libvirtComputingResource) {
        // consistent with LibvirtMigrateVolumeCommandWrapper.execute
        DataTO srcData = reconcileCommand.getSrcData();
        DataTO destData = reconcileCommand.getDestData();
        PrimaryDataStoreTO srcDataStore = (PrimaryDataStoreTO) srcData.getDataStore();
        PrimaryDataStoreTO destDataStore = (PrimaryDataStoreTO) destData.getDataStore();

        VolumeOnStorageTO volumeOnSource = getVolumeOnStorage(srcDataStore, srcData.getPath(), libvirtComputingResource.getStoragePoolMgr());
        VolumeOnStorageTO volumeOnDestination = getVolumeOnStorage(destDataStore, destData.getPath(), libvirtComputingResource.getStoragePoolMgr());

        return new ReconcileMigrateVolumeAnswer(volumeOnSource, volumeOnDestination);
    }
}
