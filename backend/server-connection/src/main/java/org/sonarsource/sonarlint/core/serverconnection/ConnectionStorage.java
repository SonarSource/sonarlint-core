/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.core.serverconnection.storage.AiCodeFixStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.OrganizationStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.SQCProjectBindingStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.SQCSearchedProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStoresManager;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class ConnectionStorage {
  private final ServerIssueStoresManager serverIssueStoresManager;
  private final ServerInfoStorage serverInfoStorage;
  private final SQCProjectBindingStorage sqcProjectBindingStorage;
  private final SQCSearchedProjectStorage sqcSearchedProjectStorage;
  private final Map<String, SonarProjectStorage> sonarProjectStorageByKey = new ConcurrentHashMap<>();
  private final Path projectsStorageRoot;
  private final PluginsStorage pluginsStorage;
  private final Path connectionStorageRoot;
  private final AiCodeFixStorage aiCodeFixStorage;
  private final OrganizationStorage organizationStorage;

  public ConnectionStorage(Path globalStorageRoot, Path workDir, String connectionId) {
    this.connectionStorageRoot = globalStorageRoot.resolve(encodeForFs(connectionId));
    this.projectsStorageRoot = connectionStorageRoot.resolve("projects");
    this.serverIssueStoresManager = new ServerIssueStoresManager(projectsStorageRoot, workDir);
    this.serverInfoStorage = new ServerInfoStorage(connectionStorageRoot);
    this.pluginsStorage = new PluginsStorage(connectionStorageRoot);
    this.aiCodeFixStorage = new AiCodeFixStorage(connectionStorageRoot);
    this.organizationStorage = new OrganizationStorage(connectionStorageRoot);
    this.sqcProjectBindingStorage = new SQCProjectBindingStorage(connectionStorageRoot);
    this.sqcSearchedProjectStorage = new SQCSearchedProjectStorage(connectionStorageRoot);
  }

  public ServerInfoStorage serverInfo() {
    return serverInfoStorage;
  }

  public SQCProjectBindingStorage sqcProjectBinding() {
    return sqcProjectBindingStorage;
  }

  public SQCSearchedProjectStorage sqcSearchedProject() {
    return sqcSearchedProjectStorage;
  }

  public SonarProjectStorage project(String sonarProjectKey) {
    return sonarProjectStorageByKey.computeIfAbsent(sonarProjectKey,
      k -> new SonarProjectStorage(projectsStorageRoot, serverIssueStoresManager, sonarProjectKey));
  }

  public PluginsStorage plugins() {
    return pluginsStorage;
  }

  public AiCodeFixStorage aiCodeFix() {
    return aiCodeFixStorage;
  }

  public OrganizationStorage organization() {
    return organizationStorage;
  }

  public void close() {
    serverIssueStoresManager.close();
  }

  public void delete() {
    FileUtils.deleteRecursively(connectionStorageRoot);
  }
}
