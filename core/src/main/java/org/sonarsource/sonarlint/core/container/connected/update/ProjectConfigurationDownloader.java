/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ProjectConfigurationDownloader {

  private final ProjectHierarchyDownloader projectHierarchyDownloader;
  private final ProjectQualityProfilesDownloader projectQualityProfilesDownloader;
  private final SettingsDownloader settingsDownloader;

  public ProjectConfigurationDownloader(ProjectHierarchyDownloader projectHierarchyDownloader,
    ProjectQualityProfilesDownloader projectQualityProfilesDownloader, SettingsDownloader settingsDownloader) {
    this.projectHierarchyDownloader = projectHierarchyDownloader;
    this.projectQualityProfilesDownloader = projectQualityProfilesDownloader;
    this.settingsDownloader = settingsDownloader;
  }

  public Sonarlint.ProjectConfiguration fetch(Version serverVersion, String projectKey, GlobalProperties globalProps, ProgressWrapper progress) {
    ProjectConfiguration.Builder builder = Sonarlint.ProjectConfiguration.newBuilder();
    fetchQualityProfiles(projectKey, builder, serverVersion);
    progress.setProgressAndCheckCancel("Fetching project settings", 0.1f);
    settingsDownloader.fetchProjectSettings(serverVersion, projectKey, globalProps, builder);
    progress.setProgressAndCheckCancel("Fetching project hierarchy", 0.2f);
    fetchHierarchy(projectKey, builder, progress.subProgress(0.2f, 1f, "Fetching project hierarchy"));

    return builder.build();
  }

  private void fetchHierarchy(String projectKey, Sonarlint.ProjectConfiguration.Builder builder, ProgressWrapper progress) {
    Map<String, String> moduleHierarchy = projectHierarchyDownloader.fetchModuleHierarchy(projectKey, progress);
    builder.putAllModulePathByKey(moduleHierarchy);
  }

  private void fetchQualityProfiles(String projectKey, ProjectConfiguration.Builder builder, Version serverVersion) {
    for (QualityProfile qp : projectQualityProfilesDownloader.fetchModuleQualityProfiles(projectKey, serverVersion)) {
      builder.putQprofilePerLanguage(qp.getLanguage(), qp.getKey());
    }
  }
}
