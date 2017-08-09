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
package org.apache.cloudstack.utils.security;

import java.io.InputStream;

import com.amazonaws.util.StringInputStream;
import org.junit.Assert;
import org.junit.Test;

public class DigestHelperTest {

    private final String s = "01234567890123456789012345678901234567890123456789012345678901234567890123456789\n";

    @Test
    public void testDigestSHA256() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("SHA-256", is);
        Assert.assertEquals("{SHA-256}c6ab15af7842d23d3c06c138b53a7d09c5e351a79c4eb3c8ca8d65e5ce8900ab", result);
    }

    @Test
    public void testDigestSHA1() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("SHA-1", is);
        Assert.assertEquals("{SHA-1}49e4b2f4292b63e88597c127d11bc2cc0f2ca0ff", result);
    }

    @Test
    public void testDigestMD5() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("MD5", is);
        Assert.assertEquals("{MD5}d141a8eeaf6bba779d1d1dc5102a81c5", result);
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme