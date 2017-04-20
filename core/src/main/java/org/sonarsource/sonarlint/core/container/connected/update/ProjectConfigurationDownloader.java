/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.Map;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

public class ProjectConfigurationDownloader {

  private final ModuleHierarchyDownloader moduleHierarchyDownloader;
  private final ProjectQualityProfilesDownloader projectQualityProfilesDownloader;
  private final SettingsDownloader settingsDownloader;

  public ProjectConfigurationDownloader(ModuleHierarchyDownloader moduleHierarchyDownloader,
    ProjectQualityProfilesDownloader moduleQualityProfilesDownloader, SettingsDownloader settingsDownloader) {
    this.moduleHierarchyDownloader = moduleHierarchyDownloader;
    this.projectQualityProfilesDownloader = moduleQualityProfilesDownloader;
    this.settingsDownloader = settingsDownloader;
  }

  public ModuleConfiguration fetchProjectConfiguration(String serverVersion, ProjectId projectId, GlobalProperties globalProps) {
    ModuleConfiguration.Builder builder = ModuleConfiguration.newBuilder();
    fetchProjectQualityProfiles(projectId, builder);
    settingsDownloader.fetchProjectSettings(serverVersion, projectId, globalProps, builder);
    fetchModuleHierarchy(projectId, builder);

    return builder.build();
  }

  private void fetchModuleHierarchy(ProjectId projectId, ModuleConfiguration.Builder builder) {
    Map<String, String> moduleHierarchy = moduleHierarchyDownloader.fetchModuleHierarchy(projectId);
    builder.putAllModulePathByKey(moduleHierarchy);
  }

  private void fetchProjectQualityProfiles(ProjectId projectId, ModuleConfiguration.Builder builder) {
    for (QualityProfile qp : projectQualityProfilesDownloader.fetchProjectQualityProfiles(projectId)) {
      builder.putQprofilePerLanguage(qp.getLanguage(), qp.getKey());
    }
  }

}
