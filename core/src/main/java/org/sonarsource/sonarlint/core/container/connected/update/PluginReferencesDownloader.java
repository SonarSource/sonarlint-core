/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static java.lang.String.format;

public class PluginReferencesDownloader {

  private static final Logger LOG = Loggers.get(PluginReferencesDownloader.class);

  private final PluginCache pluginCache;
  private final SonarLintWsClient wsClient;

  public PluginReferencesDownloader(SonarLintWsClient wsClient, PluginCache pluginCache) {
    this.wsClient = wsClient;
    this.pluginCache = pluginCache;
  }

  public PluginReferences toReferences(List<SonarAnalyzer> analyzers) {
    Builder builder = PluginReferences.newBuilder();

    analyzers.stream()
      .filter(PluginReferencesDownloader::analyzerFilter)
      .map(analyzer -> PluginReference.newBuilder()
        .setKey(analyzer.key())
        .setHash(analyzer.hash())
        .setFilename(analyzer.filename())
        .build())
      .forEach(builder::addReference);

    return builder.build();
  }

  private static boolean analyzerFilter(SonarAnalyzer analyzer) {
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

  public PluginReferences fetchPluginsTo(Version serverVersion, Path dest, List<SonarAnalyzer> analyzers, ProgressWrapper progress) {
    PluginReferences refs = toReferences(analyzers);
    int i = 0;
    float refCount = (float) refs.getReferenceList().size();
    for (PluginReference ref : refs.getReferenceList()) {
      progress.setProgressAndCheckCancel("Loading analyzer " + ref.getKey(), i / refCount);
      pluginCache.get(ref.getFilename(), ref.getHash(), new SonarQubeServerPluginDownloader(serverVersion, ref.getKey()));
    }
    ProtobufUtil.writeToFile(refs, dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB));
    return refs;
  }

  private class SonarQubeServerPluginDownloader implements PluginCache.Copier {
    private final String key;
    private final Version serverVersion;

    SonarQubeServerPluginDownloader(Version serverVersion, String key) {
      this.serverVersion = serverVersion;
      this.key = key;
    }

    @Override
    public void copy(String filename, Path toFile) throws IOException {
      String url;

      if (serverVersion.compareTo(Version.create("7.2")) >= 0) {
        url = "api/plugins/download?plugin=" + key;
      } else {
        url = format("/deploy/plugins/%s/%s", key, filename);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Download plugin '{}' to '{}'...", filename, toFile);
      } else {
        LOG.info("Download '{}'...", filename);
      }

      SonarLintWsClient.consumeTimed(
        () -> wsClient.get(url),
        response -> FileUtils.copyInputStreamToFile(response.contentStream(), toFile.toFile()),
        duration -> LOG.info("Downloaded '{}' in {}ms", filename, duration));
    }
  }
}
