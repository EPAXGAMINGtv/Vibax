package de.epax.storageapi.security;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.time.Duration;

public class TLSConfig {
    private SSLContext sslContext;
    private final char[] password;

    public TLSConfig(char[] password) {
        this.password = password.clone();
    }

    public TLSConfig(String password) {
        this.password = password.toCharArray();
    }

    public void loadKeyStore(String keystorePath, String keystorePassword) throws Exception {
        char[] ksPass = keystorePassword != null ? keystorePassword.toCharArray() : password;
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, ksPass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        this.sslContext = SSLContext.getInstance("TLS");
        this.sslContext.init(kmf.getKeyManagers(), null, null);
    }

    public void loadTrustStore(String truststorePath, String truststorePassword) throws Exception {
        char[] tsPass = truststorePassword != null ? truststorePassword.toCharArray() : password;
        KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            ts.load(fis, tsPass);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        this.sslContext = SSLContext.getInstance("TLS");
        this.sslContext.init(null, tmf.getTrustManagers(), null);
    }

    public HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2);

        if (sslContext != null) {
            builder.sslContext(sslContext);
        }

        return builder.build();
    }

    public static boolean isValidCertificate(String certPath) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            File f = new File(certPath);
            try (FileInputStream fis = new FileInputStream(f)) {
                cf.generateCertificate(fis);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getDefaultTLSVersion() {
        try {
            return SSLContext.getDefault().getProtocol();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
