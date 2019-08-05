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

package org.apache.cloudstack.fizzbuzz;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.cloudstack.api.command.user.fizzbuzz.FizzBuzzCmd;

import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;

public class BuggyFizzBuzzManagerImpl extends ManagerBase implements FizzBuzzService, PluggableService {

    /**
     * Works the usual fizzbuzz problem:
     *      (1) for numbers divisible by 3 output "fizz"
     *      (2) for numbers divisible by 5 output "buzz"
     *      (3) for numbers divisible by both 3 and 5, output "fizzbuzz"
     * Add intermittent failure:
     *      (1) for numbers between 40-60 (inclusive) it will add random integers to the input
     * Add known failures:
     *      (1) for numbers divisible by 30 output "buzzinga"
     *      (2) for number divisible by 45 output "fuzzylumpkins" as exception
     *      (3) for negative inputs or invalid/null inputs, output "lulz"
     * @param number
     * @return
     */
    @Override
    public String fizzBuzz(Long number) {
        if (number == null || number < 0) {
            return "lulz";
        }

        if (number >= 40 && number <= 60) {
            number += new Random().nextInt(10);
        }

        if (number % 30 == 0) {
            return "buzzinga";
        }

        if (number % 45 == 0) {
            throw new CloudRuntimeException("fuzzylumpkins");
        }

        StringBuilder builder = new StringBuilder();
        if (number % 3 == 0) {
            builder.append("fizz");
        }
        if (number % 5 == 0) {
            builder.append("buzz");
        }
        return builder.toString();
    }

    @Override
    public List<Class<?>> getCommands() {
        return Collections.singletonList(FizzBuzzCmd.class);
    }
}
