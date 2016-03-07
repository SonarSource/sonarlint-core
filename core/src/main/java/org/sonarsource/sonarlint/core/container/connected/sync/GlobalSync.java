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
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.sonar.api.utils.TempFolder;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.SyncStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalSync {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final GlobalConfiguration globalConfig;
  private final ServerConfiguration serverConfig;
  private final PluginReferencesSync pluginReferenceSync;
  private final GlobalPropertiesSync globalPropertiesSync;
  private final RulesSync rulesSync;
  private final TempFolder tempFolder;

  public GlobalSync(StorageManager storageManager, SonarLintWsClient wsClient, GlobalConfiguration globalConfig, ServerConfiguration serverConfig,
    PluginReferencesSync pluginReferenceSync, GlobalPropertiesSync globalPropertiesSync, RulesSync rulesSync, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.globalConfig = globalConfig;
    this.serverConfig = serverConfig;
    this.pluginReferenceSync = pluginReferenceSync;
    this.globalPropertiesSync = globalPropertiesSync;
    this.rulesSync = rulesSync;
    this.tempFolder = tempFolder;
  }

  public void sync() {
    Path temp = tempFolder.newDir().toPath();

    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      throw new IllegalStateException("Server not ready (" + serverStatus.getStatus() + ")");
    }
    ProtobufUtil.writeToFile(serverStatus, temp.resolve(StorageManager.SERVER_INFO_PB));

    Set<String> allowedPlugins = globalPropertiesSync.fetchGlobalPropertiesTo(temp);

    pluginReferenceSync.fetchPluginsTo(temp, allowedPlugins);

    rulesSync.fetchRulesTo(temp);

    SyncStatus syncStatus = SyncStatus.newBuilder()
      .setClientUserAgent(serverConfig.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.readSlCoreVersion())
      .setSyncTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(syncStatus, temp.resolve(StorageManager.SYNC_STATUS_PB));

    Path dest = storageManager.getGlobalStorageRoot();
    FileUtils.deleteDirectory(dest);
    FileUtils.moveDir(temp, dest);
  }

  public ServerInfos fetchServerInfos() {
    WsResponse response = wsClient.get("api/system/status");
    if (response.isSuccessful()) {
      String responseStr = response.content();
      try {
        ServerInfos.Builder builder = ServerInfos.newBuilder();
        JsonFormat.parser().merge(responseStr, builder);
        return builder.build();
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException("Unable to parse server infos from: " + response.content(), e);
      }
    } else {
      throw new IllegalStateException("Unable to get server infos: " + response.code() + " " + response.content());
    }
  }

}
