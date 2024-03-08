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

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.admin.resource.PurgeExpungedResourcesCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.configuration.Resource;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.GlobalLock;

public class ResourceCleanupServiceImpl extends ManagerBase implements ResourceCleanupService, PluggableService,
        Configurable {
    private static final Logger logger = Logger.getLogger(ResourceCleanupServiceImpl.class);
    @Inject
    ManagementServerHostDao managementServerHostDao;

    ScheduledExecutorService expungedResourcesCleanupExecutor;
    @Override
    public boolean purgeExpungedResources(PurgeExpungedResourcesCmd cmd) {
        final String resourceTypeStr = cmd.getResourceType();
        final Long batchSize = cmd.getBatchSize();
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
