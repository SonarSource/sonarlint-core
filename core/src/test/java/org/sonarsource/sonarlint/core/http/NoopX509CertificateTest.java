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

import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NoopX509CertificateTest {

  @Test
  void dummyCoverage() throws Exception {
    var underTest = new NoopX509Certificate();
    assertThat(underTest.getBasicConstraints()).isZero();
    assertThat(underTest.getEncoded()).isEmpty();
    assertThat(underTest.getExtensionValue("")).isEmpty();
    assertThat(underTest.getNotAfter()).isNull();
    assertThat(underTest.getKeyUsage()).isEmpty();
    assertThat(underTest.getNotBefore()).isNull();
    assertThat(underTest.getSerialNumber()).isNull();
    assertThat(underTest.getIssuerUniqueID()).isEmpty();
    assertThat(underTest.getTBSCertificate()).isEmpty();
    assertThat(underTest.getSigAlgName()).isNull();
    assertThat(underTest.getSignature()).isEmpty();
    assertThat(underTest.getSigAlgOID()).isNull();
    assertThat(underTest.getSigAlgParams()).isEmpty();
    assertThat(underTest.getVersion()).isZero();
    assertThat(underTest.getExtensionValue("")).isEmpty();
    assertThat(underTest.getExtensionValue("")).isEmpty();
    assertThat(underTest.getIssuerDN()).isNull();
    assertThat(underTest.getSubjectDN()).isNull();
    assertThat(underTest.getSubjectUniqueID()).isEmpty();
    assertThat(underTest.toString()).isNull();
    assertThat(underTest.getPublicKey()).isNull();
    assertThat(underTest.hasUnsupportedCriticalExtension()).isFalse();
    assertThat(underTest.getCriticalExtensionOIDs()).isNull();
    assertThat(underTest.getNonCriticalExtensionOIDs()).isNull();

    underTest.checkValidity();
    underTest.checkValidity(null);
    underTest.verify(null);
    underTest.verify((PublicKey) null, (String) null);
  }

}