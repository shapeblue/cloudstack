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

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigKeyUtilTest {

    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String VALUE_1 = "val1";
    private static final String VALUE_2 = "val2";

    @Test
    public void toMapNullReturnsEmpty() {
        assertTrue(ConfigKeyUtil.toMap(null).isEmpty());
    }

    @Test
    public void toMapEmptyStringReturnsEmpty() {
        assertTrue(ConfigKeyUtil.toMap("").isEmpty());
    }

    @Test
    public void toMapSingleEntry() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=%s", KEY, VALUE));
        assertEquals(1, result.size());
        assertEquals(VALUE, result.get(KEY));
    }

    @Test
    public void toMapMultipleEntries() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=%s;%s=%s;key3=val3", KEY_1, VALUE_1, KEY_2, VALUE_2));
        assertEquals(3, result.size());
        assertEquals(VALUE_1, result.get(KEY_1));
        assertEquals(VALUE_2, result.get(KEY_2));
        assertEquals("val3", result.get("key3"));
    }

    @Test
    public void toMapWhitespaceAroundSeparators() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s = %s ; %s = %s", KEY_1, VALUE_1, KEY_2, VALUE_2));
        assertEquals(2, result.size());
        assertEquals(VALUE_1, result.get(KEY_1));
        assertEquals(VALUE_2, result.get(KEY_2));
    }

    @Test
    public void toMapTrailingSemicolon() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=%s;", KEY, VALUE));
        assertEquals(1, result.size());
        assertEquals(VALUE, result.get(KEY));
    }

    @Test
    public void toMapValueContainsEquals() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=val=extra", KEY));
        assertEquals(1, result.size());
        assertEquals("val=extra", result.get(KEY));
    }

    @Test
    public void toMapEmptyValueAllowed() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=", KEY));
        assertEquals(1, result.size());
        assertEquals("", result.get(KEY));
    }

    @Test
    public void toMapEntryWithoutEqualsSkipped() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("noequals;%s=%s", KEY, VALUE));
        assertEquals(1, result.size());
        assertEquals(VALUE, result.get(KEY));
    }

    @Test
    public void toMapEmptyKeySkipped() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("=%s;%s=val", VALUE, KEY));
        assertEquals(1, result.size());
        assertEquals("val", result.get(KEY));
    }

    @Test
    public void toMapDuplicateKeyLastValueWins() {
        Map<String, String> result = ConfigKeyUtil.toMap(String.format("%s=first;%s=second", KEY, KEY));
        assertEquals(1, result.size());
        assertEquals("second", result.get(KEY));
    }
}
