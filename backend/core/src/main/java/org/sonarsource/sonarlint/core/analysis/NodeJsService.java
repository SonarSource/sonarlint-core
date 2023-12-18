/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeNodeJsParams;

/**
 * Keep track of the Node.js executable to be used by analysis
 */
@Singleton
@Named
public class NodeJsService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private volatile boolean nodeInit;

  @Nullable
  private Path nodeJsPath;
  @Nullable
  private Path clientNodeJsPath;
  private final SonarLintRpcClient client;
  @Nullable
  private Version nodeJsVersion;

  public NodeJsService(InitializeParams initializeParams, SonarLintRpcClient client) {
    this.clientNodeJsPath = initializeParams.getClientNodeJsPath();
    this.client = client;
  }


  public synchronized void didChangeClientNodeJsPath(@Nullable Path clientNodeJsPath) {
    this.clientNodeJsPath = clientNodeJsPath;
    this.nodeInit = false;
  }

  private synchronized void initNodeIfNeeded() {
    if (!nodeInit) {
      var helper = new NodeJsHelper(Objects.requireNonNull(SonarLintLogger.getTargetForCopy()));
      helper.detect(clientNodeJsPath);
      this.nodeInit = true;
      var newNodeJsPath = helper.getNodeJsPath();
      var newNodeJsVersion = helper.getNodeJsVersion();

      if (!Objects.equals(newNodeJsPath, this.nodeJsPath) || !Objects.equals(newNodeJsVersion, this.nodeJsVersion)) {
        LOG.info("Node.js path set to: {} (version {})", newNodeJsPath, newNodeJsVersion);
        this.nodeJsPath = newNodeJsPath;
        this.nodeJsVersion = newNodeJsVersion;
        client.didChangeNodeJs(new DidChangeNodeJsParams(nodeJsPath, nodeJsVersion != null ? nodeJsVersion.toString() : null));
      }

      if (this.nodeJsPath == null) {
        LOG.warn(
          "Node.js could not be automatically detected, has to be configured manually in the SonarLint preferences!");

        if (SystemUtils.IS_OS_MAC_OSX) {
          // In case of macOS or could not be found, just add the warning for the user and us if we have to provide
          // support on that matter at some point.
          LOG.warn(
            "Automatic detection does not work on macOS when added to PATH from user shell configuration (e.g. Bash)");
        }
      }
    }
  }

  @CheckForNull
  public Path getNodeJsPath() {
    initNodeIfNeeded();
    return nodeJsPath;
  }

  @CheckForNull
  public Version getNodeJsVersion() {
    initNodeIfNeeded();
    return nodeJsVersion;
  }


}
