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

package com.cloud.kubernetes.cluster;

import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.framework.events.EventBusException;
import org.apache.cloudstack.framework.events.EventSubscriber;
import org.apache.cloudstack.framework.events.EventTopic;
import org.apache.cloudstack.mom.inmemory.InMemoryEventBus;

import com.cloud.event.EventCategory;
import com.cloud.event.EventTypes;
import com.cloud.serializer.GsonHelper;
import com.cloud.utils.component.ManagerBase;

public class TestEventSubsManagerImpl extends ManagerBase implements EventSubscriber {

    @Inject
    InMemoryEventBus eventBus;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        return super.configure(name, params);
    }

    @Override
    public boolean start() {
        subscribeToActionEvent(EventTypes.EVENT_VM_CREATE);
        subscribeToActionEvent(EventTypes.EVENT_VM_START);
        subscribeToActionEvent(EventTypes.EVENT_VM_REBOOT);
        return true;
    }

    private void subscribeToActionEvent(String eventType) {
        try {
            UUID uuid = eventBus.subscribe(new EventTopic(
                    EventCategory.ACTION_EVENT.getName(),
                    eventType,
                    null,
                    null,
                    null), this);
            logger.info("Subscribed to event bus for topic: {}: {} with subscription id: {}",
                    EventCategory.ACTION_EVENT.getName(), eventType, uuid);
        } catch (EventBusException e) {
            logger.error("Unable to subscribe to event bus for topic: {}: {}",
                    EventCategory.ACTION_EVENT.getName(), eventType, e);
        }
    }

    @Override
    public void onEvent(Event event) {
        logger.info("Received event: {} for resource: {} with type: {} :: {}",
                event.getEventType(),
                event.getResourceUUID(),
                event.getResourceType(),
                GsonHelper.getGson().toJson(event));

    }
}
