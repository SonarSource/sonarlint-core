/*
 * SonarLint Core - HTTP
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.CheckForNull;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import nl.altindag.ssl.util.CertificateUtils;
import nl.altindag.ssl.util.KeyStoreUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Inspired by
 * https://github.com/JetBrains/intellij-community/blob/f4fc17b0c44d38d65fb7ad47b968bed55c889609/platform/platform-api/src/com/intellij/util/net/ssl/ConfirmingTrustManager.java#L313
 * 
 */
public final class MutableTrustManager implements X509TrustManager {

  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Path myPath;
  private final String myPassword;
  private final TrustManagerFactory myFactory;
  private final KeyStore myKeyStore;
  private final ReadWriteLock myLock = new ReentrantReadWriteLock();
  private final Lock myReadLock = myLock.readLock();
  private final Lock myWriteLock = myLock.writeLock();
  // reloaded after each modification
  private X509TrustManager myTrustManager;

  MutableTrustManager(Path cacertsFile, String password) {
    myPath = cacertsFile;
    myPassword = password;
    // initialization step
    myWriteLock.lock();
    try {
      myFactory = createFactory();
      myKeyStore = createKeyStore(cacertsFile, password);
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

  @CheckForNull
  private static KeyStore createKeyStore(Path cacertsFile, String password) {
    try {
      if (Files.exists(cacertsFile)) {
        return KeyStoreUtils.loadKeyStore(cacertsFile, password.toCharArray());
      } else {
        try {
          Files.createDirectories(cacertsFile.getParent());
        } catch (IOException e) {
          LOG.error("Cannot create directories: " + cacertsFile.getParent(), e);
          return null;
        }

        return KeyStoreUtils.createKeyStore(password.toCharArray());
      }
    } catch (Exception e) {
      LOG.error("Cannot create key store", e);
      return null;
    }
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
      myKeyStore.setCertificateEntry(CertificateUtils.generateAlias(certificate), certificate);
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
        var trustManagers = myFactory.getTrustManagers();
        var result = findX509TrustManager(trustManagers);
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

  private void flushKeyStore() throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
    try (var stream = new FileOutputStream(myPath.toFile())) {
      myKeyStore.store(stream, myPassword.toCharArray());
    }
  }

  private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (var manager : managers) {
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
