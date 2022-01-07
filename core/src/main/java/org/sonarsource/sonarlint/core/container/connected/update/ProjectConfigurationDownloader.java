/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class ProjectConfigurationDownloader {

  private final ModuleHierarchyDownloader moduleHierarchyDownloader;

  public ProjectConfigurationDownloader(ModuleHierarchyDownloader moduleHierarchyDownloader) {
    this.moduleHierarchyDownloader = moduleHierarchyDownloader;
  }

  public Sonarlint.ProjectConfiguration fetch(ServerApiHelper serverApiHelper, String projectKey, ProgressMonitor progress) {
    var builder = Sonarlint.ProjectConfiguration.newBuilder();
    progress.setProgressAndCheckCancel("Fetching project hierarchy", 0.2f);
    fetchHierarchy(serverApiHelper, projectKey, builder, progress.subProgress(0.2f, 1f, "Fetching project hierarchy"));

    return builder.build();
  }

  private void fetchHierarchy(ServerApiHelper serverApiHelper, String projectKey, Sonarlint.ProjectConfiguration.Builder builder, ProgressMonitor progress) {
    var moduleHierarchy = moduleHierarchyDownloader.fetchModuleHierarchy(serverApiHelper, projectKey, progress);
    builder.putAllModulePathByKey(moduleHierarchy);
  }
}
