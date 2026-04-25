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
package org.apache.cloudstack.service;

import com.cloud.utils.exception.CloudRuntimeException;
import org.junit.Assert;
import org.junit.Test;

public class OvnNbClientTest {
    private final OvnNbClient client = new OvnNbClient();

    @Test
    public void testIsValidConnectionString() {
        Assert.assertTrue(client.isValidConnectionString("tcp:127.0.0.1:6641"));
        Assert.assertTrue(client.isValidConnectionString("ssl:ovn.example.com:6641"));
        Assert.assertTrue(client.isValidConnectionString("unix:/var/run/ovn/ovnnb_db.sock"));
        Assert.assertFalse(client.isValidConnectionString("http://1.2.3.4:6641"));
        Assert.assertFalse(client.isValidConnectionString("tcp:1.2.3.4"));
        Assert.assertFalse(client.isValidConnectionString(""));
        Assert.assertFalse(client.isValidConnectionString(null));
    }

    @Test
    public void testParseTcpEndpoint() {
        OvnNbClient.Endpoint ep = OvnNbClient.parse("tcp:10.0.34.51:6641");
        Assert.assertEquals(OvnNbClient.Scheme.TCP, ep.scheme);
        Assert.assertEquals("10.0.34.51", ep.host);
        Assert.assertEquals(6641, ep.port);
    }

    @Test
    public void testParseSslEndpoint() {
        OvnNbClient.Endpoint ep = OvnNbClient.parse("ssl:nb.example.com:6641");
        Assert.assertEquals(OvnNbClient.Scheme.SSL, ep.scheme);
        Assert.assertEquals("nb.example.com", ep.host);
        Assert.assertEquals(6641, ep.port);
    }

    @Test
    public void testParseUnixEndpoint() {
        OvnNbClient.Endpoint ep = OvnNbClient.parse("unix:/var/run/ovn/ovnnb_db.sock");
        Assert.assertEquals(OvnNbClient.Scheme.UNIX, ep.scheme);
        Assert.assertEquals("/var/run/ovn/ovnnb_db.sock", ep.host);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testParseInvalidThrows() {
        OvnNbClient.parse("not-a-connection-string");
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyConnectionRejectsUnix() {
        client.verifyConnection("unix:/var/run/ovn/ovnnb_db.sock", null, null, null);
    }
}
