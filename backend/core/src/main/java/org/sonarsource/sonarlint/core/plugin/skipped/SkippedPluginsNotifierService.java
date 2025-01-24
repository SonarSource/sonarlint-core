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
package org.sonarsource.sonarlint.core.plugin.skipped;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.event.EventListener;

public class SkippedPluginsNotifierService {
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final ConfigurationRepository configurationRepository;
  private final SonarLintRpcClient client;
  private final Set<String> alreadyNotifiedPluginKeys = new HashSet<>();

  public SkippedPluginsNotifierService(SkippedPluginsRepository skippedPluginsRepository, ConfigurationRepository configurationRepository, SonarLintRpcClient client) {
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.configurationRepository = configurationRepository;
    this.client = client;
  }

  @EventListener
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    var detectedLanguages = event.getDetectedLanguages();
    var configurationScopeId = event.getConfigurationScopeId();
    var skippedPlugins = getSkippedPluginsToNotify(configurationScopeId);
    if (skippedPlugins.isEmpty()) {
      return;
    }
    notifyClientOfSkippedPlugins(configurationScopeId, detectedLanguages, skippedPlugins);
  }

  private void notifyClientOfSkippedPlugins(String configurationScopeId, Set<SonarLanguage> detectedLanguages, List<SkippedPlugin> skippedPlugins) {
    detectedLanguages.stream().filter(Objects::nonNull)
      .forEach(sonarLanguage -> skippedPlugins.stream().filter(p -> p.getKey().equals(sonarLanguage.getPluginKey()))
        .findFirst()
        .ifPresent(skippedPlugin -> {
          var skipReason = skippedPlugin.getReason();
          if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement) {
            var rpcLanguage = Language.valueOf(sonarLanguage.name());
            var rpcSkipReason = runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE
              ? DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE
              : DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS;
            alreadyNotifiedPluginKeys.add(skippedPlugin.getKey());
            client.didSkipLoadingPlugin(
              new DidSkipLoadingPluginParams(configurationScopeId, rpcLanguage, rpcSkipReason, runtimeRequirement.getMinVersion(), runtimeRequirement.getCurrentVersion()));
          }
        }));
  }

  private List<SkippedPlugin> getSkippedPluginsToNotify(String configurationScopeId) {
    var skippedPlugins = getSkippedPlugins(configurationScopeId);
    if (skippedPlugins != null) {
      return skippedPlugins.stream().filter(skippedPlugin -> !alreadyNotifiedPluginKeys.contains(skippedPlugin.getKey())).toList();
    }
    return List.of();
  }

  @CheckForNull
  private List<SkippedPlugin> getSkippedPlugins(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> skippedPluginsRepository.getSkippedPlugins(binding.getConnectionId()))
      .orElseGet(skippedPluginsRepository::getSkippedEmbeddedPlugins);
  }
}
