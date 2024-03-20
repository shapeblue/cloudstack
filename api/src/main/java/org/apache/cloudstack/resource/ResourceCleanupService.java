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

import java.util.List;

import org.apache.cloudstack.api.command.admin.resource.PurgeExpungedResourcesCmd;
import org.apache.cloudstack.framework.config.ConfigKey;

import com.cloud.configuration.Resource;

public interface ResourceCleanupService {
    ConfigKey<Boolean> ExpungedResourcePurgeEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "expunged.resources.purge.enabled", "false",
            "Purge expunged resources",
            false, ConfigKey.Scope.Global);
    ConfigKey<Integer> ExpungedResourcesPurgeInterval = new ConfigKey<>("Advanced", Integer.class,
            "expunged.resources.purge.interval", "0",
            "Interval (in seconds) to purge expunged resources", false);
    ConfigKey<Integer> ExpungedResourcesPurgeDelay = new ConfigKey<>("Advanced", Integer.class,
            "expunged.resources.purge.delay", "0",
            "Initial delay (in seconds) to start purge expunged resources task", false);
    ConfigKey<Integer> ExpungedResourcesPurgeBatchSize = new ConfigKey<>("Advanced", Integer.class,
            "expunged.resources.purge.batch size", "0",
            "Batch size to be used during expunged resources purging", true);

    List<Resource.ResourceType> CLEANUP_SUPPORTED_RESOURCE_TYPES = List.of(Resource.ResourceType.user_vm);
    boolean purgeExpungedResources(PurgeExpungedResourcesCmd cmd);
}
