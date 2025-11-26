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

package org.apache.cloudstack.feature;

import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.poll.BackgroundPollManager;
import org.apache.cloudstack.poll.BackgroundPollTask;
import org.apache.cloudstack.api.Coffee;
import org.apache.cloudstack.api.CoffeeManager;
import org.apache.cloudstack.api.command.CreateCoffeeCmd;
import org.apache.cloudstack.api.command.ListCoffeeCmd;
import org.apache.cloudstack.api.command.RemoveCoffeeCmd;
import org.apache.cloudstack.api.command.UpdateCoffeeCmd;
import org.apache.cloudstack.feature.dao.CoffeeDao;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoffeeManagerImpl extends ManagerBase implements CoffeeManager, Configurable, PluggableService {

    private static final Logger s_logger = LogManager.getLogger(CoffeeManagerImpl.class);

    @Inject
    private CoffeeDao coffeeDao;

    @Inject
    private BackgroundPollManager backgroundPollManager;

    private static final ConfigKey<Long> CoffeeTTLInterval = new ConfigKey<Long>(
            "Advanced",
            Long.class,
            "coffee.ttl.interval",
            "600",
            "The max time in seconds after which coffee becomes stale.",
            true,
            ConfigKey.Scope.Zone
    );

    private static final ConfigKey<Long> CoffeeGCInterval = new ConfigKey<Long>(
            "Advanced",
            Long.class,
            "coffee.gc.interval",
            "300",
            "The interval in seconds at which the coffee garbage collection task runs.",
            true,
            ConfigKey.Scope.Zone
    );

    private static final class CoffeeGCTask extends ManagedContextRunnable implements BackgroundPollTask {
        private final CoffeeManager coffeeManager;

        private CoffeeGCTask(CoffeeManager coffeeManager) {
            this.coffeeManager = coffeeManager;
        }

        @Override
        protected void runInContext() {
            try {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Coffee GC task is running...");
                }

                final Long ttl = CoffeeTTLInterval.value();

                s_logger.info("Coffee GC task executed. TTL: " + ttl + " seconds");

            } catch (final Throwable t) {
                s_logger.error("Error trying to run Coffee GC task", t);
            }
        }

        @Override
        public Long getDelay() {
            return CoffeeGCInterval.value() * 1000L;
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        s_logger.info("CoffeeManager is being configured");
        backgroundPollManager.submitTask(new CoffeeGCTask(this));
        s_logger.info("Coffee GC background task has been scheduled");
        return true;
    }

    @Override
    public boolean start() {
        s_logger.info("CoffeeManager is starting");
        return true;
    }

    @Override
    public boolean stop() {
        s_logger.info("CoffeeManager is stopping");
        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(CreateCoffeeCmd.class);
        cmdList.add(ListCoffeeCmd.class);
        cmdList.add(UpdateCoffeeCmd.class);
        cmdList.add(RemoveCoffeeCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return CoffeeManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[]{
                CoffeeTTLInterval,
                CoffeeGCInterval
        };
    }

    @Override
    public Coffee createCoffee(CreateCoffeeCmd cmd) {
        s_logger.info("Creating coffee: " + cmd.getName());

        Coffee.Offering offering = Coffee.Offering.valueOf(cmd.getOffering());
        Coffee.Size size = Coffee.Size.valueOf(cmd.getSize());

        CoffeeVO coffee = new CoffeeVO(cmd.getName(), offering, size, 1L);
        coffee = coffeeDao.persist(coffee);

        s_logger.debug("Created coffee with ID: " + coffee.getId() + ", UUID: " + coffee.getUuid());
        return coffee;
    }

    @Override
    public List<Coffee> listCoffees(ListCoffeeCmd cmd) {
        s_logger.info("Listing coffees");

        List<CoffeeVO> coffees = coffeeDao.listAll();

        Long id = cmd.getId();
        String offering = cmd.getOffering();
        String size = cmd.getSize();

        List<Coffee> filteredCoffeeList = new ArrayList<>();

        for (CoffeeVO coffee : coffees) {
            boolean isCoffeeFound = true;

            if (id != null && coffee.getId() != id) {
                isCoffeeFound = false;
            }

            if (offering != null && !coffee.getOffering().name().equalsIgnoreCase(offering)) {
                isCoffeeFound = false;
            }

            if (size != null && !coffee.getSize().name().equalsIgnoreCase(size)) {
                isCoffeeFound = false;
            }

            if (isCoffeeFound) {
                filteredCoffeeList.add(coffee);
            }
        }

        s_logger.debug("Returning " + filteredCoffeeList.size() + " coffees");
        return filteredCoffeeList;
    }

    @Override
    public Coffee updateCoffee(UpdateCoffeeCmd cmd) {
        s_logger.info("Updating coffee with ID: " + cmd.getId());

        long id = cmd.getId();
        CoffeeVO coffee = coffeeDao.findById(id);

        if (coffee == null) {
            throw new CloudRuntimeException("Coffee with ID " + id + " not found");
        }

        if (cmd.getSize() != null) {
            coffee.setSize(Coffee.Size.valueOf(cmd.getSize()));
        }

        coffeeDao.update(id, coffee);

        s_logger.debug("Updated coffee: " + coffee.getName());
        return coffee;
    }

    @Override
    public boolean removeCoffee(RemoveCoffeeCmd cmd) {
        if (cmd.getId() != null) {
            s_logger.info("Removing coffee with ID: " + cmd.getId());
            long id = cmd.getId();

            boolean result = coffeeDao.remove(id);

            if (!result) {
                throw new CloudRuntimeException("Failed to remove coffee with ID " + id);
            }

            return true;
        } else if (cmd.getIds() != null) {
            s_logger.info("Removing " + cmd.getIds().size() + " coffees");
            for (String id : cmd.getIds()) {
                coffeeDao.remove(Long.parseLong(id));
            }
            return true;
        }

        return false;
    }
}