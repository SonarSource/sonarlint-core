/*
 * SonarLint Core - HTTP
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.http.ssl;

import java.nio.file.Path;

public class CertificateStore {
  public static final String DEFAULT_PASSWORD = "sonarlint";
  public static final String DEFAULT_STORE_TYPE = "PKCS12";
  private final Path path;
  private final String keyStorePassword;
  private final String keyStoreType;

  public CertificateStore(Path path, String keyStorePassword, String keyStoreType) {
    this.path = path;
    this.keyStorePassword = keyStorePassword;
    this.keyStoreType = keyStoreType;
  }

  public Path getPath() {
    return path;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public String getKeyStoreType() {
    return keyStoreType;
  }
}
