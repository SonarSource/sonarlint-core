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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

class NoopX509Certificate extends X509Certificate {
  @Override
  public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {

  }

  @Override
  public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {

  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public BigInteger getSerialNumber() {
    return null;
  }

  @Override
  public Principal getIssuerDN() {
    return null;
  }

  @Override
  public Principal getSubjectDN() {
    return null;
  }

  @Override
  public Date getNotBefore() {
    return null;
  }

  @Override
  public Date getNotAfter() {
    return null;
  }

  @Override
  public byte[] getTBSCertificate() throws CertificateEncodingException {
    return new byte[0];
  }

  @Override
  public byte[] getSignature() {
    return new byte[0];
  }

  @Override
  public String getSigAlgName() {
    return null;
  }

  @Override
  public String getSigAlgOID() {
    return null;
  }

  @Override
  public byte[] getSigAlgParams() {
    return new byte[0];
  }

  @Override
  public boolean[] getIssuerUniqueID() {
    return new boolean[0];
  }

  @Override
  public boolean[] getSubjectUniqueID() {
    return new boolean[0];
  }

  @Override
  public boolean[] getKeyUsage() {
    return new boolean[0];
  }

  @Override
  public int getBasicConstraints() {
    return 0;
  }

  @Override
  public byte[] getEncoded() throws CertificateEncodingException {
    return new byte[0];
  }

  @Override
  public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

  }

  @Override
  public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

  }

  @Override
  public String toString() {
    return null;
  }

  @Override
  public PublicKey getPublicKey() {
    return null;
  }

  @Override
  public boolean hasUnsupportedCriticalExtension() {
    return false;
  }

  @Override
  public Set<String> getCriticalExtensionOIDs() {
    return null;
  }

  @Override
  public Set<String> getNonCriticalExtensionOIDs() {
    return null;
  }

  @Override
  public byte[] getExtensionValue(String oid) {
    return new byte[0];
  }
}
