//
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
//

package com.cloud.maint;

import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class Version {
    public static void main(String[] args) {
        System.out.println("Result is " + compare(args[0], args[1]));
    }

    /**
     * Compares two version strings and see which one is higher version.
     *
     * @param version1
     * @param version2
     *
     * @return positive if version1 is higher.  negative if version2 is lower; zero if the same.
     */
    public static int compare(String version1, String version2) {
        String[] tokens1 = tokenize(version1);
        String[] tokens2 = tokenize(version2);
        int length = Math.max(tokens1.length, tokens2.length);

        for (int i = 0; i < length; i++) {
            int v1Part = i < tokens1.length ? Integer.parseInt(tokens1[i]) : 0;
            int v2Part = i < tokens2.length ? Integer.parseInt(tokens2[i]) : 0;

            if (v1Part < v2Part) {
                return -1;
            }

            if (v1Part > v2Part) {
                return 1;
            }
        }

        return 0;
    }

    public static String trimToPatch(String version) {
        String[] tokens = tokenize(version);

        if (tokens.length < 3) {
            return "0";
        }

        return tokens[0] + "." + tokens[1] + "." + tokens[2];
    }

    /**
     * Trim full version from router version. Valid versions are:
     *
     * 1) x.y[.z[.a]
     * 2) x.y[.z[.a]]-<brand string>
     * 3) x.y[.z[.a]]-SNAPSHOT
     * 4) x.y[.z[.a]]-<epoch timestamp>
     *
     * @param version to trim
     *
     * @return actual trimmed version
     */
    public static String trimRouterVersion(String version) {
        final String[] tokens = version.split(" ");
        final Pattern versionPattern = Pattern.compile("(\\d+\\.){2}(\\d+\\.)?\\d+(-[a-zA-Z]+)?(-\\d+)?(-SNAPSHOT)?");

        if (tokens.length >= 3 && versionPattern.matcher(tokens[2]).matches()) {
            return tokens[2];
        }

        return "0";
    }

    /**
     * Strip in hyphen or string (branding or SNAPSHOT) from version
     * and then return tokenized format of version (splitted by dot)
     *
     * @param version to tokenize
     *
     * @return stripped and splitted version
     */
    private static String[] tokenize(String version) {
        return StringUtils.substringBefore(version, "-").split("\\.");
    }
}
