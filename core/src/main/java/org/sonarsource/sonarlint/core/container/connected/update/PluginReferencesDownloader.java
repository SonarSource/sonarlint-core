/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
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
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static java.lang.String.format;

public class PluginReferencesDownloader {

  private static final Logger LOG = Loggers.get(PluginReferencesDownloader.class);

  private final PluginCache pluginCache;
  private final SonarLintWsClient wsClient;

  public PluginReferencesDownloader(SonarLintWsClient wsClient, PluginCache pluginCache) {
    this.wsClient = wsClient;
    this.pluginCache = pluginCache;
  }

  public PluginReferences fetchPlugins(List<SonarAnalyzer> analyzers) {
    Builder builder = PluginReferences.newBuilder();
    for (SonarAnalyzer analyzer : analyzers) {
      if (!analyzer.sonarlintCompatible()) {
        LOG.debug("Plugin {} is not compatible with SonarLint. Skip it.", analyzer.key());
        continue;
      }
      if (checkVersion(analyzer.version(), analyzer.minimumVersion())) {
        builder.addReference(PluginReference.newBuilder()
          .setKey(analyzer.key())
          .setHash(analyzer.hash())
          .setFilename(analyzer.filename())
          .build());
      }
    }
    return builder.build();
  }

  private static boolean checkVersion(@Nullable String version, @Nullable String minVersion) {
    if (version != null && minVersion != null) {
      Version v = Version.create(version);
      Version minimalVersion = Version.create(minVersion);

      return v.compareTo(minimalVersion) >= 0;
    }
    return true;
  }

  public PluginReferences fetchPluginsTo(Path dest, List<SonarAnalyzer> analyzers, ProgressWrapper progress) {
    PluginReferences refs = fetchPlugins(analyzers);
    int i = 0;
    float refCount = (float) refs.getReferenceList().size();
    for (PluginReference ref : refs.getReferenceList()) {
      progress.setProgressAndCheckCancel("Loading plugin " + ref.getKey(), i / refCount);
      pluginCache.get(ref.getFilename(), ref.getHash(), new SonarQubeServerPluginDownloader(ref.getKey()));
    }
    ProtobufUtil.writeToFile(refs, dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB));
    return refs;
  }

  private class SonarQubeServerPluginDownloader implements PluginCache.Copier {
    private String key;

    SonarQubeServerPluginDownloader(String key) {
      this.key = key;
    }

    @Override
    public void copy(String filename, Path toFile) throws IOException {
      String url = format("/deploy/plugins/%s/%s", key, filename);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Download plugin {} to {}", filename, toFile);
      } else {
        LOG.info("Download {}", filename);
      }

      WsResponse response = wsClient.get(url);
      try (InputStream stream = response.contentStream()) {
        FileUtils.copyInputStreamToFile(stream, toFile.toFile());
      }
    }
  }

}
