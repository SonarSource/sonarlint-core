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
package org.sonarsource.sonarlint.core.plugin.skipped;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class SkippedPluginsNotifierService {
  private final SonarLintLogger logger = SonarLintLogger.get();
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final SonarLintRpcClient client;

  public SkippedPluginsNotifierService(SkippedPluginsRepository skippedPluginsRepository, ConnectionConfigurationRepository connectionConfigurationRepository,
    SonarLintRpcClient client) {
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.client = client;
  }

  @EventListener
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    var analyzedLanguages = event.getAnalyzedLanguages();
    var configurationScopeId = event.getConfigurationScopeId();
    var skippedPlugins = getSkippedPlugins(configurationScopeId);
    if (skippedPlugins == null || skippedPlugins.isEmpty()) {
      return;
    }
    notifyClientOfSkippedPlugins(configurationScopeId, analyzedLanguages, skippedPlugins);
  }

  private void notifyClientOfSkippedPlugins(String configurationScopeId, Set<SonarLanguage> analyzedLanguages, List<SkippedPlugin> skippedPlugins) {
    analyzedLanguages.stream().filter(Objects::nonNull)
      .forEach(sonarLanguage -> {
        final var skippedPlugin = skippedPlugins.stream().filter(p -> p.getKey().equals(sonarLanguage.getPluginKey())).findFirst();
        skippedPlugin.ifPresent(plugin -> {
          var skipReason = plugin.getReason();
          if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement) {
            var languageLabel = Language.valueOf(sonarLanguage.name()).getLabel();
            final var title = "<b>SonarLint failed to analyze " + languageLabel + " code</b>";
            if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
              var content = String.format(
                "SonarLint requires Java runtime version %s or later to analyze %s code. Current version is %s.",
                runtimeRequirement.getMinVersion(), languageLabel, runtimeRequirement.getCurrentVersion());
              client.didSkipLoadingPlugin(new DidSkipLoadingPluginParams(configurationScopeId, sonarLanguage, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, content));
            } else if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
              var content = new StringBuilder(
                String.format("SonarLint requires Node.js runtime version %s or later to analyze %s code.", runtimeRequirement.getMinVersion(), languageLabel));
              if (runtimeRequirement.getCurrentVersion() != null) {
                content.append(String.format(" Current version is %s.", runtimeRequirement.getCurrentVersion()));
              }
              client.didSkipLoadingPlugin(
                new DidSkipLoadingPluginParams(configurationScopeId, sonarLanguage, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, content.toString()));
            }
          }
        });
      });
  }

  @CheckForNull
  private List<SkippedPlugin> getSkippedPlugins(String configurationScopeId) {
    return null;
  }
}
