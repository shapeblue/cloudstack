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

package org.apache.cloudstack.resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.admin.resource.PurgeExpungedResourcesCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.jobs.dao.VmWorkJobDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.configuration.Resource;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.network.as.dao.AutoScaleVmGroupVmMapDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.InlineLoadBalancerNicMapDao;
import com.cloud.network.dao.InlineLoadBalancerNicMapVO;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.OpRouterMonitorServiceDao;
import com.cloud.network.rules.dao.PortForwardingRulesDao;
import com.cloud.secstorage.CommandExecLogDao;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.SnapshotDetailsDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.ItWorkDao;
import com.cloud.vm.NicExtraDhcpOptionVO;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.ConsoleProxyDao;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicDetailsDao;
import com.cloud.vm.dao.NicExtraDhcpOptionDao;
import com.cloud.vm.dao.NicSecondaryIpDao;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.UserVmDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.snapshot.VMSnapshotVO;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDetailsDao;

public class ResourceCleanupServiceImpl extends ManagerBase implements ResourceCleanupService, PluggableService,
        Configurable {
    private static final Logger logger = Logger.getLogger(ResourceCleanupServiceImpl.class);

    @Inject
    VMInstanceDao vmInstanceDao;
    @Inject
    VolumeDao volumeDao;
    @Inject
    VolumeDetailsDao volumeDetailsDao;
    @Inject
    VolumeDataStoreDao volumeDataStoreDao;
    @Inject
    SnapshotDao snapshotDao;
    @Inject
    SnapshotDetailsDao snapshotDetailsDao;
    @Inject
    SnapshotDataStoreDao snapshotDataStoreDao;
    @Inject
    NicDao nicDao;
    @Inject
    NicDetailsDao nicDetailsDao;
    @Inject
    NicExtraDhcpOptionDao nicExtraDhcpOptionDao;
    @Inject
    InlineLoadBalancerNicMapDao inlineLoadBalancerNicMapDao;
    @Inject
    UserVmDetailsDao userVmDetailsDao;
    @Inject
    UserVmDao userVmDao;
    @Inject
    ConsoleProxyDao consoleProxyDao;
    @Inject
    SecondaryStorageVmDao secondaryStorageVmDao;
    @Inject
    DomainRouterDao domainRouterDao;
    @Inject
    VMSnapshotDao vmSnapshotDao;
    @Inject
    VMSnapshotDetailsDao vmSnapshotDetailsDao;
    @Inject
    AutoScaleVmGroupVmMapDao autoScaleVmGroupVmMapDao;
    @Inject
    CommandExecLogDao commandExecLogDao;
    @Inject
    LoadBalancerVMMapDao loadBalancerVMMapDao;
    @Inject
    NicSecondaryIpDao nicSecondaryIpDao;
    @Inject
    HighAvailabilityManager highAvailabilityManager;
    @Inject
    ItWorkDao itWorkDao;
    @Inject
    OpRouterMonitorServiceDao opRouterMonitorServiceDao;
    @Inject
    PortForwardingRulesDao portForwardingRulesDao;
    @Inject
    IPAddressDao ipAddressDao;
    @Inject
    VmWorkJobDao vmWorkJobDao;
    @Inject
    ManagementServerHostDao managementServerHostDao;

    ScheduledExecutorService expungedResourcesCleanupExecutor;

    protected long expungeSnapshotStoreRefs(final List<Long> snapshotIds, final Long batchSize) {
        SearchBuilder<SnapshotDataStoreVO> sb = snapshotDataStoreDao.createSearchBuilder();
        sb.and("snapshotIds", sb.entity().getSnapshotId(), SearchCriteria.Op.IN);
        SearchCriteria<SnapshotDataStoreVO> sc = sb.create();
        sc.setParameters("snapshotIds", snapshotIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(SnapshotDataStoreVO.class, "id", true, 0L, batchSize);
        do {
            removed = snapshotDataStoreDao.expunge(sc, filter);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected void expungeLinkedSnapshotEntities(final List<Long> snapshotIds, final Long batchSize) {
        snapshotDetailsDao.batchExpungeForResources(snapshotIds, batchSize);
        expungeSnapshotStoreRefs(snapshotIds, batchSize);
        // Snapshot policies are using ON DELETE CASCADE
    }

    protected long expungeVolumeSnapshots(final List<Long> volumeIds, final Long batchSize) {
        SearchBuilder<SnapshotVO> sb = snapshotDao.createSearchBuilder();
        sb.and("volumeIds", sb.entity().getVolumeId(), SearchCriteria.Op.IN);
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        // ToDo: add a condition that volume should not have any un-removed snapshot
        SearchCriteria<SnapshotVO> sc = sb.create();
        sc.setParameters("volumeIds", volumeIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(SnapshotVO.class, "id", true, 0L, batchSize);
        do {
            List<SnapshotVO> volumes = snapshotDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> snapshotIds = volumes.stream().map(SnapshotVO::getId).collect(Collectors.toList());
            expungeLinkedSnapshotEntities(snapshotIds, batchSize);
            removed = snapshotDao.expungeList(snapshotIds);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected long expungeVolumeStoreRefs(final List<Long> volumeIds, final Long batchSize) {
        SearchBuilder<VolumeDataStoreVO> sb = volumeDataStoreDao.createSearchBuilder();
        sb.and("volumeIds", sb.entity().getVolumeId(), SearchCriteria.Op.IN);
        SearchCriteria<VolumeDataStoreVO> sc = sb.create();
        sc.setParameters("volumeIds", volumeIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(VolumeDataStoreVO.class, "id", true, 0L, batchSize);
        do {
            removed = volumeDataStoreDao.expunge(sc, filter);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected void expungeLinkedVolumeEntities(final List<Long> volumeIds, final Long batchSize) {
        volumeDetailsDao.batchExpungeForResources(volumeIds, batchSize);
        expungeVolumeStoreRefs(volumeIds, batchSize);
        expungeVolumeSnapshots(volumeIds, batchSize);
        // Snapshot policies are using ON DELETE CASCADE
    }

    protected long expungeVMVolumes(final List<Long> vmIds, final Long batchSize) {
        SearchBuilder<VolumeVO> sb = volumeDao.createSearchBuilder();
        sb.and("vmIds", sb.entity().getInstanceId(), SearchCriteria.Op.IN);
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        SearchCriteria<VolumeVO> sc = sb.create();
        sc.setParameters("vmIds", vmIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(VolumeVO.class, "id", true, 0L, batchSize);
        do {
            List<VolumeVO> volumes = volumeDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> volumeIds = volumes.stream().map(VolumeVO::getId).collect(Collectors.toList());
            expungeLinkedVolumeEntities(volumeIds, batchSize);
            removed = volumeDao.expungeList(volumeIds);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected long expungeNicExtraDhcpOptions(final List<Long> nicIds, final Long batchSize) {
        SearchBuilder<NicExtraDhcpOptionVO> sb = nicExtraDhcpOptionDao.createSearchBuilder();
        sb.and("nicIds", sb.entity().getNicId(), SearchCriteria.Op.IN);
        SearchCriteria<NicExtraDhcpOptionVO> sc = sb.create();
        sc.setParameters("nicIds", nicIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(NicExtraDhcpOptionVO.class, "id", true, 0L, batchSize);
        do {
            removed = nicExtraDhcpOptionDao.expunge(sc, filter);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected long expungeNicInlineLoabBalancerRefs(final List<Long> nicIds, final Long batchSize) {
        SearchBuilder<InlineLoadBalancerNicMapVO> sb = inlineLoadBalancerNicMapDao.createSearchBuilder();
        sb.and("nicIds", sb.entity().getNicId(), SearchCriteria.Op.IN);
        SearchCriteria<InlineLoadBalancerNicMapVO> sc = sb.create();
        sc.setParameters("nicIds", nicIds.toArray());
        return inlineLoadBalancerNicMapDao.batchExpunge(sc, batchSize == null ? null : batchSize.intValue());
    }

    protected void expungeLinkedNicEntities(final List<Long> nicIds, final Long batchSize) {
        nicDetailsDao.batchExpungeForResources(nicIds, batchSize);
        expungeNicExtraDhcpOptions(nicIds, batchSize);
        expungeNicInlineLoabBalancerRefs(nicIds, batchSize);
        // Snapshot policies are using ON DELETE CASCADE
    }

    protected long expungeVMNics(final List<Long> vmIds, final Long batchSize) {
        SearchBuilder<NicVO> sb = nicDao.createSearchBuilder();
        sb.and("vmIds", sb.entity().getInstanceId(), SearchCriteria.Op.IN);
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        SearchCriteria<NicVO> sc = sb.create();
        sc.setParameters("vmIds", vmIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(NicVO.class, "id", true, 0L, batchSize);
        do {
            List<NicVO> nics = nicDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> nicIds = nics.stream().map(NicVO::getId).collect(Collectors.toList());
            expungeLinkedNicEntities(nicIds, batchSize);
            removed = nicDao.expungeList(nicIds);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected long expungeVMSnapshots(final List<Long> vmIds, final Long batchSize) {
        SearchBuilder<VMSnapshotVO> sb = vmSnapshotDao.createSearchBuilder();
        sb.and("vmIds", sb.entity().getVmId(), SearchCriteria.Op.IN);
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        SearchCriteria<VMSnapshotVO> sc = sb.create();
        sc.setParameters("vmIds", vmIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(VMSnapshotVO.class, "id", true, 0L, batchSize);
        do {
            List<VMSnapshotVO> vmSnapshots = vmSnapshotDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> ids = vmSnapshots.stream().map(VMSnapshotVO::getId).collect(Collectors.toList());
            vmSnapshotDetailsDao.batchExpungeForResources(ids, batchSize);
            removed = vmSnapshotDao.expungeList(ids);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    protected void expungeVMLinkedEntities(final List<Long> vmIds, final Long batchSize) {
        expungeVMVolumes(vmIds, batchSize);
        expungeVMNics(vmIds, batchSize);
        userVmDetailsDao.batchExpungeForResources(vmIds, batchSize);
//        userVmDao.expunge();
//        consoleProxyDao.expunge();
//        secondaryStorageVmDao.expunge();
//        domainRouterDao.expunge();
        expungeVMSnapshots(vmIds, batchSize);
        Integer batchSizeInt = batchSize == null ? null : batchSize.intValue();
        autoScaleVmGroupVmMapDao.expungeByVmList(vmIds, batchSizeInt);
        commandExecLogDao.expungeByVmList(vmIds, batchSizeInt);
//        expungeElasticLbRefs(vmIds, batchSizeInt);
        loadBalancerVMMapDao.expungeByVmList(vmIds, batchSizeInt);
        nicSecondaryIpDao.expungeByVmList(vmIds, batchSizeInt);
        highAvailabilityManager.expungeWorkItemsByVmList(vmIds, batchSizeInt);
        itWorkDao.expungeByVmList(vmIds, batchSizeInt);
        opRouterMonitorServiceDao.expungeByVmList(vmIds, batchSizeInt);
        portForwardingRulesDao.expungeByVmList(vmIds, batchSizeInt);
        ipAddressDao.expungeByVmList(vmIds, batchSizeInt);
        vmWorkJobDao.expungeByVmList(vmIds, batchSizeInt);
    }

    protected long expungeVMEntities(final Long batchSize, final Date startDate, final Date endDate) {
        SearchBuilder<VMInstanceVO> sb = vmInstanceDao.createSearchBuilder();
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        sb.and("startDate", sb.entity().getRemoved(), SearchCriteria.Op.GTEQ);
        sb.and("endDate", sb.entity().getRemoved(), SearchCriteria.Op.LTEQ);
        SearchCriteria<VMInstanceVO> sc = sb.create();
        if (startDate != null) {
            sc.setParameters("startDate", startDate);
        }
        if (endDate != null) {
            sc.setParameters("endDate", endDate);
        }
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(VMInstanceVO.class, "id", true, 0L, batchSize);
        do {
            List<VMInstanceVO> vms = vmInstanceDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> vmIds = vms.stream().map(VMInstanceVO::getId).collect(Collectors.toList());
            expungeVMLinkedEntities(vmIds, batchSize);
            removed = vmInstanceDao.expungeList(vmIds);
            totalRemoved += removed;
        } while (batchSize > 0 && removed >= batchSize);
        return totalRemoved;
    }

    @Override
    public boolean purgeExpungedResources(PurgeExpungedResourcesCmd cmd) {
        final String resourceTypeStr = cmd.getResourceType();
        final Integer batchSize = cmd.getBatchSize();
        final Date startDate = cmd.getStartDate();
        final Date endDate = cmd.getEndDate();

        Resource.ResourceType resourceType = null;
        if (StringUtils.isNotBlank(resourceTypeStr)) {
            try {
                resourceType = Resource.ResourceType.valueOf(resourceTypeStr);
            } catch (IllegalArgumentException iae) {
                throw new InvalidParameterValueException("Invalid resource type specified");
            }
            if (!CLEANUP_SUPPORTED_RESOURCE_TYPES.contains(resourceType)) {
                throw new InvalidParameterValueException("Invalid resource type specified");
            }
        }
        if (batchSize != null && batchSize <= 0) {
            throw new InvalidParameterValueException(String.format("Invalid %s specified", ApiConstants.BATCH_SIZE));
        }
        if (endDate != null && startDate != null && endDate.before(startDate)) {
            throw new InvalidParameterValueException(String.format("Invalid %s specified", ApiConstants.END_DATE));
        }
        return false;
    }

    @Override
    public boolean start() {
        if (Boolean.TRUE.equals(ExpungedResourcePurgeEnabled.value())) {
            expungedResourcesCleanupExecutor.scheduleWithFixedDelay(new ExpungedResourceCleanupWorker(),
                    ExpungedResourcesPurgeDelay.value(), ExpungedResourcesPurgeInterval.value(), TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(PurgeExpungedResourcesCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return null;
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{
                ExpungedResourcePurgeEnabled,
                ExpungedResourcesPurgeInterval,
                ExpungedResourcesPurgeDelay,
                ExpungedResourcesPurgeDelay
        };
    }

    public class ExpungedResourceCleanupWorker extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock gcLock = GlobalLock.getInternLock("Expunged.Resource.Cleanup.Lock");
            try {
                if (gcLock.lock(3)) {
                    try {
                        runCleanupForLongestRunningManagementServer();
                    } finally {
                        gcLock.unlock();
                    }
                }
            } finally {
                gcLock.releaseRef();
            }
        }

        protected void runCleanupForLongestRunningManagementServer() {
            ManagementServerHostVO msHost = managementServerHostDao.findOneByLongestRuntime();
            if (msHost == null || (msHost.getMsid() != ManagementServerNode.getManagementServerId())) {
                logger.trace("Skipping the expunged resource cleanup task on this management server");
                return;
            }
            reallyRun();
        }

        public void reallyRun() {
            try {
                // do cleanup
            } catch (Exception e) {
                logger.warn("Caught exception while running expunged resources cleanup task: ", e);
            }
        }
    }
}
