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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Tracks config key access in the current thread.
 *
 * <p>Config keys whose names match any pattern configured in the
 * global setting {@code config.key.usage.exclusion.patterns} are silently skipped.
 * Patterns are comma-separated and support {@code *} as a wildcard
 * (e.g. {@code list*,describe*}).  Full Java regular-expression syntax
 * is also accepted.</p>
 */
public final class ConfigKeyAccessTracker {
    public static final String UNKNOWN_SCOPE = "Unknown";
    static final String CONFIG_KEY_USAGE_EXCLUSION_PATTERNS_KEY = "config.key.usage.exclusion.patterns";

    // -------------------------------------------------------------------
    // Compiled exclusion patterns – refreshed whenever the raw config value
    // changes.  The AtomicReference holds a two-element array:
    //   [0] = the raw String value used to compile the patterns
    //   [1] = List<Pattern> compiled patterns
    // -------------------------------------------------------------------
    private static final AtomicReference<Object[]> s_compiledExclusions = new AtomicReference<>(new Object[]{"", Collections.emptyList()});

    /**
     * Re-entrancy guard: set to {@code true} on a thread while we are reading
     * the exclusion-patterns ConfigKey so that the resulting record() call from
     * ConfigKey.value() does not recurse back into isExcluded().
     */
    private static final ThreadLocal<Boolean> s_inExclusionCheck = new ThreadLocal<>();

    /**
     * Tracks config key access in the current thread.
     */
    public static final class Access {
        private final String key;
        private final String scope;
        private final String resolvedScope;
        private final String value;

        public Access(String key, String scope, String value) {
            this(key, scope, scope, value);
        }

        public Access(String key, String scope, String resolvedScope, String value) {
            this.key = key;
            this.scope = scope;
            this.resolvedScope = resolvedScope;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getScope() {
            return scope;
        }

        public String getResolvedScope() {
            return resolvedScope;
        }

        public String getValue() {
            return value;
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
            if (!key.equals(that.key) || !scope.equals(that.scope) || !resolvedScope.equals(that.resolvedScope)) {
                return false;
            }
            if (value == null) {
                return that.value == null;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            int result = 31 * key.hashCode() + scope.hashCode();
            result = 31 * result + resolvedScope.hashCode();
            return 31 * result + (value != null ? value.hashCode() : 0);
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

    public static void record(String key, String scope, String value) {
        record(key, scope, scope, value);
    }

    public static void record(String key, String scope, String resolvedScope, String value) {
        if (key == null) {
            return;
        }
        if (isExcluded(key)) {
            return;
        }
        Set<Access> keys = TRACKED_KEYS.get();
        if (keys != null) {
            String requestedScope = scope == null ? UNKNOWN_SCOPE : scope;
            keys.add(new Access(key, requestedScope, resolvedScope == null ? requestedScope : resolvedScope, value));
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

    // -------------------------------------------------------------------
    // Exclusion helpers
    // -------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code name} (a config key name or an API command name)
     * matches any currently configured exclusion pattern.
     */
    public static boolean isExcluded(String name) {
        if (Boolean.TRUE.equals(s_inExclusionCheck.get())) {
            // Re-entrant call while resolving exclusion patterns – skip.
            return false;
        }
        s_inExclusionCheck.set(Boolean.TRUE);
        try {
            String rawPatterns = ConfigKey.s_depot != null
                    ? ConfigKey.s_depot.getConfigStringValue(CONFIG_KEY_USAGE_EXCLUSION_PATTERNS_KEY, ConfigKey.Scope.Global, null)
                    : null;
            if (rawPatterns == null || rawPatterns.isEmpty()) {
                return false;
            }
            List<Pattern> patterns = getCompiledPatterns(rawPatterns);
            for (Pattern p : patterns) {
                if (p.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        } finally {
            s_inExclusionCheck.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Pattern> getCompiledPatterns(String rawPatterns) {
        Object[] cached = s_compiledExclusions.get();
        if (rawPatterns.equals(cached[0])) {
            return (List<Pattern>) cached[1];
        }
        // Recompile
        List<Pattern> compiled = new ArrayList<>();
        for (String token : rawPatterns.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Convert glob-style * to regex .*; escape dots only when not
            // followed by * so that users can write plain globs like "list*"
            // as well as full regex like "network\.throttling\..*"
            String regex = trimmed
                    .replace(".", "\\.")       // escape literal dots first
                    .replace("\\.*", ".*")     // un-escape .* that was a wildcard
                    .replace("*", ".*");       // bare * → .*
            compiled.add(Pattern.compile(regex));
        }
        List<Pattern> immutable = Collections.unmodifiableList(compiled);
        s_compiledExclusions.set(new Object[]{rawPatterns, immutable});
        return immutable;
    }
}
