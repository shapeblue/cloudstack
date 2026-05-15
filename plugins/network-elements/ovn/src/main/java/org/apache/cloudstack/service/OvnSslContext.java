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
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.aaa.cert.api.ICertificateManager;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OvnSslContext {
    private static final char[] EMPTY_PASSWORD = new char[0];
    private static final String KEYSTORE_TYPE = "JKS";
    private static final String CLIENT_KEY_ALIAS = "ovn-client";
    private static final String CA_ALIAS = "ovn-ca";
    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:RSA |EC )?PRIVATE KEY-----(.+?)-----END (?:RSA |EC )?PRIVATE KEY-----",
            Pattern.DOTALL);

    private final KeyStore keyStore;
    private final KeyStore trustStore;

    OvnSslContext(KeyStore keyStore, KeyStore trustStore) {
        this.keyStore = keyStore;
        this.trustStore = trustStore;
    }

    public static OvnSslContext fromPaths(String caCertPath, String clientCertPath, String clientPrivateKeyPath) {
        if (StringUtils.isAnyBlank(caCertPath, clientCertPath, clientPrivateKeyPath)) {
            throw new CloudRuntimeException("OVN SSL connection requires CA, client certificate and client private key paths");
        }
        try {
            KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
            trustStore.load(null, EMPTY_PASSWORD);
            trustStore.setCertificateEntry(CA_ALIAS, readCertificate(caCertPath));

            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, EMPTY_PASSWORD);
            keyStore.setKeyEntry(CLIENT_KEY_ALIAS, readPrivateKey(clientPrivateKeyPath), EMPTY_PASSWORD,
                    new Certificate[]{readCertificate(clientCertPath)});
            return new OvnSslContext(keyStore, trustStore);
        } catch (Exception e) {
            throw new CloudRuntimeException("Failed to build OVN SSL context: " + e.getMessage(), e);
        }
    }

    public ICertificateManager asCertificateManager() {
        return new ICertificateManager() {
            @Override public KeyStore getODLKeyStore() { return keyStore; }
            @Override public KeyStore getTrustKeyStore() { return trustStore; }
            @Override public String[] getCipherSuites() { return new String[0]; }
            @Override public String[] getTlsProtocols() { return new String[]{"TLSv1.2", "TLSv1.3"}; }
            @Override public String getCertificateTrustStore(String s, String d, boolean p) { return null; }
            @Override public String getODLKeyStoreCertificate(String s, boolean p) { return null; }
            @Override public String genODLKeyStoreCertificateReq(String s, boolean p) { return null; }
            @Override public SSLContext getServerContext() { return buildContext(); }
            @Override public boolean importSslDataKeystores(String a, String b, String c, String d, String e, String[] f, String g) { return false; }
            @Override public void exportSslDataKeystores() { }
        };
    }

    private SSLContext buildContext() {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, EMPTY_PASSWORD);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            throw new CloudRuntimeException("Failed to initialize OVN SSL context: " + e.getMessage(), e);
        }
    }

    private static X509Certificate readCertificate(String path) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (java.security.cert.CertificateException e) {
            throw new IOException("Cannot parse certificate at " + path + ": " + e.getMessage(), e);
        }
    }

    private static PrivateKey readPrivateKey(String path) throws IOException {
        String pem = Files.readString(Path.of(path));
        Matcher m = PEM_PRIVATE_KEY.matcher(pem);
        if (!m.find()) {
            throw new IOException("No PRIVATE KEY block found at " + path);
        }
        byte[] der = Base64.getMimeDecoder().decode(m.group(1));
        try {
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception eRsa) {
            try {
                return java.security.KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (Exception eEc) {
                throw new IOException("Cannot parse private key at " + path + " as RSA or EC: " + eEc.getMessage(), eEc);
            }
        }
    }
}
