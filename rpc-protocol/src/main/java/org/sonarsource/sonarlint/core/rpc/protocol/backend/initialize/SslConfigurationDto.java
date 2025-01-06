/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.nio.file.Path;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class SslConfigurationDto {

  public static SslConfigurationDto defaultConfig() {
    return new SslConfigurationDto(null, null, null, null, null, null);
  }

  private final Path trustStorePath;
  private final String trustStorePassword;
  private final String trustStoreType;
  private final Path keyStorePath;
  private final String keyStorePassword;
  private final String keyStoreType;

  public SslConfigurationDto(@Nullable Path trustStorePath, @Nullable String trustStorePassword, @Nullable String trustStoreType, @Nullable Path keyStorePath,
    @Nullable String keyStorePassword, @Nullable String keyStoreType) {
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
    this.trustStoreType = trustStoreType;
    this.keyStorePath = keyStorePath;
    this.keyStorePassword = keyStorePassword;
    this.keyStoreType = keyStoreType;
  }

  @CheckForNull
  public Path getTrustStorePath() {
    return trustStorePath;
  }

  @CheckForNull
  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  @CheckForNull
  public String getTrustStoreType() {
    return trustStoreType;
  }

  @CheckForNull
  public Path getKeyStorePath() {
    return keyStorePath;
  }

  @CheckForNull
  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  @CheckForNull
  public String getKeyStoreType() {
    return keyStoreType;
  }
}
