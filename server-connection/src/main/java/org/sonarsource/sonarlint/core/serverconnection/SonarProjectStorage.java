/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.serverconnection.storage.NewCodeDefinitionStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStoresManager;
import org.sonarsource.sonarlint.core.serverconnection.storage.SmartNotificationsStorage;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class SonarProjectStorage {

  private final ServerIssueStoresManager serverIssueStoresManager;
  private final String sonarProjectKey;
  private final AnalyzerConfigurationStorage analyzerConfigurationStorage;
  private final ProjectBranchesStorage projectBranchesStorage;
  private final ComponentsStorage componentsStorage;
  private final SmartNotificationsStorage smartNotificationsStorage;
  private final NewCodeDefinitionStorage newCodeDefinitionStorage;
  private final Path projectStorageRoot;


  public SonarProjectStorage(Path projectsStorageRoot, ServerIssueStoresManager serverIssueStoresManager, String sonarProjectKey) {
    this.projectStorageRoot = projectsStorageRoot.resolve(encodeForFs(sonarProjectKey));
    this.serverIssueStoresManager = serverIssueStoresManager;
    this.sonarProjectKey = sonarProjectKey;
    this.analyzerConfigurationStorage = new AnalyzerConfigurationStorage(projectStorageRoot);
    this.projectBranchesStorage = new ProjectBranchesStorage(projectStorageRoot);
    this.componentsStorage = new ComponentsStorage(projectStorageRoot);
    this.smartNotificationsStorage = new SmartNotificationsStorage(projectStorageRoot);
    this.newCodeDefinitionStorage = new NewCodeDefinitionStorage(projectStorageRoot);
  }

  public ProjectServerIssueStore findings() {
    return serverIssueStoresManager.get(sonarProjectKey);
  }

  public AnalyzerConfigurationStorage analyzerConfiguration() {
    return analyzerConfigurationStorage;
  }

  public ProjectBranchesStorage branches() {
    return projectBranchesStorage;
  }

  public ComponentsStorage components() {
    return componentsStorage;
  }

  public SmartNotificationsStorage smartNotifications() {
    return smartNotificationsStorage;
  }

  public NewCodeDefinitionStorage newCodeDefinition() {
    return newCodeDefinitionStorage;
  }

  public Path filePath() {
    return projectStorageRoot;
  }
}
