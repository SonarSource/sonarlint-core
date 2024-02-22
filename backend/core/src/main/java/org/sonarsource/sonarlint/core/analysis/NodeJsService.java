/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

/**
 * Keep track of the Node.js executable to be used by analysis
 */
@Singleton
@Named
public class NodeJsService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private volatile boolean nodeAutoDetected;
  @Nullable
  private InstalledNodeJs autoDetectedNodeJs;
  @Nullable
  private Path clientNodeJsPath;
  private boolean clientForcedNodeJsDetected;
  @Nullable
  private InstalledNodeJs clientForcedNodeJs;

  public NodeJsService(InitializeParams initializeParams) {
    this.clientNodeJsPath = initializeParams.getClientNodeJsPath();
  }

  public synchronized void didChangeClientNodeJsPath(@Nullable Path clientNodeJsPath) {
    this.clientNodeJsPath = clientNodeJsPath;
    this.clientForcedNodeJsDetected = false;
  }

  @CheckForNull
  public synchronized InstalledNodeJs getActiveNodeJs() {
    return clientNodeJsPath == null ? getAutoDetectedNodeJs() : getClientForcedNodeJs();
  }

  @CheckForNull
  public InstalledNodeJs getAutoDetectedNodeJs() {
    if (!nodeAutoDetected) {
      var helper = new NodeJsHelper();
      autoDetectedNodeJs = helper.autoDetect();
      nodeAutoDetected = true;
      logAutoDetectionResults(autoDetectedNodeJs);
    }
    return autoDetectedNodeJs;
  }

  @CheckForNull
  private InstalledNodeJs getClientForcedNodeJs() {
    if (!clientForcedNodeJsDetected) {
      var helper = new NodeJsHelper();
      clientForcedNodeJs = helper.detect(clientNodeJsPath);
      clientForcedNodeJsDetected = true;
      logClientForcedDetectionResults(clientForcedNodeJs);
    }
    return clientForcedNodeJs;
  }

  private static void logAutoDetectionResults(@Nullable InstalledNodeJs autoDetectedNode) {
    if (autoDetectedNode != null) {
      LOG.debug("Auto-detected Node.js path set to: {} (version {})", autoDetectedNode.getPath(), autoDetectedNode.getVersion());
    } else {
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

  private static void logClientForcedDetectionResults(@Nullable InstalledNodeJs detectedNode) {
    if (detectedNode != null) {
      LOG.debug("Node.js path set to: {} (version {})", detectedNode.getPath(), detectedNode.getVersion());
    } else {
      LOG.warn(
        "Configured Node.js could not be detected, please check your configuration in the SonarLint settings");
    }
  }
}
