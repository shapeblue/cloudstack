// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.quota.activationrule.presetvariables;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Describes the preset variable and indicates to which Quota usage types it is loaded.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface PresetVariableDefinition {
    /**
     * An array indicating for which Quota usage types the preset variable is loaded.
     * @return an array with the usage types for which the preset variable is loaded.
     */
    int[] supportedTypes() default 0;

    /**
     * A {@link String} describing the preset variable.
     * @return the description of the preset variable.
     */
    String description() default "";
}
