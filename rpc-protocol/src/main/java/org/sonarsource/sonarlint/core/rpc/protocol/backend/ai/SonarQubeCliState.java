/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import javax.annotation.Nullable;

public class SonarQubeCliState {
  private final CliInstallationStatus installationStatus;
  private final CliAuthenticationStatus authenticationStatus;
  private final boolean vortexAvailable;
  @Nullable
  private final String executablePath;
  @Nullable
  private final String version;
  @Nullable
  private final String serverUrl;
  @Nullable
  private final String organization;

  public SonarQubeCliState(CliInstallationStatus installationStatus, CliAuthenticationStatus authenticationStatus,
    @Nullable String executablePath, @Nullable String version, @Nullable String serverUrl, @Nullable String organization) {
    this(installationStatus, authenticationStatus, executablePath, version, serverUrl, organization, false);
  }

  public SonarQubeCliState(CliInstallationStatus installationStatus, CliAuthenticationStatus authenticationStatus,
    @Nullable String executablePath, @Nullable String version, @Nullable String serverUrl, @Nullable String organization, boolean vortexAvailable) {
    this.installationStatus = installationStatus;
    this.authenticationStatus = authenticationStatus;
    this.vortexAvailable = vortexAvailable;
    this.executablePath = executablePath;
    this.version = version;
    this.serverUrl = serverUrl;
    this.organization = organization;
  }

  public CliInstallationStatus getInstallationStatus() {
    return installationStatus;
  }

  public CliAuthenticationStatus getAuthenticationStatus() {
    return authenticationStatus;
  }

  /** Whether the CLI reports a Vortex entitlement, including over-consumption. */
  public boolean isVortexAvailable() {
    return vortexAvailable;
  }

  @Nullable
  public String getExecutablePath() {
    return executablePath;
  }

  @Nullable
  public String getVersion() {
    return version;
  }

  @Nullable
  public String getServerUrl() {
    return serverUrl;
  }

  @Nullable
  public String getOrganization() {
    return organization;
  }
}
