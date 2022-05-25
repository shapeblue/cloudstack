package com.cloud.vm;

import junit.framework.TestCase;

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
public class VirtualMachineNameTest extends TestCase {

    public void testTestIsValidVmName() {
        String name = "i-2-10-VM";
        assertTrue(VirtualMachineName.isValidVmName(name));
    }

    public void testTestIsInvalidVmName() {
        String name = "test-VM-name";
        assertFalse(VirtualMachineName.isValidVmName(name));
    }

    public void testGetVmName() {
        String name = "i-2-10-VM";
        assertEquals(VirtualMachineName.getVmName(10, 2, "VM"), name);
    }

    public void testGetVmId() {
        String name = "i-2-10-VM";
        assertEquals(VirtualMachineName.getVmId(name), 10);
    }

    public void testGetRouterId() {
        String name = "r-2-VM";
        assertEquals(VirtualMachineName.getRouterId(name), 2);

    }

    public void testGetConsoleProxyId() {
        String name = "v-2-VM";
        assertEquals(VirtualMachineName.getRouterId(name), 2);
    }

    public void testGetSystemVmId() {
        String name = "s-2-VM";
        assertEquals(VirtualMachineName.getRouterId(name), 2);
    }

    public void testGetRouterName() {
        String name = "r-2-VM";
        assertEquals(VirtualMachineName.getRouterName(2, "VM"), name);
    }

    public void testGetConsoleProxyName() {
        String name = "v-2-VM";
        assertEquals(VirtualMachineName.getConsoleProxyName(2, "VM"), name);
    }

    public void testGetSystemVmName() {
        String name = "s-2-VM";
        assertEquals(VirtualMachineName.getSystemVmName(2, "VM", "s"), name);
    }

    public void testIsValidRouterName() {
        String routerName = "r-2-VM";
        assertTrue(VirtualMachineName.isValidRouterName(routerName));
    }

    public void testIsInValidRouterName() {
        String routerName = "v-23-VM";
        assertFalse(VirtualMachineName.isValidRouterName(routerName));
    }

    public void testIsValidConsoleProxyName() {
        String cpvmName = "v-23-VM";
        assertTrue(VirtualMachineName.isValidConsoleProxyName(cpvmName));
    }

    public void testIsInvalidConsoleProxyName() {
        String cpvmName = "r-23-VM";
        assertFalse(VirtualMachineName.isValidConsoleProxyName(cpvmName));
    }

    public void testIsValidSecStorageVmName() {
        String ssvmName = "s-23-VM";
        assertTrue(VirtualMachineName.isValidSecStorageVmName(ssvmName, "VM"));
    }

    public void testIsValidSystemVmName() {
        String sysVMName = "b-3-VM";
        assertTrue(VirtualMachineName.isValidSystemVmName(sysVMName, "VM", "b"));
    }
}