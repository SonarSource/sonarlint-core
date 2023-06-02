/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.http;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.CheckForNull;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public final class MutableTrustManager implements X509TrustManager {

  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  private static SonarLintLogger LOG = SonarLintLogger.get();
  private final String myPath;
  private final String myPassword;
  private final TrustManagerFactory myFactory;
  private final KeyStore myKeyStore;
  private final ReadWriteLock myLock = new ReentrantReadWriteLock();
  private final Lock myReadLock = myLock.readLock();
  private final Lock myWriteLock = myLock.writeLock();
  // reloaded after each modification
  private X509TrustManager myTrustManager;

  MutableTrustManager(String path, String password) {
    myPath = path;
    myPassword = password;
    // initialization step
    myWriteLock.lock();
    try {
      myFactory = createFactory();
      myKeyStore = createKeyStore(path, password);
      myTrustManager = initFactoryAndGetManager();
    } finally {
      myWriteLock.unlock();
    }
  }

  private static TrustManagerFactory createFactory() {
    try {
      return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Cannot create trust manager factory", e);
      return null;
    }
  }

  private static KeyStore createKeyStore(String path, String password) {
    KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      Path cacertsFile = Path.of(path);
      if (Files.exists(cacertsFile)) {
        try (InputStream stream = Files.newInputStream(cacertsFile)) {
          keyStore.load(stream, password.toCharArray());
        }
      } else {
        try {
          Files.createDirectories(cacertsFile.getParent());
        } catch (IOException e) {
          LOG.error("Cannot create directories: " + cacertsFile.getParent(), e);
          return null;
        }

        keyStore.load(null, password.toCharArray());
      }
    } catch (Exception e) {
      LOG.error("Cannot create key store", e);
      return null;
    }
    return keyStore;
  }

  /**
   * Add certificate to underlying trust store.
   *
   * @param certificate server's certificate
   * @return whether the operation was successful
   */
  public boolean addCertificate(X509Certificate certificate) {
    myWriteLock.lock();
    try {
      if (isBroken()) {
        return false;
      }
      myKeyStore.setCertificateEntry(createAlias(certificate), certificate);
      flushKeyStore();
      // trust manager should be updated each time its key store was modified
      myTrustManager = initFactoryAndGetManager();
      return true;
    } catch (Exception e) {
      LOG.error("Cannot add certificate", e);
      return false;
    } finally {
      myWriteLock.unlock();
    }
  }

  private static String createAlias(X509Certificate certificate) {
    return certificate.getSubjectX500Principal().getName();
  }

  /**
   * Remove certificate from underlying trust store.
   *
   * @param certificate certificate alias
   * @return whether the operation was successful
   */
  public boolean removeCertificate(X509Certificate certificate) {
    return removeCertificate(createAlias(certificate));
  }

  /**
   * Remove certificate, specified by its alias, from underlying trust store.
   *
   * @param alias certificate's alias
   * @return true if removal operation was successful and false otherwise
   */
  public boolean removeCertificate(String alias) {
    myWriteLock.lock();
    try {
      if (isBroken()) {
        return false;
      }
      // for listeners
      X509Certificate certificate = getCertificate(alias);
      if (certificate == null) {
        LOG.error("No certificate found for alias: " + alias);
        return false;
      }
      myKeyStore.deleteEntry(alias);
      flushKeyStore();
      // trust manager should be updated each time its key store was modified
      myTrustManager = initFactoryAndGetManager();
      return true;
    } catch (Exception e) {
      LOG.error("Cannot remove certificate for alias: " + alias, e);
      return false;
    } finally {
      myWriteLock.unlock();
    }
  }

  /**
   * Get certificate, specified by its alias, from underlying trust store.
   *
   * @param alias certificate's alias
   * @return certificate or null if it's not present
   */
  @CheckForNull
  public X509Certificate getCertificate(String alias) {
    myReadLock.lock();
    try {
      return (X509Certificate) myKeyStore.getCertificate(alias);
    } catch (KeyStoreException e) {
      return null;
    } finally {
      myReadLock.unlock();
    }
  }

  /**
   * Select all available certificates from underlying trust store. Returned list is not supposed to be modified.
   *
   * @return certificates
   */
  public List<X509Certificate> getCertificates() {
    myReadLock.lock();
    try {
      List<X509Certificate> certificates = new ArrayList<>();
      for (String alias : Collections.list(myKeyStore.aliases())) {
        certificates.add(getCertificate(alias));
      }
      return List.copyOf(certificates);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return Collections.emptyList();
    } finally {
      myReadLock.unlock();
    }
  }

  /**
   * Check that underlying trust store contains certificate with specified alias.
   *
   * @param alias - certificate's alias to be checked
   * @return - whether certificate is in storage
   */
  public boolean containsCertificate(String alias) {
    myReadLock.lock();
    try {
      return myKeyStore.containsAlias(alias);
    } catch (KeyStoreException e) {
      LOG.error(e.getMessage(), e);
      return false;
    } finally {
      myReadLock.unlock();
    }
  }

  boolean removeAllCertificates() {
    for (X509Certificate certificate : getCertificates()) {
      if (!removeCertificate(certificate)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
    myReadLock.lock();
    try {
      if (keyStoreIsEmpty() || isBroken()) {
        throw new CertificateException();
      }
      myTrustManager.checkServerTrusted(certificates, s);
    } finally {
      myReadLock.unlock();
    }
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    myReadLock.lock();
    try {
      // trust no one if broken
      if (keyStoreIsEmpty() || isBroken()) {
        return NO_CERTIFICATES;
      }
      return myTrustManager.getAcceptedIssuers();
    } finally {
      myReadLock.unlock();
    }
  }

  // Guarded by caller's lock
  private boolean keyStoreIsEmpty() {
    try {
      return myKeyStore.size() == 0;
    } catch (KeyStoreException e) {
      LOG.error(e.getMessage(), e);
      return true;
    }
  }

  // Guarded by caller's lock
  private X509TrustManager initFactoryAndGetManager() {
    try {
      if (myFactory != null && myKeyStore != null) {
        myFactory.init(myKeyStore);
        final TrustManager[] trustManagers = myFactory.getTrustManagers();
        final X509TrustManager result = findX509TrustManager(trustManagers);
        if (result == null) {
          LOG.error("Cannot find X509 trust manager among " + Arrays.toString(trustManagers));
        }
        return result;
      }
    } catch (KeyStoreException e) {
      LOG.error("Cannot initialize trust store", e);
    }
    return null;
  }

  // Guarded by caller's lock
  private boolean isBroken() {
    return myKeyStore == null || myFactory == null || myTrustManager == null;
  }

  private void flushKeyStore() throws Exception {
    try (FileOutputStream stream = new FileOutputStream(myPath)) {
      myKeyStore.store(stream, myPassword.toCharArray());
    }
  }

  private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager) manager;
      }
    }
    return null;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    throw new UnsupportedOperationException("Should not be called by client");
  }

}
