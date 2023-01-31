/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.commons.Language;

public class InitializeParams {
  private final HostInfoDto hostInfo;
  private final String telemetryProductKey;
  private final Path storageRoot;
  private final Set<Path> embeddedPluginPaths;
  private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private final Set<Language> enabledLanguagesInStandaloneMode;
  private final Set<Language> extraEnabledLanguagesInConnectedMode;
  private final boolean enableSecurityHotspots;
  private final List<SonarQubeConnectionConfigurationDto> sonarQubeConnections;
  private final List<SonarCloudConnectionConfigurationDto> sonarCloudConnections;
  private final String sonarlintUserHome;
  private final boolean shouldManageLocalServer;

  /**
   * @param telemetryProductKey SonarLint product key (vscode, idea, eclipse, ...)
   * @param sonarlintUserHome Path to SonarLint user home directory. If null, will default to ~/.sonarlint
   */
  public InitializeParams(HostInfoDto hostInfo, String telemetryProductKey, Path storageRoot, Set<Path> embeddedPluginPaths,
    Map<String, Path> connectedModeEmbeddedPluginPathsByKey, Set<Language> enabledLanguagesInStandaloneMode, Set<Language> extraEnabledLanguagesInConnectedMode,
    boolean enableSecurityHotspots, List<SonarQubeConnectionConfigurationDto> sonarQubeConnections,
    List<SonarCloudConnectionConfigurationDto> sonarCloudConnections, @Nullable String sonarlintUserHome, boolean shouldManageLocalServer) {
    this.hostInfo = hostInfo;
    this.telemetryProductKey = telemetryProductKey;
    this.storageRoot = storageRoot;
    this.embeddedPluginPaths = embeddedPluginPaths;
    this.connectedModeEmbeddedPluginPathsByKey = connectedModeEmbeddedPluginPathsByKey;
    this.enabledLanguagesInStandaloneMode = enabledLanguagesInStandaloneMode;
    this.extraEnabledLanguagesInConnectedMode = extraEnabledLanguagesInConnectedMode;
    this.enableSecurityHotspots = enableSecurityHotspots;
    this.sonarQubeConnections = sonarQubeConnections;
    this.sonarCloudConnections = sonarCloudConnections;
    this.sonarlintUserHome = sonarlintUserHome;
    this.shouldManageLocalServer = shouldManageLocalServer;
  }

  public HostInfoDto getHostInfo() {
    return hostInfo;
  }

  public String getTelemetryProductKey() {
    return telemetryProductKey;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public Set<Path> getEmbeddedPluginPaths() {
    return embeddedPluginPaths;
  }

  public Map<String, Path> getConnectedModeEmbeddedPluginPathsByKey() {
    return connectedModeEmbeddedPluginPathsByKey;
  }

  public Set<Language> getEnabledLanguagesInStandaloneMode() {
    return enabledLanguagesInStandaloneMode;
  }

  public Set<Language> getExtraEnabledLanguagesInConnectedMode() {
    return extraEnabledLanguagesInConnectedMode;
  }

  public boolean isEnableSecurityHotspots() {
    return enableSecurityHotspots;
  }

  public List<SonarQubeConnectionConfigurationDto> getSonarQubeConnections() {
    return sonarQubeConnections;
  }

  public List<SonarCloudConnectionConfigurationDto> getSonarCloudConnections() {
    return sonarCloudConnections;
  }

  @CheckForNull
  public String getSonarlintUserHome() {
    return sonarlintUserHome;
  }

  public boolean shouldManageLocalServer() {
    return shouldManageLocalServer;
  }
}
