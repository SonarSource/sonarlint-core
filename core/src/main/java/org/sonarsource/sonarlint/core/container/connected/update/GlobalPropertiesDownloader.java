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
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

public class GlobalPropertiesDownloader {

  private final SonarLintWsClient wsClient;

  public GlobalPropertiesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public Set<String> fetchGlobalPropertiesTo(Path dest) {
    WsResponse response = wsClient.get("api/properties?format=json");
    try (JsonReader reader = new JsonReader(response.contentReader())) {
      GlobalProperties.Builder builder = GlobalProperties.newBuilder();
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginObject();
        parseProperty(reader, builder);
        reader.endObject();
      }
      reader.endArray();
      GlobalProperties globalProperties = builder.build();
      ProtobufUtil.writeToFile(globalProperties, dest.resolve(StorageManager.PROPERTIES_PB));
      return getPluginWhitelist(globalProperties);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse global properties from: " + response.content(), e);
    }
  }

  private static void parseProperty(JsonReader reader, GlobalProperties.Builder builder) throws IOException {
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
    builder.getMutableProperties().put(key, value);
  }

  private static Set<String> getPluginWhitelist(GlobalProperties globalProperties) {
    if (globalProperties.getProperties().containsKey("sonarlint.plugins.whitelist")) {
      String[] list = globalProperties.getProperties().get("sonarlint.plugins.whitelist").split(",");
      return ImmutableSet.copyOf(list);
    }
    return ImmutableSet.of("java", "javascript", "php", "python");
  }

}
