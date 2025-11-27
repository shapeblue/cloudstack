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

package org.apache.cloudstack.api;

/**
 * Represents a coffee order in the system.
 * @since 4.23.0.0
 */
public interface Coffee extends InternalIdentity, Identity {

    /**
     * Size options for coffee orders.
     */
    enum Size {
        SMALL,
        MEDIUM,
        LARGE
    }

    /**
     * Available coffee offerings/types.
     */
    enum Offering {
        ESPRESSO,
        CAPPUCCINO,
        MOCHA,
        LATTE
    }

    /**
     * Lifecycle states for a coffee order.
     */
    enum State {
        CREATED,
        BREWING,
        BREWED
    }

    /**
     * Returns the name of the coffee order.
     * @return the coffee name
     */
    String getName();

    /**
     * Returns the type of coffee ordered.
     * @return the coffee offering type
     */
    Offering getOffering();

    /**
     * Returns the size of the coffee order.
     * @return the coffee size
     */
    Size getSize();

    /**
     * Returns the current state of the coffee order.
     * @return the coffee state
     */
    State getState();
}
