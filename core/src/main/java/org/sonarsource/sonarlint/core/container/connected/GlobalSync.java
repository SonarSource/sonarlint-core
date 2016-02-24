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
package org.sonarsource.sonarlint.core.container.connected;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Scanner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint.SyncStatus;

import static java.lang.String.format;

public class GlobalSync {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalSync.class);

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final GlobalConfiguration globalConfig;
  private final ServerConfiguration serverConfig;
  private final PluginCache pluginCache;

  public GlobalSync(StorageManager storageManager, SonarLintWsClient wsClient, GlobalConfiguration globalConfig, ServerConfiguration serverConfig, PluginCache pluginCache) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.globalConfig = globalConfig;
    this.serverConfig = serverConfig;
    this.pluginCache = pluginCache;
  }

  public void sync() {
    Path temp;
    try {
      temp = Files.createTempDirectory(globalConfig.getWorkDir(), "sync");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }

    ProtobufUtil.writeToFile(fetchServerStatus(), temp.resolve("server_status.pb"));
    ProtobufUtil.writeToFile(fetchPlugins(), temp.resolve("plugin_references.pb"));

    SyncStatus syncStatus = SyncStatus.newBuilder()
      .setClientUserAgent(serverConfig.getUserAgent())
      .setSonarlintCoreVersion(readSlCoreVersion())
      .setSyncTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(syncStatus, temp.resolve("sync_status.pb"));

    Path dest = storageManager.getGlobalStorageRoot();
    deleteDirectory(dest);
    try {
      try {
        Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, dest);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to move directory " + temp + " to " + dest, e);
    }
  }

  private void deleteDirectory(Path dest) {
    try {
      Files.walkFileTree(dest, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }

      });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to delete directory " + dest, e);
    }
  }

  private String readSlCoreVersion() {
    try {
      return IOUtils.toString(this.getClass().getResourceAsStream("/sl_core_version.txt"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read library version", e);
    }
  }

  public ServerStatus fetchServerStatus() {
    WsResponse response = wsClient.get("api/system/status");
    if (response.isSuccessful()) {
      String responseStr = response.content();
      try {
        ServerStatus.Builder builder = ServerStatus.newBuilder();
        JsonFormat.parser().merge(responseStr, builder);
        return builder.build();
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException("Unable to parse server status from: " + response.content(), e);
      }
    } else {
      throw new IllegalStateException("Unable to get server status: " + response.code() + " " + response.content());
    }
  }

  public PluginReferences fetchPlugins() {
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
          // TODO handle plugin exclusions by key

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
      return builder.build();
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
