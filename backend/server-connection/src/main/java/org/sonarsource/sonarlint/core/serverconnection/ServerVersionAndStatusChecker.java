/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.UnsupportedServerException;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverapi.system.SystemApi;

public class ServerVersionAndStatusChecker {

  private static final String MIN_SQ_VERSION = "7.9";
  private final SystemApi systemApi;

  public ServerVersionAndStatusChecker(ServerApi serverApi) {
    this.systemApi = serverApi.system();
  }

  /**
   * Checks SonarQube version against the minimum version supported by the library
   *
   * @return ServerInfos
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException      If server is not ready
   */
  public ServerInfo checkVersionAndStatus(SonarLintCancelMonitor cancelMonitor) {
    var serverStatus = systemApi.getStatus(cancelMonitor);
    checkServerUpAndSupported(serverStatus);
    return serverStatus;
  }

  public static void checkServerUpAndSupported(ServerInfo serverInfo) {
    if (!serverInfo.isUp()) {
      throw new IllegalStateException(serverNotReady(serverInfo));
    }
    var serverVersion = Version.create(serverInfo.getVersion());
    if (serverVersion.compareToIgnoreQualifier(Version.create(MIN_SQ_VERSION)) < 0) {
      throw new UnsupportedServerException(unsupportedVersion(serverInfo, MIN_SQ_VERSION));
    }
  }

  private static String unsupportedVersion(ServerInfo serverStatus, String minVersion) {
    return "SonarQube server has version " + serverStatus.getVersion() + ". Version should be greater or equal to " + minVersion;
  }

  private static String serverNotReady(ServerInfo serverStatus) {
    return "Server not ready (" + serverStatus.getStatus() + ")";
  }

}
