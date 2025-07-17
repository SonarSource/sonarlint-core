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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.StoredSQCSearchedProject;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class SQCSearchedProjectStorage {

  public static final String SQC_SEARCHED_PROJECT_PB = "sqc_searched_project.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public SQCSearchedProjectStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(SQC_SEARCHED_PROJECT_PB);
  }

  public void store(String projectKey, String projectName) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var remoteBinding = adapt(projectKey, projectName);
    LOG.debug("Storing server info in {}", storageFilePath);
    rwLock.write(() -> writeToFile(remoteBinding, storageFilePath));
    LOG.debug("Stored server info");
  }

  private static Sonarlint.SQCSearchedProject adapt(String projectKey, String projectName) {
    var sqcSearchedProjectBuilder = Sonarlint.SQCSearchedProject.newBuilder().setProjectKey(projectKey);
    sqcSearchedProjectBuilder.setProjectName(projectName);
    return sqcSearchedProjectBuilder.build();
  }

  private static StoredSQCSearchedProject adapt(Sonarlint.SQCSearchedProject binding) {
    return new StoredSQCSearchedProject(binding.getProjectKey(), binding.getProjectName());
  }


  public Optional<StoredSQCSearchedProject> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.SQCSearchedProject.parser())))
      : Optional.empty());
  }

}
