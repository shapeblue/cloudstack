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
import org.apache.cloudstack.api.Coffee;
import org.apache.cloudstack.api.CoffeeManager;
import org.apache.cloudstack.api.command.CreateCoffeeCmd;
import org.apache.cloudstack.api.command.ListCoffeeCmd;
import org.apache.cloudstack.api.command.RemoveCoffeeCmd;
import org.apache.cloudstack.api.command.UpdateCoffeeCmd;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoffeeManagerImpl extends ManagerBase implements CoffeeManager, PluggableService {

    private static final Logger s_logger = LogManager.getLogger(CoffeeManagerImpl.class);

    private static class HardcodedCoffee implements Coffee {
        private final long id;
        private final String uuid;
        private final String name;
        private final Offering offering;
        private Size size;
        private State state;

        public HardcodedCoffee(long id, String uuid, String name, Offering offering, Size size, State state) {
            this.id = id;
            this.uuid = uuid;
            this.name = name;
            this.offering = offering;
            this.size = size;
            this.state = state;
        }

        @Override
        public long getId() { return id; }

        @Override
        public String getUuid() { return uuid; }

        @Override
        public String getName() { return name; }

        @Override
        public Offering getOffering() { return offering; }

        @Override
        public Size getSize() { return size; }

        public void setSize(Size size) { this.size = size; }

        @Override
        public State getState() { return state; }

        public void setState(State state) { this.state = state; }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        s_logger.info("CoffeeManager is being configured");
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
    public Coffee createCoffee(CreateCoffeeCmd cmd) {
        s_logger.info("Creating coffee: " + cmd.getName());
        Coffee coffee = new HardcodedCoffee(
                3L,
                "fake-uuid-3",
                cmd.getName(),
                Coffee.Offering.valueOf(cmd.getOffering()),
                Coffee.Size.valueOf(cmd.getSize()),
                Coffee.State.Created
        );

        s_logger.debug("Created coffee with ID: " + coffee.getId());
        return coffee;
    }

    @Override
    public List<Coffee> listCoffees(ListCoffeeCmd cmd) {
        s_logger.info("Listing coffees");
        List<Coffee> coffees = new ArrayList<>();

        coffees.add(new HardcodedCoffee(1L, "uuid-1", "Espresso",
                Coffee.Offering.Espresso, Coffee.Size.SMALL, Coffee.State.Brewed));

        coffees.add(new HardcodedCoffee(2L, "uuid-2", "Latte",
                Coffee.Offering.Latte, Coffee.Size.LARGE, Coffee.State.Created));

        coffees.add(new HardcodedCoffee(3L, "uuid-3", "Cappuccino",
                Coffee.Offering.Cappuccino, Coffee.Size.MEDIUM, Coffee.State.Brewing));

        String id = cmd.getId();
        String offering = cmd.getOffering();
        String size = cmd.getSize();

        List<Coffee> filtered = new ArrayList<>();
        for (Coffee coffee : coffees) {
            boolean match = true;

            if (id != null && !String.valueOf(coffee.getId()).equals(id)) {
                match = false;
            }
            if (offering != null && !coffee.getOffering().name().equalsIgnoreCase(offering)) {
                match = false;
            }
            if (size != null && !coffee.getSize().name().equalsIgnoreCase(size)) {
                match = false;
            }

            if (match) {
                filtered.add(coffee);
            }
        }

        s_logger.debug("Returning " + filtered.size() + " coffees");
        return filtered;
    }

    @Override
    public Coffee updateCoffee(UpdateCoffeeCmd cmd) {
        s_logger.info("Updating coffee with ID: " + cmd.getId());

        HardcodedCoffee coffee = new HardcodedCoffee(
                Long.parseLong(cmd.getId()),
                "uuid-" + cmd.getId(),
                "Updated Coffee Order",
                Coffee.Offering.Espresso,
                cmd.getSize() != null ? Coffee.Size.valueOf(cmd.getSize()) : Coffee.Size.MEDIUM,
                Coffee.State.Created
        );

        s_logger.debug("Updated coffee: " + coffee.getName());
        return coffee;
    }

    @Override
    public boolean removeCoffee(RemoveCoffeeCmd cmd) {
        if (cmd.getId() != null) {
            s_logger.info("Removing coffee with ID: " + cmd.getId());
        } else if (cmd.getIds() != null) {
            s_logger.info("Removing " + cmd.getIds().size() + " coffees");
        }

        return true;
    }
}