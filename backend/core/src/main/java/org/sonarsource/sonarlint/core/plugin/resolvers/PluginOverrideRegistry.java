/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class PluginOverrideRegistry {

  private static final Map<String, OverrideSpec> OVERRIDES = Map.of(
    "text", new OverrideSpec("textenterprise", Version.create("10.4")),
    "iac", new OverrideSpec("iacenterprise", Version.create("2025.1")),
    "go", new OverrideSpec("goenterprise", Version.create("2025.2"))
  );

  private static final Map<String, OverrideSpec> ENTERPRISE_OVERRIDES = OVERRIDES.entrySet().stream()
    .collect(Collectors.toMap(e -> e.getValue().enterpriseKey(), Map.Entry::getValue));

  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final StorageService storageService;

  public PluginOverrideRegistry(ConnectionConfigurationRepository connectionConfigurationRepository, StorageService storageService) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.storageService = storageService;
  }

  public Optional<String> getEnterpriseOverrideKey(String basePluginKey, String connectionId) {
    return findApplicableSpec(OVERRIDES, basePluginKey, connectionId)
      .map(OverrideSpec::enterpriseKey);
  }

  public boolean isLanguageOverride(String pluginKey, String connectionId) {
    return findApplicableSpec(ENTERPRISE_OVERRIDES, pluginKey, connectionId)
      .isPresent();
  }

  private Optional<OverrideSpec> findApplicableSpec(Map<String, OverrideSpec> specs, String pluginKey, String connectionId) {
    return Optional.ofNullable(specs.get(pluginKey))
      .filter(spec -> isSonarQubeCloudOrVersionAtLeast(spec.minSqVersion(), connectionId));
  }

  private boolean isSonarQubeCloudOrVersionAtLeast(Version minVersion, String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      return false;
    }
    return connection.getKind() == ConnectionKind.SONARCLOUD || storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> serverInfo.version().compareToIgnoreQualifier(minVersion) >= 0)
      .orElse(false);
  }

  private record OverrideSpec(String enterpriseKey, Version minSqVersion) {
  }
}
