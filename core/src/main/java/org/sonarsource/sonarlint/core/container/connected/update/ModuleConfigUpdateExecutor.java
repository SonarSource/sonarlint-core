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

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.sonar.api.utils.TempFolder;
import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ModuleConfigUpdateExecutor {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final TempFolder tempFolder;

  public ModuleConfigUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.tempFolder = tempFolder;
  }

  public void update(String moduleKey) {
    GlobalProperties globalProps = storageManager.readGlobalPropertiesFromStorage();
    Set<String> qProfileKeys = storageManager.readQProfilesFromStorage().getQprofilesByKey().keySet();

    ModuleConfiguration.Builder builder = ModuleConfiguration.newBuilder();

    fetchProjectQualityProfiles(moduleKey, qProfileKeys, builder);
    fetchProjectProperties(moduleKey, globalProps, builder);

    Path temp = tempFolder.newDir().toPath();
    ProtobufUtil.writeToFile(builder.build(), temp.resolve(StorageManager.MODULE_CONFIGURATION_PB));

    UpdateStatus updateStatus = UpdateStatus.newBuilder()
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(updateStatus, temp.resolve(StorageManager.UPDATE_STATUS_PB));

    Path dest = storageManager.getModuleStorageRoot(moduleKey);
    FileUtils.deleteDirectory(dest);
    FileUtils.forceMkDirs(dest.getParent());
    FileUtils.moveDir(temp, dest);
  }

  private void fetchProjectQualityProfiles(String moduleKey, Set<String> qProfileKeys, ModuleConfiguration.Builder builder) {
    try (InputStream contentStream = wsClient.get("/api/qualityprofiles/search.protobuf?projectKey=" + StringUtils.urlEncode(moduleKey)).contentStream()) {
      SearchWsResponse qpResponse = QualityProfiles.SearchWsResponse.parseFrom(contentStream);
      for (QualityProfile qp : qpResponse.getProfilesList()) {
        String qpKey = qp.getKey();
        if (!qProfileKeys.contains(qpKey)) {
          throw new IllegalStateException(
            "Module '" + moduleKey + "' is associated to quality profile '" + qpKey + "' that is not in storage. Server storage is probably outdated. Please update server.");
        }
        builder.getMutableQprofilePerLanguage().put(qp.getLanguage(), qp.getKey());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load module quality profiles", e);
    }

  }

  private void fetchProjectProperties(String moduleKey, GlobalProperties globalProps, ModuleConfiguration.Builder projectConfigurationBuilder) {
    WsResponse response = wsClient.get("api/properties?format=json&resource=" + StringUtils.urlEncode(moduleKey));
    String responseStr = response.content();
    try (JsonReader reader = new JsonReader(new StringReader(responseStr))) {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginObject();
        parseProperty(globalProps, projectConfigurationBuilder, reader);
        reader.endObject();
      }
      reader.endArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse project properties from: " + response.content(), e);
    }
  }

  private static void parseProperty(GlobalProperties globalProps, ModuleConfiguration.Builder projectConfigurationBuilder, JsonReader reader) throws IOException {
    String key = null;
    String value = null;
    while (reader.hasNext()) {
      String propName = reader.nextName();
      if ("key".equals(propName)) {
        key = reader.nextString();
      } else if ("value".equals(propName)) {
        value = reader.nextString();
      } else {
        reader.skipValue();
      }
    }
    // Storage optimisation: don't store properties having same value than global properties
    if (!globalProps.getProperties().containsKey(key) || !globalProps.getProperties().get(key).equals(value)) {
      projectConfigurationBuilder.getMutableProperties().put(key, value);
    }
  }

}
