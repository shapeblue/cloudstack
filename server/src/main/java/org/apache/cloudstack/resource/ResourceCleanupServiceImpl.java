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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.admin.resource.PurgeExpungedResourcesCmd;
import org.apache.cloudstack.framework.async.AsyncCallFuture;
import org.apache.cloudstack.framework.async.AsyncCallbackDispatcher;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.framework.async.AsyncRpcContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.jobs.dao.VmWorkJobDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.ItWorkDao;
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
    public static final String EXPUNGED_RESOURCES_PURGE_JOB_POOL_THREAD_PREFIX = "Expunged-Resource-Purge-Job-Executor";

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

    private ScheduledExecutorService expungedResourcesCleanupExecutor;
    private ExecutorService expungedResourcesPurgeJobExecutor;

    protected void expungeLinkedSnapshotEntities(final List<Long> snapshotIds, final Long batchSize) {
        snapshotDetailsDao.batchExpungeForResources(snapshotIds, batchSize);
        snapshotDataStoreDao.expungeList(snapshotIds);
        // Snapshot policies are using ON DELETE CASCADE
    }

    protected long expungeVolumeSnapshots(final List<Long> volumeIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(volumeIds)) {
            return 0;
        }
        SearchBuilder<SnapshotVO> sb = snapshotDao.createSearchBuilder();
        sb.and("volumeIds", sb.entity().getVolumeId(), SearchCriteria.Op.IN);
        sb.and("removed", sb.entity().getRemoved(), SearchCriteria.Op.NNULL);
        // ToDo: Check if volume entries for active snapshots need to be preserved
        SearchCriteria<SnapshotVO> sc = sb.create();
        sc.setParameters("volumeIds", volumeIds.toArray());
        int removed = 0;
        long totalRemoved = 0;
        Filter filter = new Filter(SnapshotVO.class, "id", true, null, batchSize);
        final long batchSizeFinal = ObjectUtils.defaultIfNull(batchSize, 0L);
        do {
            List<SnapshotVO> volumes = snapshotDao.searchIncludingRemoved(sc, filter, null, false);
            List<Long> snapshotIds = volumes.stream().map(SnapshotVO::getId).collect(Collectors.toList());
            expungeLinkedSnapshotEntities(snapshotIds, batchSize);
            removed = snapshotDao.expungeList(snapshotIds);
            totalRemoved += removed;
        } while (batchSizeFinal > 0 && removed >= batchSizeFinal);
        return totalRemoved;
    }

    protected void expungeLinkedVolumeEntities(final List<Long> volumeIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(volumeIds)) {
            return;
        }
        volumeDetailsDao.batchExpungeForResources(volumeIds, batchSize);
        volumeDataStoreDao.expungeByVolumeList(volumeIds, batchSize);
        expungeVolumeSnapshots(volumeIds, batchSize);
        // Snapshot policies are using ON DELETE CASCADE
    }

    protected long expungeVMVolumes(final List<Long> vmIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(vmIds)) {
            return 0;
        }
        int removed = 0;
        long totalRemoved = 0;
        final long batchSizeFinal = ObjectUtils.defaultIfNull(batchSize, 0L);
        do {
            List<VolumeVO> volumes = volumeDao.searchRemovedByVms(vmIds, batchSize);
            List<Long> volumeIds = volumes.stream().map(VolumeVO::getId).collect(Collectors.toList());
            expungeLinkedVolumeEntities(volumeIds, batchSize);
            removed = volumeDao.expungeList(volumeIds);
            totalRemoved += removed;
        } while (batchSizeFinal > 0 && removed >= batchSizeFinal);
        return totalRemoved;
    }

    protected void expungeLinkedNicEntities(final List<Long> nicIds, final Long batchSize) {
        nicDetailsDao.batchExpungeForResources(nicIds, batchSize);
        nicExtraDhcpOptionDao.expungeByNicList(nicIds, batchSize);
        inlineLoadBalancerNicMapDao.expungeByNicList(nicIds, batchSize);
    }

    protected long expungeVMNics(final List<Long> vmIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(vmIds)) {
            return 0;
        }
        int removed = 0;
        long totalRemoved = 0;
        final long batchSizeFinal = ObjectUtils.defaultIfNull(batchSize, 0L);
        do {
            List<NicVO> nics = nicDao.searchRemovedByVms(vmIds, batchSize);
            List<Long> nicIds = nics.stream().map(NicVO::getId).collect(Collectors.toList());
            expungeLinkedNicEntities(nicIds, batchSize);
            removed = nicDao.expungeList(nicIds);
            totalRemoved += removed;
        } while (batchSizeFinal > 0 && removed >= batchSizeFinal);
        return totalRemoved;
    }

    protected long expungeVMSnapshots(final List<Long> vmIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(vmIds)) {
            return 0;
        }
        // ToDo: Check if VM entries for active snapshots need to be preserved
        int removed = 0;
        long totalRemoved = 0;
        final long batchSizeFinal = ObjectUtils.defaultIfNull(batchSize, 0L);
        do {
            List<VMSnapshotVO> vmSnapshots = vmSnapshotDao.searchRemovedByVms(vmIds, batchSize);
            List<Long> ids = vmSnapshots.stream().map(VMSnapshotVO::getId).collect(Collectors.toList());
            vmSnapshotDetailsDao.batchExpungeForResources(ids, batchSize);
            removed = vmSnapshotDao.expungeList(ids);
            totalRemoved += removed;
        } while (batchSizeFinal > 0 && removed >= batchSizeFinal);
        return totalRemoved;
    }

    protected void expungeVMLinkedEntities(final List<Long> vmIds, final Long batchSize) {
        if (CollectionUtils.isEmpty(vmIds)) {
            return;
        }
        expungeVMVolumes(vmIds, batchSize);
        expungeVMNics(vmIds, batchSize);
        userVmDetailsDao.batchExpungeForResources(vmIds, batchSize);
        // ToDo: Check if we need to remove entries from  specific VM tables
//        userVmDao.expunge();
//        consoleProxyDao.expunge();
//        secondaryStorageVmDao.expunge();
//        domainRouterDao.expunge();
        expungeVMSnapshots(vmIds, batchSize);
        autoScaleVmGroupVmMapDao.expungeByVmList(vmIds, batchSize);
        commandExecLogDao.expungeByVmList(vmIds, batchSize);
//        expungeElasticLbRefs(vmIds, batchSizeInt);
        loadBalancerVMMapDao.expungeByVmList(vmIds, batchSize);
        nicSecondaryIpDao.expungeByVmList(vmIds, batchSize);
        highAvailabilityManager.expungeWorkItemsByVmList(vmIds, batchSize);
        itWorkDao.expungeByVmList(vmIds, batchSize);
        opRouterMonitorServiceDao.expungeByVmList(vmIds, batchSize);
        portForwardingRulesDao.expungeByVmList(vmIds, batchSize);
        ipAddressDao.expungeByVmList(vmIds, batchSize);
        vmWorkJobDao.expungeByVmList(vmIds, batchSize);
    }

    protected long expungeVMEntities(final Long batchSize, final Date startDate, final Date endDate) {
        int removed = 0;
        long totalRemoved = 0;
        final long batchSizeFinal = ObjectUtils.defaultIfNull(batchSize, 0L);
        do {
            List<VMInstanceVO> vms = vmInstanceDao.searcRemovedByRemoveDate(startDate, endDate, batchSize);
            List<Long> vmIds = vms.stream().map(VMInstanceVO::getId).collect(Collectors.toList());
            expungeVMLinkedEntities(vmIds, batchSize);
            removed = vmInstanceDao.expungeList(vmIds);
            totalRemoved += removed;
        } while (batchSizeFinal > 0 && removed >= batchSizeFinal);
        return totalRemoved;
    }

    protected long expungeEntities(final Resource.ResourceType resourceType, final Long batchSize,
            final Date startDate, final Date endDate) {
        long totalExpunged = 0;
        if (resourceType == null || Resource.ResourceType.user_vm.equals(resourceType)) {
            totalExpunged += expungeVMEntities(batchSize, startDate, endDate);
        }
        return totalExpunged;
    }

    protected Void expungedResourcePurgeCallback(
            AsyncCallbackDispatcher<ResourceCleanupServiceImpl, ExpungedResourcePurgeResult> callback,
            ExpungedResourcesContext<ExpungedResourcePurgeResult> context) {
        ExpungedResourcePurgeResult result = callback.getResult();
        context.future.complete(result);
        return null;
    }

    @Override
    public boolean purgeExpungedResources(PurgeExpungedResourcesCmd cmd) {
        final String resourceTypeStr = cmd.getResourceType();
        final Date startDate = cmd.getStartDate();
        final Date endDate = cmd.getEndDate();
        Long batchSize = cmd.getBatchSize();

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
        Integer globalBatchSize = ExpungedResourcesPurgeBatchSize.value();
        if (batchSize == null && globalBatchSize > 0) {
            batchSize = globalBatchSize.longValue();
        }
        AsyncCallFuture<ExpungedResourcePurgeResult> future = new AsyncCallFuture<>();
        ExpungedResourcesContext<ExpungedResourcePurgeResult> context =
                new ExpungedResourcesContext<>(null, future);
        AsyncCallbackDispatcher<ResourceCleanupServiceImpl, ExpungedResourcePurgeResult> caller =
                AsyncCallbackDispatcher.create(this);
        caller.setCallback(caller.getTarget().expungedResourcePurgeCallback(null, null))
                .setContext(context);
        ExpungedResourcePurgeThread job = new ExpungedResourcePurgeThread(resourceType, batchSize, startDate, endDate,
                caller);
        expungedResourcesPurgeJobExecutor.submit(job);
        long expungedCount;
        try {
            ExpungedResourcePurgeResult result = future.get();
            if (result.isFailed()) {
                throw new CloudRuntimeException(String.format("Failed to purge expunged resources due to: %s", result.getResult()));
            }
            expungedCount = result.getExpungedCount();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(String.format("Failed to purge expunged resources due to: %s", e.getMessage()), e);
            throw new CloudRuntimeException("Failed to purge expunged resources");
        }
        if (expungedCount <= 0) {
            logger.debug("No resource expunged during purgeExpungedResources execution");
        }
        return true;
    }

    @Override
    public boolean start() {
        if (Boolean.TRUE.equals(ExpungedResourcePurgeEnabled.value())) {
            expungedResourcesCleanupExecutor.scheduleWithFixedDelay(new ExpungedResourceCleanupWorker(),
                    ExpungedResourcesPurgeDelay.value(), ExpungedResourcesPurgeInterval.value(), TimeUnit.SECONDS);
        }
        expungedResourcesPurgeJobExecutor = Executors.newFixedThreadPool(3,
                new NamedThreadFactory(EXPUNGED_RESOURCES_PURGE_JOB_POOL_THREAD_PREFIX));
        return true;
    }

    @Override
    public boolean stop() {
        expungedResourcesPurgeJobExecutor.shutdown();
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
        return ResourceCleanupService.class.getName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{
                ExpungedResourcePurgeEnabled,
                ExpungedResourcesPurgeInterval,
                ExpungedResourcesPurgeDelay,
                ExpungedResourcesPurgeBatchSize,
                ExpungedResourcesPurgeEndTimeDifference
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
                Integer batchSize = ExpungedResourcesPurgeBatchSize.value();
                Calendar cal = Calendar.getInstance();
                Date endDate = new Date();
                cal.setTime(endDate);
                cal.add(Calendar.DATE, -1 * ExpungedResourcesPurgeEndTimeDifference.value());
                endDate = cal.getTime();
                expungeEntities(null, batchSize.longValue(), null, endDate);
            } catch (Exception e) {
                logger.warn("Caught exception while running expunged resources cleanup task: ", e);
            }
        }
    }

    protected class ExpungedResourcePurgeThread extends ManagedContextRunnable {
        Resource.ResourceType resourceType;
        Long batchSize;
        Date startDate;
        Date endDate;
        AsyncCompletionCallback<ExpungedResourcePurgeResult> callback;
        public ExpungedResourcePurgeThread(final Resource.ResourceType resourceType, final Long batchSize,
               final Date startDate, final Date endDate,
               AsyncCompletionCallback<ExpungedResourcePurgeResult> callback) {
            this.resourceType = resourceType;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.endDate = endDate;
            this.callback = callback;
        }
        @Override
        protected void runInContext() {
            logger.trace(String.format("Executing purge for resource type: %s with batch size: %d start: %s, end: %s",
                    resourceType, batchSize, startDate, endDate));
            GlobalLock gcLock = GlobalLock.getInternLock("Expunged.Resource.Cleanup.Lock");
            try {
                if (gcLock.lock(3)) {
                    try {
                        reallyRun();
                    } finally {
                        gcLock.unlock();
                    }
                }
            } finally {
                gcLock.releaseRef();
            }
        }

        public void reallyRun() {
            try {
                long purged = expungeEntities(resourceType, batchSize, startDate, endDate);
                callback.complete(new ExpungedResourcePurgeResult(resourceType, batchSize, startDate, endDate, purged));
            } catch (CloudRuntimeException e) {
                logger.error("Caught exception while expunging resources: ", e);
                callback.complete(new ExpungedResourcePurgeResult(resourceType, batchSize, startDate, endDate, e.getMessage()));
            }
        }
    }

    public static class ExpungedResourcePurgeResult extends CommandResult {
        Resource.ResourceType resourceType;
        Long batchSize;
        Date startDate;
        Date endDate;
        Long expungedCount;

        public ExpungedResourcePurgeResult(final Resource.ResourceType resourceType, final Long batchSize,
                 final Date startDate, final Date endDate, final long expungedCount) {
            super();
            this.resourceType = resourceType;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.endDate = endDate;
            this.expungedCount = expungedCount;
            this.setSuccess(true);
        }

        public ExpungedResourcePurgeResult(final Resource.ResourceType resourceType, final Long batchSize,
               final Date startDate, final Date endDate, final String error) {
            super();
            this.resourceType = resourceType;
            this.batchSize = batchSize;
            this.startDate = startDate;
            this.endDate = endDate;
            this.setResult(error);
        }

        public Resource.ResourceType getResourceType() {
            return resourceType;
        }

        public Long getBatchSize() {
            return batchSize;
        }

        public Date getStartDate() {
            return startDate;
        }

        public Date getEndDate() {
            return endDate;
        }

        public Long getExpungedCount() {
            return expungedCount;
        }
    }

    public static class ExpungedResourcesContext<T> extends AsyncRpcContext<T> {
        final AsyncCallFuture<ExpungedResourcePurgeResult> future;

        public ExpungedResourcesContext(AsyncCompletionCallback<T> callback,
                AsyncCallFuture<ExpungedResourcePurgeResult> future) {
            super(callback);
            this.future = future;
        }

    }
}
