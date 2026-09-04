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
package org.apache.cloudstack.framework.config;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that helps with configuration key manipulation.
 *
 * @author mprokopchuk
 */
public final class ConfigKeyUtil {

    private ConfigKeyUtil() {
    }

    /**
     * Convert configuration value of format {@code key1=value1;key2=value2;...} to {@link Map<String, String>}.
     * <p>
     * Parsing notes: surrounding whitespace is stripped from every key and value, entries with an empty
     * key are skipped, and when the same key appears more than once the last occurrence wins.
     *
     * @param configValue configuration value string
     * @return configuration values map
     */
    public static Map<String, String> toMap(String configValue) {
        if (StringUtils.isEmpty(configValue)) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        int start = 0;
        int len = configValue.length();

        // indexOf(char) is a JVM intrinsic (SIMD scan), avoiding Matcher allocation and regex engine overhead per call.
        // strip() is a no-op when there is no surrounding whitespace, which is the common case for machine-generated values.
        while (start < len) {
            int end = configValue.indexOf(';', start);
            if (end == -1) end = len;

            int eq = configValue.indexOf('=', start);
            if (eq != -1 && eq < end) {
                String key = configValue.substring(start, eq).strip();
                String value = configValue.substring(eq + 1, end).strip();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
            start = end + 1;
        }
        return result;
    }
}
