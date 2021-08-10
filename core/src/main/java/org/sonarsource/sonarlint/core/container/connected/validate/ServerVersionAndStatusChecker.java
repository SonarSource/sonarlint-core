/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.system.SystemApi;

public class ServerVersionAndStatusChecker {

  private static final String MIN_SQ_VERSION = "7.9";
  private final SystemApi systemApi;

  public ServerVersionAndStatusChecker(ServerApiHelper serverApiHelper) {
    this.systemApi = new ServerApi(serverApiHelper).system();
  }

  public ServerInfos checkVersionAndStatus() {
    try {
      return checkVersionAndStatusAsync().get();
    } catch (InterruptedException e) {
      throw new IllegalStateException("Cannot check server version and status", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new IllegalStateException("Cannot check server version and status", cause);
    }
  }

  /**
   * Checks SonarQube version against the minimum version supported by the library
   * @return ServerInfos
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException If server is not ready
   */
  public CompletableFuture<ServerInfos> checkVersionAndStatusAsync() {
    return checkVersionAndStatusAsync(MIN_SQ_VERSION);
  }

  /**
   * Checks SonarQube version against a provided minimum version
   * @return ServerInfos
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException If server is not ready
   */
  public CompletableFuture<ServerInfos> checkVersionAndStatusAsync(String minVersion) {
    return systemApi.getStatus()
      .thenApply(serverStatus -> {
        if (!"UP".equals(serverStatus.getStatus())) {
          throw new IllegalStateException(serverNotReady(serverStatus));
        }
        Version serverVersion = Version.create(serverStatus.getVersion());
        if (serverVersion.compareToIgnoreQualifier(Version.create(minVersion)) < 0) {
          throw new UnsupportedServerException(unsupportedVersion(serverStatus, minVersion));
        }
        return serverStatus;
      });
  }

  private static String unsupportedVersion(ServerInfos serverStatus, String minVersion) {
    return "SonarQube server has version " + serverStatus.getVersion() + ". Version should be greater or equal to " + minVersion;
  }

  private static String serverNotReady(ServerInfos serverStatus) {
    return "Server not ready (" + serverStatus.getStatus() + ")";
  }

  public CompletableFuture<ValidationResult> validateStatusAndVersion() {
    return validateStatusAndVersion(MIN_SQ_VERSION);
  }

  public CompletableFuture<ValidationResult> validateStatusAndVersion(String minVersion) {
    return systemApi.getStatus()
      .thenApply(serverStatus -> {
        if (!"UP".equals(serverStatus.getStatus())) {
          return new DefaultValidationResult(false, serverNotReady(serverStatus));
        }
        Version serverVersion = Version.create(serverStatus.getVersion());
        if (serverVersion.compareToIgnoreQualifier(Version.create(minVersion)) < 0) {
          return new DefaultValidationResult(false, unsupportedVersion(serverStatus, minVersion));
        }
        return new DefaultValidationResult(true, "Compatible and ready");
      });
  }
}
