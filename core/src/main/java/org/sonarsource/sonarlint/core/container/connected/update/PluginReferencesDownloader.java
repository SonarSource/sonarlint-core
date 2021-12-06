/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class PluginReferencesDownloader {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final PluginCache pluginCache;
  private final PluginsApi pluginsApi;
  private final ConnectedGlobalConfiguration configuration;
  private final PluginReferenceStore pluginReferenceStore;

  public PluginReferencesDownloader(ServerApiHelper serverApiHelper, PluginCache pluginCache, ConnectedGlobalConfiguration configuration,
    PluginReferenceStore pluginReferenceStore) {
    this.pluginsApi = new ServerApi(serverApiHelper).plugins();
    this.pluginCache = pluginCache;
    this.configuration = configuration;
    this.pluginReferenceStore = pluginReferenceStore;
  }

  public PluginReferences toReferences(List<SonarAnalyzer> analyzers) {
    Builder builder = PluginReferences.newBuilder();

    analyzers.stream()
      .filter(this::isCompatible)
      .map(this::toPluginReference)
      .forEach(builder::addReference);

    return builder.build();
  }

  private PluginReference toPluginReference(SonarAnalyzer analyzer) {
    org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference.Builder builder = PluginReference.newBuilder()
      .setKey(analyzer.key());
    if (!configuration.getEmbeddedPluginUrlsByKey().containsKey(analyzer.key())) {
      // For embedded plugins, ignore hash and filename
      builder.setHash(analyzer.hash())
        .setFilename(analyzer.filename());
    }
    return builder.build();
  }

  private boolean isCompatible(SonarAnalyzer analyzer) {
    if (configuration.getEmbeddedPluginUrlsByKey().containsKey(analyzer.key())) {
      return true;
    }
    if (!analyzer.sonarlintCompatible()) {
      LOG.debug("Code analyzer '{}' is not compatible with SonarLint. Skip downloading it.", analyzer.key());
      return false;
    } else if (!analyzer.versionSupported()) {
      LOG.debug("Code analyzer '{}' version '{}' is not supported (minimal version is '{}'). Skip downloading it.",
        analyzer.key(), analyzer.version(), analyzer.minimumVersion());
      return false;
    }
    return true;
  }

  public void fetchPlugins(List<SonarAnalyzer> analyzers, ProgressWrapper progress) {
    PluginReferences refs = toReferences(analyzers);
    int i = 0;
    float refCount = refs.getReferenceList().size();
    for (PluginReference ref : refs.getReferenceList()) {
      if (configuration.getEmbeddedPluginUrlsByKey().containsKey(ref.getKey())) {
        LOG.debug("Code analyzer '{}' is embedded in SonarLint. Skip downloading it.", ref.getKey());
        continue;
      }
      progress.setProgressAndCheckCancel("Loading analyzer " + ref.getKey(), i / refCount);
      pluginCache.get(ref.getFilename(), ref.getHash(), new SonarQubeServerPluginDownloader(ref.getKey()));
    }
    pluginReferenceStore.store(refs);
  }

  private class SonarQubeServerPluginDownloader implements PluginCache.Copier {
    private final String key;

    SonarQubeServerPluginDownloader(String key) {
      this.key = key;
    }

    @Override
    public void copy(String filename, Path toFile) throws IOException {
      LOG.debug("Download plugin '{}' to '{}'...", filename, toFile);
      pluginsApi.getPlugin(key, pluginInputStream -> FileUtils.copyInputStreamToFile(pluginInputStream, toFile.toFile()));
    }
  }
}
