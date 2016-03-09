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
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.SyncStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalSync {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final PluginReferencesSync pluginReferenceSync;
  private final GlobalPropertiesSync globalPropertiesSync;
  private final RulesSync rulesSync;
  private final TempFolder tempFolder;
  private final ModuleListSync moduleListSync;

  public GlobalSync(StorageManager storageManager, SonarLintWsClient wsClient, PluginReferencesSync pluginReferenceSync, GlobalPropertiesSync globalPropertiesSync,
    RulesSync rulesSync, ModuleListSync moduleListSync, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.pluginReferenceSync = pluginReferenceSync;
    this.globalPropertiesSync = globalPropertiesSync;
    this.rulesSync = rulesSync;
    this.moduleListSync = moduleListSync;
    this.tempFolder = tempFolder;
  }

  public void sync() {
    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      throw new IllegalStateException("Server not ready (" + serverStatus.getStatus() + ")");
    }
    Version serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareTo(Version.create("5.2")) < 0) {
      throw new UnsupportedOperationException("SonarQube server version should be 5.2+");
    }
    Path temp = tempFolder.newDir().toPath();
    ProtobufUtil.writeToFile(serverStatus, temp.resolve(StorageManager.SERVER_INFO_PB));

    Set<String> allowedPlugins = globalPropertiesSync.fetchGlobalPropertiesTo(temp);

    pluginReferenceSync.fetchPluginsTo(temp, allowedPlugins);

    rulesSync.fetchRulesTo(temp);
    moduleListSync.fetchModulesList(temp);

    SyncStatus syncStatus = SyncStatus.newBuilder()
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setSyncTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(syncStatus, temp.resolve(StorageManager.SYNC_STATUS_PB));

    Path dest = storageManager.getGlobalStorageRoot();
    FileUtils.deleteDirectory(dest);
    FileUtils.moveDir(temp, dest);
  }

  private ServerInfos fetchServerInfos() {
    WsResponse response = wsClient.get("api/system/status");
    String responseStr = response.content();
    try {
      ServerInfos.Builder builder = ServerInfos.newBuilder();
      JsonFormat.parser().merge(responseStr, builder);
      return builder.build();
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Unable to parse server infos from: " + response.content(), e);
    }
  }

}
