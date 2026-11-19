/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;
import org.springframework.context.event.EventListener;

public class PluginStatusNotifierService {

  private static final String EMBEDDED_RESULTS_KEY = "";

  private final PluginsService pluginsService;
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  /**
   * Terminal states already reported for downloads that the cached configuration still lists as DOWNLOADING.
   * A later completion merges into that cache, so these results have to be applied again or the earlier language is sent as DOWNLOADING.
   */
  private final Map<String, Map<SonarLanguage, PluginStatus>> appliedDownloadResultsByConnection = new HashMap<>();
  private final Object appliedDownloadResultsLock = new Object();

  public PluginStatusNotifierService(PluginsService pluginsService, SonarLintRpcClient client, ConfigurationRepository configurationRepository) {
    this.pluginsService = pluginsService;
    this.client = client;
    this.configurationRepository = configurationRepository;
  }

  @EventListener
  public void onPluginStatusesChanged(PluginStatusesChangedEvent event) {
    clearAppliedDownloadResults(event.connectionId());
    var connectionId = event.connectionId();
    if (connectionId != null) {
      // All affected scopes share the same connection: reuse the pre-computed statuses from the event
      notifyBoundScopes(connectionId, event.pluginStatuses());
    } else {
      // Embedded plugins changed: each scope may have a different effective connection, resolve per scope
      notifyEachScope(this::statusesForScope);
    }
  }

  /**
   * A download publishes {@link PluginStatusUpdateEvent} when it ends, whether or not an analysis scheduler exists.
   * The event carries only the languages of that artifact, so it is merged into the full per-scope list.
   */
  @EventListener
  public void onPluginStatusUpdate(PluginStatusUpdateEvent event) {
    if (event.newStatuses().stream().noneMatch(status -> status.language() != null)) {
      return;
    }
    var connectionId = event.connectionId();
    if (connectionId != null) {
      if (configurationRepository.getBoundScopesToConnection(connectionId).isEmpty()) {
        return;
      }
      var current = pluginsService.getPluginStatuses(connectionId);
      notifyBoundScopes(connectionId, mergeKeepingEarlierResults(connectionId, current, event.newStatuses(), false));
    } else {
      notifyEachScope(scopeId -> mergeKeepingEarlierResults(null, statusesForScope(scopeId), event.newStatuses(), true));
    }
  }

  private List<PluginStatus> mergeKeepingEarlierResults(@Nullable String connectionId, List<PluginStatus> current, Collection<PluginStatus> updates,
    boolean binariesDownload) {
    synchronized (appliedDownloadResultsLock) {
      var remembered = appliedDownloadResultsByConnection.computeIfAbsent(connectionKey(connectionId), key -> new EnumMap<>(SonarLanguage.class));
      var withEarlierResults = mergeDownloadUpdate(current, remembered.values(), binariesDownload);
      var merged = mergeDownloadUpdate(withEarlierResults, updates, binariesDownload);
      for (var update : updates) {
        if (update.language() != null && merged.contains(update)) {
          remembered.put(update.language(), update);
        }
      }
      return merged;
    }
  }

  private void clearAppliedDownloadResults(@Nullable String connectionId) {
    synchronized (appliedDownloadResultsLock) {
      appliedDownloadResultsByConnection.remove(connectionKey(connectionId));
    }
  }

  private static String connectionKey(@Nullable String connectionId) {
    return connectionId == null ? EMBEDDED_RESULTS_KEY : connectionId;
  }

  private List<PluginStatus> statusesForScope(String configScopeId) {
    var effectiveConnectionId = configurationRepository.getEffectiveBinding(configScopeId)
      .map(Binding::connectionId).orElse(null);
    return pluginsService.getPluginStatuses(effectiveConnectionId);
  }

  private void notifyBoundScopes(String connectionId, List<PluginStatus> statuses) {
    var statusDtos = PluginStatusMapper.toDto(statuses);
    configurationRepository.getBoundScopesToConnection(connectionId).stream()
      .map(BoundScope::getConfigScopeId)
      .forEach(scopeId -> client.didChangePluginStatuses(new DidChangePluginStatusesParams(scopeId, statusDtos)));
  }

  private void notifyEachScope(Function<String, List<PluginStatus>> statusesByScope) {
    for (var configScopeId : configurationRepository.getConfigScopeIds()) {
      client.didChangePluginStatuses(new DidChangePluginStatusesParams(configScopeId, PluginStatusMapper.toDto(statusesByScope.apply(configScopeId))));
    }
  }

  /**
   * Replaces a language that is still downloading from the same source with the state reported at the end of that download.
   * A binaries download does not replace a SonarQube download, and a language that is not downloading keeps its status.
   */
  private static List<PluginStatus> mergeDownloadUpdate(List<PluginStatus> current, Collection<PluginStatus> updates, boolean binariesDownload) {
    var updatesByLanguage = new EnumMap<SonarLanguage, PluginStatus>(SonarLanguage.class);
    for (var update : updates) {
      if (update.language() != null) {
        updatesByLanguage.put(update.language(), update);
      }
    }
    return current.stream()
      .map(status -> overlayDownloading(status, updatesByLanguage, binariesDownload))
      .toList();
  }

  private static PluginStatus overlayDownloading(PluginStatus status, EnumMap<SonarLanguage, PluginStatus> updatesByLanguage, boolean binariesDownload) {
    if (status.language() == null || status.state() != ArtifactState.DOWNLOADING || !isSameDownload(status.source(), binariesDownload)) {
      return status;
    }
    var update = updatesByLanguage.get(status.language());
    return update != null ? update : status;
  }

  private static boolean isSameDownload(@Nullable ArtifactOrigin source, boolean binariesDownload) {
    if (binariesDownload) {
      return source == ArtifactOrigin.ON_DEMAND;
    }
    return source == ArtifactOrigin.SONARQUBE_SERVER || source == ArtifactOrigin.SONARQUBE_CLOUD;
  }

}
