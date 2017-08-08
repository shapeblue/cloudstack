package org.apache.cloudstack.utils.security;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestHelper {

    public static String digest(String algorithm, InputStream is) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest;
        digest = MessageDigest.getInstance(algorithm);
        String checksum = null;
        byte[] buffer = new byte[8192];
        int read = 0;
        while ((read = is.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        byte[] md5sum = digest.digest();
        // TODO make sure this is valid for all types of checksums !?!
        BigInteger bigInt = new BigInteger(1, md5sum);
        checksum = '{' + digest.getAlgorithm() + '}' + bigInt.toString(16);
        return checksum;
    }
}
