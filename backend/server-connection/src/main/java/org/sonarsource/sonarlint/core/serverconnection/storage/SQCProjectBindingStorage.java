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
import org.sonarsource.sonarlint.core.serverconnection.StoredSQCProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class SQCProjectBindingStorage {

  public static final String PROJECT_BINDINGS_PB = "project_bindings.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public SQCProjectBindingStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(PROJECT_BINDINGS_PB);
  }

  public void store(String projectId) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var remoteBinding = adapt(projectId);
    LOG.debug("Storing server info in {}", storageFilePath);
    rwLock.write(() -> writeToFile(remoteBinding, storageFilePath));
    LOG.debug("Stored server info");
  }

  private static Sonarlint.SQCProjectBinding adapt(String projectId) {
    var remoteProjectBindingBuilder = Sonarlint.SQCProjectBinding.newBuilder().setProjectId(projectId);
    return remoteProjectBindingBuilder.build();
  }

  private static StoredSQCProjectBinding adapt(Sonarlint.SQCProjectBinding binding) {
    return new StoredSQCProjectBinding(binding.getProjectId());
  }


  public Optional<StoredSQCProjectBinding> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.SQCProjectBinding.parser())))
      : Optional.empty());
  }

}
