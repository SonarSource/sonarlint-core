/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.UnsupportedServerException;
import org.sonarsource.sonarlint.core.serverapi.system.ServerStatusInfo;
import org.sonarsource.sonarlint.core.serverapi.system.SystemApi;

public class ServerVersionAndStatusChecker {

  private static final String MIN_SQ_VERSION = "9.9";
  private static final String MIN_SQ_VERSION_SUPPORTING_BEARER = "10.4";
  private final SystemApi systemApi;
  private final boolean isSonarCloud;

  public ServerVersionAndStatusChecker(ServerApi serverApi) {
    this.systemApi = serverApi.system();
    this.isSonarCloud = serverApi.isSonarCloud();
  }

  /**
   * Checks SonarQube availability status and version against the minimum version supported by the core
   * or only server availability status for SonarCloud
   *
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException      If server is not ready
   */
  public void checkVersionAndStatus(SonarLintCancelMonitor cancelMonitor) {
    var serverStatus = systemApi.getStatus(cancelMonitor);
    if (isSonarCloud) {
      checkServerUp(serverStatus);
    } else {
      checkServerUpAndSupported(serverStatus);
    }
  }

  public boolean isSupportingBearer(ServerStatusInfo serverStatus) {
    if (isSonarCloud) {
      return true;
    } else {
      var serverVersion = Version.create(serverStatus.getVersion());
      return serverVersion.compareToIgnoreQualifier(Version.create(MIN_SQ_VERSION_SUPPORTING_BEARER)) >= 0;
    }
  }

  private static void checkServerUp(ServerStatusInfo serverStatus) {
    if (!serverStatus.isUp()) {
      throw new IllegalStateException(serverNotReady(serverStatus));
    }
  }

  private static void checkServerUpAndSupported(ServerStatusInfo serverStatus) {
    checkServerUp(serverStatus);
    var serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareToIgnoreQualifier(Version.create(MIN_SQ_VERSION)) < 0) {
      throw new UnsupportedServerException(unsupportedVersion(serverStatus));
    }
  }

  private static String unsupportedVersion(ServerStatusInfo serverStatus) {
    return "Your SonarQube Server instance has version " + serverStatus.getVersion() + ". Version should be greater or equal to " + MIN_SQ_VERSION;
  }

  private static String serverNotReady(ServerStatusInfo serverStatus) {
    return "Server not ready (" + serverStatus.getStatus() + ")";
  }

}
