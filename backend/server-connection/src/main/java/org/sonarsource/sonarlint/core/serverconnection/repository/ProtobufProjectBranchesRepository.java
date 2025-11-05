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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

/**
 * Protobuf-based implementation of ProjectBranchesRepository.
 */
public class ProtobufProjectBranchesRepository implements ProjectBranchesRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageRoot;

  public ProtobufProjectBranchesRepository(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  private Path getStorageFilePath(String connectionId, String projectKey) {
    var connectionStorageRoot = storageRoot.resolve(encodeForFs(connectionId));
    var projectsStorageRoot = connectionStorageRoot.resolve("projects");
    var projectStorageRoot = projectsStorageRoot.resolve(encodeForFs(projectKey));
    return projectStorageRoot.resolve("project_branches.pb");
  }

  @Override
  public boolean exists(String connectionId, String projectKey) {
    return Files.exists(getStorageFilePath(connectionId, projectKey));
  }

  @Override
  public void store(String connectionId, String projectKey, ProjectBranches projectBranches) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    FileUtils.mkdirs(storageFilePath.getParent());
    var data = adapt(projectBranches);
    LOG.debug("Storing project branches in {}", storageFilePath);
    new RWLock().write(() -> writeToFile(data, storageFilePath));
  }

  @Override
  public ProjectBranches read(String connectionId, String projectKey) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    return adapt(new RWLock().read(() -> ProtobufFileUtil.readFile(storageFilePath, Sonarlint.ProjectBranches.parser())));
  }

  private static ProjectBranches adapt(Sonarlint.ProjectBranches projectBranches) {
    return new ProjectBranches(Set.copyOf(projectBranches.getBranchNameList()), projectBranches.getMainBranchName());
  }

  private static Sonarlint.ProjectBranches adapt(ProjectBranches projectBranches) {
    return Sonarlint.ProjectBranches.newBuilder()
      .addAllBranchName(projectBranches.getBranchNames())
      .setMainBranchName(projectBranches.getMainBranchName())
      .build();
  }
}
