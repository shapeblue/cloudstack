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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks config key access in the current thread.
 */
public final class ConfigKeyAccessTracker {
    public static final String UNKNOWN_SCOPE = "Unknown";

    public static final class Access {
        private final String key;
        private final String scope;

        public Access(String key, String scope) {
            this.key = key;
            this.scope = scope;
        }

        public String getKey() {
            return key;
        }

        public String getScope() {
            return scope;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Access)) {
                return false;
            }
            Access that = (Access) obj;
            return key.equals(that.key) && scope.equals(that.scope);
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + scope.hashCode();
        }
    }

    private static final ThreadLocal<LinkedHashSet<Access>> TRACKED_KEYS = new ThreadLocal<>();

    private ConfigKeyAccessTracker() {
    }

    public static void startTracking() {
        TRACKED_KEYS.set(new LinkedHashSet<>());
    }

    public static void restore(List<Access> keys) {
        LinkedHashSet<Access> restored = new LinkedHashSet<>();
        if (keys != null) {
            restored.addAll(keys);
        }
        TRACKED_KEYS.set(restored);
    }

    public static void record(String key, String scope) {
        if (key == null) {
            return;
        }
        Set<Access> keys = TRACKED_KEYS.get();
        if (keys != null) {
            keys.add(new Access(key, scope == null ? UNKNOWN_SCOPE : scope));
        }
    }

    public static List<Access> snapshot() {
        Set<Access> keys = TRACKED_KEYS.get();
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(keys);
    }

    public static List<Access> stopTracking() {
        List<Access> keys = snapshot();
        clear();
        return keys;
    }

    public static void clear() {
        TRACKED_KEYS.remove();
    }
}

