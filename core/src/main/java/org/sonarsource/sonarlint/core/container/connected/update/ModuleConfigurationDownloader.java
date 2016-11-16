/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.Map;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

public class ModuleConfigurationDownloader {

  private final SonarLintWsClient wsClient;
  private final ModuleHierarchyDownloader moduleHierarchyDownloader;
  private final ModuleQualityProfilesDownloader moduleQualityProfilesDownloader;
  private final PropertiesDownloader propertiesDownloader;

  public ModuleConfigurationDownloader(SonarLintWsClient wsClient, ModuleHierarchyDownloader moduleHierarchyDownloader,
    ModuleQualityProfilesDownloader moduleQualityProfilesDownloader, PropertiesDownloader propertiesDownloader) {
    this.wsClient = wsClient;
    this.moduleHierarchyDownloader = moduleHierarchyDownloader;
    this.moduleQualityProfilesDownloader = moduleQualityProfilesDownloader;
    this.propertiesDownloader = propertiesDownloader;
  }

  public ModuleConfiguration fetchModuleConfiguration(String moduleKey, GlobalProperties globalProps) {
    ModuleConfiguration.Builder builder = ModuleConfiguration.newBuilder();
    fetchProjectQualityProfiles(moduleKey, builder);
    propertiesDownloader.fetchProjectProperties(moduleKey, globalProps, builder);
    fetchModuleHierarchy(moduleKey, builder);

    return builder.build();
  }

  private void fetchModuleHierarchy(String moduleKey, ModuleConfiguration.Builder builder) {
    Map<String, String> moduleHierarchy = moduleHierarchyDownloader.fetchModuleHierarchy(moduleKey);
    builder.putAllModulePathByKey(moduleHierarchy);
  }

  private void fetchProjectQualityProfiles(String moduleKey, ModuleConfiguration.Builder builder) {
    for (QualityProfile qp : moduleQualityProfilesDownloader.fetchModuleQualityProfiles(moduleKey)) {
      builder.putQprofilePerLanguage(qp.getLanguage(), qp.getKey());
    }
  }

}
