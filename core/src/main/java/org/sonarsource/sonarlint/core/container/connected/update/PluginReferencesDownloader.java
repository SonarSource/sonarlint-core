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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Scanner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import static java.lang.String.format;

public class PluginReferencesDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(PluginReferencesDownloader.class);

  private final SonarLintWsClient wsClient;
  private final PluginCache pluginCache;
  private final PluginVersionChecker pluginVersionChecker;

  public PluginReferencesDownloader(SonarLintWsClient wsClient, PluginCache pluginCache, PluginVersionChecker pluginVersionChecker) {
    this.wsClient = wsClient;
    this.pluginCache = pluginCache;
    this.pluginVersionChecker = pluginVersionChecker;
  }

  public PluginReferences fetchPluginsTo(Path dest, String serverVersion) {
    boolean compatibleFlagPresent = Version.create(serverVersion).compareToIgnoreQualifier(Version.create("6.0")) >= 0;
    WsResponse response = wsClient.get("deploy/plugins/index.txt");
    PluginReferences.Builder builder = PluginReferences.newBuilder();
    String responseStr = response.content();

    pluginVersionChecker.checkPlugins(responseStr);

    Scanner scanner = new Scanner(responseStr);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      String[] fields = StringUtils.split(line, ",");
      String[] nameAndHash = StringUtils.split(fields[fields.length - 1], "|");
      String key = fields[0];
      boolean compatible = PluginCopier.isWhitelisted(key) || !compatibleFlagPresent || "true".equals(fields[1]);
      if (!compatible) {
        LOG.debug("Plugin {} is not compatible with SonarLint. Skip it.", key);
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
    scanner.close();
    PluginReferences pluginReferences = builder.build();
    ProtobufUtil.writeToFile(pluginReferences, dest.resolve(StorageManager.PLUGIN_REFERENCES_PB));
    return pluginReferences;
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
