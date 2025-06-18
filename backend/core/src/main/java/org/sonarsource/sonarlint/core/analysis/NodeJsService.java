/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.ApplicationEventPublisher;

import static org.sonarsource.sonarlint.core.commons.api.SonarLanguage.Constants.JAVASCRIPT_PLUGIN_KEY;

/**
 * Keep track of the Node.js executable to be used by analysis
 */
public class NodeJsService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ApplicationEventPublisher eventPublisher;
  private final boolean isNodeJsNeeded;
  private volatile boolean nodeAutoDetected;
  @Nullable
  private InstalledNodeJs autoDetectedNodeJs;
  @Nullable
  private Path clientNodeJsPath;
  private boolean clientForcedNodeJsDetected;
  @Nullable
  private InstalledNodeJs clientForcedNodeJs;

  public NodeJsService(InitializeParams initializeParams, ApplicationEventPublisher eventPublisher) {
    var languageSpecificRequirements = initializeParams.getLanguageSpecificRequirements();
    this.clientNodeJsPath = languageSpecificRequirements == null || languageSpecificRequirements.getJsTsRequirements() == null ? null
      : languageSpecificRequirements.getJsTsRequirements().getClientNodeJsPath();
    this.isNodeJsNeeded = isNodeJsNeeded(initializeParams);
    this.eventPublisher = eventPublisher;
  }

  private static boolean isNodeJsNeeded(InitializeParams initializeParams) {
    // in theory all clients bundle SonarJS, so this should always return true
    // in practice and to speed up tests, we will avoid looking up Node.js if SonarJS is not present
    var languagesNeedingNodeJsInSonarJs = SonarLanguage.getLanguagesByPluginKey(JAVASCRIPT_PLUGIN_KEY).stream().map(l -> Language.valueOf(l.name())).collect(Collectors.toSet());
    return !Collections.disjoint(initializeParams.getEnabledLanguagesInStandaloneMode(), languagesNeedingNodeJsInSonarJs)
      || !Collections.disjoint(initializeParams.getExtraEnabledLanguagesInConnectedMode(), languagesNeedingNodeJsInSonarJs);
  }

  @CheckForNull
  public synchronized InstalledNodeJs didChangeClientNodeJsPath(@Nullable Path clientNodeJsPath) {
    if (!Objects.equals(this.clientNodeJsPath, clientNodeJsPath)) {
      this.clientNodeJsPath = clientNodeJsPath;
      this.clientForcedNodeJsDetected = false;
      this.eventPublisher.publishEvent(new ClientNodeJsPathChanged());
    }
    var forcedNodeJs = getClientForcedNodeJs();
    return forcedNodeJs == null ? null : new InstalledNodeJs(forcedNodeJs.getPath(), forcedNodeJs.getVersion());
  }

  @CheckForNull
  public synchronized InstalledNodeJs getActiveNodeJs() {
    return clientNodeJsPath == null ? getAutoDetectedNodeJs() : getClientForcedNodeJs();
  }

  public synchronized Optional<Version> getActiveNodeJsVersion() {
    return Optional.ofNullable(getActiveNodeJs()).map(InstalledNodeJs::getVersion);
  }

  @CheckForNull
  public InstalledNodeJs getAutoDetectedNodeJs() {
    if (!nodeAutoDetected) {
      if (!isNodeJsNeeded) {
        LOG.debug("Skip Node.js auto-detection as no plugins require it");
        nodeAutoDetected = true;
        return null;
      }
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
