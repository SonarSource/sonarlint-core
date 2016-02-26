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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint.SyncStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class GlobalSync {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final GlobalConfiguration globalConfig;
  private final ServerConfiguration serverConfig;
  private final PluginReferencesSync pluginReferenceSync;
  private final GlobalPropertiesSync globalPropertiesSync;
  private final RulesSync rulesSync;

  public GlobalSync(StorageManager storageManager, SonarLintWsClient wsClient, GlobalConfiguration globalConfig, ServerConfiguration serverConfig,
    PluginReferencesSync pluginReferenceSync, GlobalPropertiesSync globalPropertiesSync, RulesSync rulesSync) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.globalConfig = globalConfig;
    this.serverConfig = serverConfig;
    this.pluginReferenceSync = pluginReferenceSync;
    this.globalPropertiesSync = globalPropertiesSync;
    this.rulesSync = rulesSync;
  }

  public void sync() {
    Path temp;
    try {
      temp = Files.createTempDirectory(globalConfig.getWorkDir(), "sync");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }

    ServerStatus serverStatus = fetchServerStatus();
    if (!"UP".equals(serverStatus.getStatus())) {
      throw new IllegalStateException("Server not ready (" + serverStatus.getStatus() + ")");
    }
    ProtobufUtil.writeToFile(serverStatus, temp.resolve("server_status.pb"));

    Set<String> allowedPlugins = globalPropertiesSync.fetchGlobalPropertiesTo(temp);

    pluginReferenceSync.fetchPluginsTo(temp, allowedPlugins);

    rulesSync.fetchRulesTo(temp);

    SyncStatus syncStatus = SyncStatus.newBuilder()
      .setClientUserAgent(serverConfig.getUserAgent())
      .setSonarlintCoreVersion(readSlCoreVersion())
      .setSyncTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(syncStatus, temp.resolve("sync_status.pb"));

    Path dest = storageManager.getGlobalStorageRoot();
    FileUtils.deleteDirectory(dest);
    FileUtils.moveDir(temp, dest);
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

}
