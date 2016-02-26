/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.sync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import static java.lang.String.format;

public class PluginReferencesSync {

  private static final Logger LOG = LoggerFactory.getLogger(PluginReferencesSync.class);

  private final SonarLintWsClient wsClient;
  private final PluginCache pluginCache;

  public PluginReferencesSync(SonarLintWsClient wsClient, PluginCache pluginCache) {
    this.wsClient = wsClient;
    this.pluginCache = pluginCache;
  }

  public void fetchPluginsTo(Path dest, Set<String> allowedPlugins) {
    WsResponse response = wsClient.get("deploy/plugins/index.txt");
    if (response.isSuccessful()) {
      PluginReferences.Builder builder = PluginReferences.newBuilder();
      String responseStr = response.content();
      Scanner scanner = new Scanner(responseStr);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        String[] fields = StringUtils.split(line, ",");
        if (fields.length >= 2) {
          String[] nameAndHash = StringUtils.split(fields[1], "|");
          String key = fields[0];
          if (!allowedPlugins.contains(key)) {
            LOG.debug("Plugin {} is not in the SonarLint whitelist. Skip it.", key);
            continue;
          }
          String filename = nameAndHash[0];
          String hash = nameAndHash[1];
          builder.addReference(PluginReference.newBuilder()
            .setKey(key)
            .setHash(hash)
            .setFilename(filename)
            .build());
          pluginCache.get(filename, hash, new SonarQubeServerPluginDownloader(key));
        }
      }
      scanner.close();
      ProtobufUtil.writeToFile(builder.build(), dest.resolve(StorageManager.PLUGIN_REFERENCES_PB));
    } else {
      throw new IllegalStateException("Unable to get plugin list: " + response.code() + " " + response.content());
    }

  }

  private class SonarQubeServerPluginDownloader implements PluginCache.Downloader {
    private String key;

    SonarQubeServerPluginDownloader(String key) {
      this.key = key;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
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
