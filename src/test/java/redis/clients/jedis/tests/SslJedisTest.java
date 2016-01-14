package redis.clients.jedis.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.tests.commands.JedisCommandTestBase;

public class SslJedisTest extends JedisCommandTestBase {

  @BeforeClass
  public static void setupTrustStore() {
    setJvmTrustStore("src/test/resources/truststore.jceks", "jceks");
  }

  private static void setJvmTrustStore(String trustStoreFilePath, String trustStoreType) {
    Assert.assertTrue(String.format("Could not find trust store at '%s'.", trustStoreFilePath),
        new File(trustStoreFilePath).exists());
    System.setProperty("javax.net.ssl.trustStore", trustStoreFilePath);
    System.setProperty("javax.net.ssl.trustStoreType", trustStoreType);
  }

  /**
   * Tests opening an SSL/TLS connection to redis.
   */
  @Test
  public void connectWithShardInfo() throws Exception {
    final URI uri = URI.create("rediss://localhost:6380");
    final SSLSocketFactory sslSocketFactory = createTrustStoreSslSocketFactory();
    // These SSL parameters ensure that we use the same hostname verifier used
    // for HTTPS.
    // Note: this options is only available in Java 7.
    final SSLParameters sslParameters = new SSLParameters();
    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");

    JedisShardInfo shardInfo = new JedisShardInfo(uri, sslSocketFactory, sslParameters, null);
    shardInfo.setPassword("foobared");

    Jedis jedis = new Jedis(shardInfo);
    jedis.get("foo");
    jedis.disconnect();
    jedis.close();
  }

  /**
   * Tests opening an SSL/TLS connection to redis using the loopback address of
   * 127.0.0.1. This test should fail because "127.0.0.1" does not match the
   * certificate subject common name and there are no subject alternative names
   * in the certificate.
   */
  @Test
  public void connectWithShardInfoByIpAddress() throws Exception {
    final URI uri = URI.create("rediss://127.0.0.1:6380");
    final SSLSocketFactory sslSocketFactory = createTrustStoreSslSocketFactory();
    // These SSL parameters ensure that we use the same hostname verifier used
    // for HTTPS.
    // Note: this options is only available in Java 7.
    final SSLParameters sslParameters = new SSLParameters();
    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");

    JedisShardInfo shardInfo = new JedisShardInfo(uri, sslSocketFactory, sslParameters, null);
    shardInfo.setPassword("foobared");

    Jedis jedis = new Jedis(shardInfo);
    try {
      jedis.get("foo");
      Assert.fail("The code did not throw the expected JedisConnectionException.");
    } catch (JedisConnectionException e) {
      Assert.assertEquals("Unexpected first inner exception.",
          SSLHandshakeException.class, e.getCause().getClass());
      Assert.assertEquals("Unexpected second inner exception.",
          CertificateException.class, e.getCause().getCause().getClass());
    }

    try {
      jedis.close();
    } catch (Throwable e1) {
      // Expected.
    }
  }

  /**
   * Tests opening an SSL/TLS connection to redis with a custom hostname
   * verifier.
   */
  @Test
  public void connectWithShardInfoAndCustomHostnameVerifier() {
    final URI uri = URI.create("rediss://localhost:6380");
    final SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    final SSLParameters sslParameters = new SSLParameters();

    HostnameVerifier hostnameVerifier = new BasicHostnameVerifier();
    JedisShardInfo shardInfo = new JedisShardInfo(uri, sslSocketFactory, sslParameters, hostnameVerifier);
    shardInfo.setPassword("foobared");

    Jedis jedis = new Jedis(shardInfo);
    jedis.get("foo");
    jedis.disconnect();
    jedis.close();
  }

  /**
   * Tests opening an SSL/TLS connection to redis with a custom hostname
   * verifier. This test should fail because "127.0.0.1" does not match the
   * certificate subject common name and there are no subject alternative names
   * in the certificate.
   */
  @Test
  public void connectWithShardInfoAndCustomHostnameVerifierByIpAddress() {
    final URI uri = URI.create("rediss://127.0.0.1:6380");
    final SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    final SSLParameters sslParameters = new SSLParameters();

    HostnameVerifier hostnameVerifier = new BasicHostnameVerifier();
    JedisShardInfo shardInfo = new JedisShardInfo(uri, sslSocketFactory, sslParameters, hostnameVerifier);
    shardInfo.setPassword("foobared");

    Jedis jedis = new Jedis(shardInfo);
    try {
      jedis.get("foo");
      Assert.fail("The code did not throw the expected JedisConnectionException.");
    } catch (JedisConnectionException e) {
      Assert.assertEquals("The JedisConnectionException does not contain the expected message.",
          "The connection to '127.0.0.1' failed ssl/tls hostname verification.", e.getMessage());
    }

    try {
      jedis.close();
    } catch (Throwable e1) {
      // Expected.
    }
  }

  /**
   * Tests opening an SSL/TLS connection to redis with an empty certificate
   * trust store. This test should fail because there is no trust anchor for the
   * redis server certificate.
   * 
   * @throws Exception
   */
  @Test
  public void connectWithShardInfoAndEmptyTrustStore() throws Exception {

    final URI uri = URI.create("rediss://localhost:6380");
    final SSLSocketFactory sslSocketFactory = createTrustNoOneSslSocketFactory();

    // These SSL parameters ensure that we use the same hostname verifier used
    // for HTTPS.
    // Note: this options is only available in Java 7.
    final SSLParameters sslParameters = new SSLParameters();
    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");

    JedisShardInfo shardInfo = new JedisShardInfo(uri, sslSocketFactory, sslParameters, null);
    shardInfo.setPassword("foobared");

    Jedis jedis = new Jedis(shardInfo);
    try {
      jedis.get("foo");
      Assert.fail("The code did not throw the expected JedisConnectionException.");
    } catch (JedisConnectionException e) {
      Assert.assertEquals("Unexpected first inner exception.",
          SSLException.class, e.getCause().getClass());
      Assert.assertEquals("Unexpected second inner exception.",
          RuntimeException.class, e.getCause().getCause().getClass());
      Assert.assertEquals("Unexpected third inner exception.",
          InvalidAlgorithmParameterException.class, e.getCause().getCause().getCause().getClass());
    }

    try {
      jedis.close();
    } catch (Throwable e1) {
      // Expected.
    }
  }

  /**
   * Creates an SSLSocketFactory that trusts all certificates in
   * truststore.jceks.
   */
  private static SSLSocketFactory createTrustStoreSslSocketFactory() throws Exception {

    KeyStore trustStore = KeyStore.getInstance("jceks");
    InputStream inputStream = null;
    try {
      inputStream = new FileInputStream("src/test/resources/truststore.jceks");
      trustStore.load(inputStream, null);
    } finally {
      inputStream.close();
    }

    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
    trustManagerFactory.init(trustStore);
    TrustManager trustManagers[] = trustManagerFactory.getTrustManagers();

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustManagers, new SecureRandom());
    return sslContext.getSocketFactory();
  }

  /**
   * Creates an SSLSocketFactory with a trust manager that does not trust any
   * certificates.
   */
  private static SSLSocketFactory createTrustNoOneSslSocketFactory() throws Exception {
    TrustManager[] unTrustManagers = new TrustManager[] {
      new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
  
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
          throw new RuntimeException(new InvalidAlgorithmParameterException());
        }
  
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
          throw new RuntimeException(new InvalidAlgorithmParameterException());
        }
      }
    };
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, unTrustManagers, new SecureRandom());
    return sslContext.getSocketFactory();
  }

  /**
   * Very basic hostname verifier implementation for testing. NOT recommended
   * for production.
   * 
   */
  private static class BasicHostnameVerifier implements HostnameVerifier {

    private static final String COMMON_NAME_RDN_PREFIX = "CN=";

    @Override
    public boolean verify(String hostname, SSLSession session) {
      X509Certificate peerCertificate;
      try {
        peerCertificate = (X509Certificate) session.getPeerCertificates()[0];
      } catch (SSLPeerUnverifiedException e) {
        throw new IllegalStateException("The session does not contain a peer X.509 certificate.");
      }
      String peerCertificateCN = getCommonName(peerCertificate);
      return hostname.equals(peerCertificateCN);
    }

    private String getCommonName(X509Certificate peerCertificate) {
      String subjectDN = peerCertificate.getSubjectDN().getName();
      String[] dnComponents = subjectDN.split(",");
      for (String dnComponent : dnComponents) {
        if (dnComponent.startsWith(COMMON_NAME_RDN_PREFIX)) {
          return dnComponent.substring(COMMON_NAME_RDN_PREFIX.length());
        }
      }
      throw new IllegalArgumentException("The certificate has no common name.");
    }
  }
}