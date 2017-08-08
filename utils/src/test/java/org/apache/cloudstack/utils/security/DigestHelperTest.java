package org.apache.cloudstack.utils.security;

import com.amazonaws.util.StringInputStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

public class DigestHelperTest {

    private final String s = "01234567890123456789012345678901234567890123456789012345678901234567890123456789";

    @Test
    public void testDigestSHA256() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("SHA-256", is);
        Assert.assertEquals("{SHA-256}af1909413b96cbb29927b3a67f3a8879c801a37be383e5f9b31df5fa8d10fa2b", result);
    }

    @Test
    public void testDigestSHA1() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("SHA-1", is);
        Assert.assertEquals("{SHA-1}db9d100073836c9651690af5a74192fe6af1a2b6", result);
    }

    @Test
    public void testDigestMD5() throws Exception {
        InputStream is = new StringInputStream(s);
        String result = DigestHelper.digest("MD5", is);
        Assert.assertEquals("{MD5}faef1f4cb01d560d59016a2d5e91da6", result);
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme