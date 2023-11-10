/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import com.google.gson.annotations.JsonAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.PathTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class InitializeParams {
  private final ClientInfoDto clientInfo;
  private final FeatureFlagsDto featureFlags;

  private final Path storageRoot;
  @Nullable
  private final Path workDir;
  private final Set<Path> embeddedPluginPaths;
  private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private final Set<Language> enabledLanguagesInStandaloneMode;
  private final Set<Language> extraEnabledLanguagesInConnectedMode;
  private final List<SonarQubeConnectionConfigurationDto> sonarQubeConnections;
  private final List<SonarCloudConnectionConfigurationDto> sonarCloudConnections;
  private final String sonarlintUserHome;
  private final Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey;
  private final boolean isFocusOnNewCode;

  /**
   * @param workDir                   Path to work directory. If null, will default to [sonarlintUserHome]/work
   * @param sonarlintUserHome         Path to SonarLint user home directory. If null, will default to ~/.sonarlint
   * @param standaloneRuleConfigByKey Local rule configuration for standalone analysis. This configuration will override defaults rule activation and parameters.
   */
  public InitializeParams(ClientInfoDto clientInfo, FeatureFlagsDto featureFlags, Path storageRoot, @Nullable Path workDir, Set<Path> embeddedPluginPaths,
    Map<String, Path> connectedModeEmbeddedPluginPathsByKey, Set<Language> enabledLanguagesInStandaloneMode, Set<Language> extraEnabledLanguagesInConnectedMode,
    List<SonarQubeConnectionConfigurationDto> sonarQubeConnections, List<SonarCloudConnectionConfigurationDto> sonarCloudConnections, @Nullable String sonarlintUserHome,
    Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey, boolean isFocusOnNewCode) {
    this.clientInfo = clientInfo;
    this.featureFlags = featureFlags;
    this.storageRoot = storageRoot;
    this.workDir = workDir;
    this.embeddedPluginPaths = embeddedPluginPaths;
    this.connectedModeEmbeddedPluginPathsByKey = connectedModeEmbeddedPluginPathsByKey;
    this.enabledLanguagesInStandaloneMode = enabledLanguagesInStandaloneMode;
    this.extraEnabledLanguagesInConnectedMode = extraEnabledLanguagesInConnectedMode;
    this.sonarQubeConnections = sonarQubeConnections;
    this.sonarCloudConnections = sonarCloudConnections;
    this.sonarlintUserHome = sonarlintUserHome;
    this.standaloneRuleConfigByKey = standaloneRuleConfigByKey;
    this.isFocusOnNewCode = isFocusOnNewCode;
  }

  public ClientInfoDto getClientInfo() {
    return clientInfo;
  }

  public FeatureFlagsDto getFeatureFlags() {
    return featureFlags;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  @CheckForNull
  public Path getWorkDir() {
    return workDir;
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

  public Map<String, StandaloneRuleConfigDto> getStandaloneRuleConfigByKey() {
    return standaloneRuleConfigByKey;
  }

  public boolean isFocusOnNewCode() {
    return isFocusOnNewCode;
  }
}
