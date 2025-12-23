/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

public class UserPaths {

  public static UserPaths from(InitializeParams initializeParams) {
    var userHome = computeUserHome(initializeParams.getSonarlintUserHome());
    createFolderIfNeeded(userHome);
    var workDir = Optional.ofNullable(initializeParams.getWorkDir()).orElse(userHome.resolve("work"));
    createFolderIfNeeded(workDir);
    var storageRoot = Optional.ofNullable(initializeParams.getStorageRoot()).orElse(userHome.resolve("storage"));
    createFolderIfNeeded(storageRoot);
    return new UserPaths(userHome, workDir, storageRoot, initializeParams.getTelemetryConstantAttributes().getProductKey());
  }

  static Path computeUserHome(@Nullable String clientUserHome) {
    if (clientUserHome != null) {
      return Paths.get(clientUserHome);
    }
    return SonarLintUserHome.get();
  }

  private static void createFolderIfNeeded(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create directory '" + path + "'", e);
    }
  }

  private final Path userHome;
  private final Path workDir;
  private final Path storageRoot;
  private final String productKey;

  private UserPaths(Path userHome, Path workDir, Path storageRoot, String productKey) {
    this.userHome = userHome;
    this.workDir = workDir;
    this.storageRoot = storageRoot;
    this.productKey = productKey;
  }

  public Path getUserHome() {
    return userHome;
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public Path getHomeIdeSpecificDir(String intermediateDir) {
    return userHome.resolve(intermediateDir).resolve(productKey);
  }
}
