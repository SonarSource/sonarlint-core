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

import com.google.common.collect.ImmutableSet;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties.Builder;

public class GlobalPropertiesDownloader {
  private static final String API_PROPERTIES_PATH = "/api/properties?format=json";
  private static final String BATCH_GLOBAL_PATH = "/batch/global";
  private final SonarLintWsClient wsClient;

  public GlobalPropertiesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public Set<String> fetchGlobalPropertiesTo(Path dest, String serverVersionStr) {
    WsResponse response = wsClient.get(API_PROPERTIES_PATH);
    GlobalProperties.Builder builder = GlobalProperties.newBuilder();

    try (JsonReader reader = new JsonReader(response.contentReader())) {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginObject();
        parseProperty(reader, builder);
        reader.endObject();
      }
      reader.endArray();
      if (needsBatchGlobal(serverVersionStr)) {
        readBatchGlobalTo(builder);
      }
      GlobalProperties globalProperties = builder.build();
      ProtobufUtil.writeToFile(globalProperties, dest.resolve(StorageManager.PROPERTIES_PB));
      return getPluginWhitelist(globalProperties);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse global properties from: " + response.content(), e);
    }
  }

  /**
   * Reads properties from batch global WS without overwriting properties already defined in the builder.
   */
  private void readBatchGlobalTo(Builder builder) {
    WsResponse response = wsClient.get(BATCH_GLOBAL_PATH);
    GlobalRepositories global = GlobalRepositories.fromJson(response.content());

    for (Map.Entry<String, String> e : global.globalSettings.entrySet()) {
      if (!builder.getMutableProperties().containsKey(e.getKey())) {
        builder.getMutableProperties().put(e.getKey(), e.getValue());
      }
    }
  }

  private static void parseProperty(JsonReader reader, GlobalProperties.Builder builder) throws IOException {
    String key = null;
    String value = null;
    while (reader.hasNext()) {
      String propName = reader.nextName();
      switch (propName) {
        case "key":
          key = reader.nextString();
          break;
        case "value":
          value = reader.nextString();
          break;
        default:
          reader.skipValue();
      }
    }
    builder.getMutableProperties().put(key, value);
  }

  private static Set<String> getPluginWhitelist(GlobalProperties globalProperties) {
    if (globalProperties.getProperties().containsKey("sonarlint.plugins.whitelist")) {
      String[] list = globalProperties.getProperties().get("sonarlint.plugins.whitelist").split(",");
      return ImmutableSet.copyOf(list);
    }
    return ImmutableSet.of("java", "javascript", "php", "python", "cobol", "abap", "plsql", "swift");
  }

  private static boolean needsBatchGlobal(String version) {
    return Version.create(version).compareTo(Version.create("5.6")) < 0;
  }

  private static class GlobalRepositories {
    private Map<String, String> globalSettings = new HashMap<>();

    public static GlobalRepositories fromJson(String json) {
      return new GsonBuilder().create().fromJson(json, GlobalRepositories.class);
    }
  }
}
