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
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ModuleConfigurationDownloader {

  private final ModuleHierarchyDownloader moduleHierarchyDownloader;
  private final ModuleQualityProfilesDownloader moduleQualityProfilesDownloader;
  private final SettingsDownloader settingsDownloader;

  public ModuleConfigurationDownloader(ModuleHierarchyDownloader moduleHierarchyDownloader,
    ModuleQualityProfilesDownloader moduleQualityProfilesDownloader, SettingsDownloader settingsDownloader) {
    this.moduleHierarchyDownloader = moduleHierarchyDownloader;
    this.moduleQualityProfilesDownloader = moduleQualityProfilesDownloader;
    this.settingsDownloader = settingsDownloader;
  }

  public ModuleConfiguration fetchModuleConfiguration(String serverVersion, String moduleKey, GlobalProperties globalProps, ProgressWrapper progress) {
    ModuleConfiguration.Builder builder = ModuleConfiguration.newBuilder();
    fetchProjectQualityProfiles(moduleKey, builder, serverVersion);
    progress.setProgressAndCheckCancel("Fetching module settings", 0.1f);
    settingsDownloader.fetchProjectSettings(serverVersion, moduleKey, globalProps, builder);
    progress.setProgressAndCheckCancel("Fetching module hierarchy", 0.2f);
    fetchModuleHierarchy(moduleKey, builder, progress.subProgress(0.2f, 1f, "Fetching module hierarchy"));

    return builder.build();
  }

  private void fetchModuleHierarchy(String moduleKey, ModuleConfiguration.Builder builder, ProgressWrapper progress) {
    Map<String, String> moduleHierarchy = moduleHierarchyDownloader.fetchModuleHierarchy(moduleKey, progress);
    builder.putAllModulePathByKey(moduleHierarchy);
  }

  private void fetchProjectQualityProfiles(String moduleKey, ModuleConfiguration.Builder builder, String serverVersion) {
    for (QualityProfile qp : moduleQualityProfilesDownloader.fetchModuleQualityProfiles(moduleKey, serverVersion)) {
      builder.putQprofilePerLanguage(qp.getLanguage(), qp.getKey());
    }
  }

}
