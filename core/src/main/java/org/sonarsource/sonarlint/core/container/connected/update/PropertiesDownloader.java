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
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class PropertiesDownloader {
  private static final String API_PROPERTIES_PATH = "/api/properties?format=json";
  private final SonarLintWsClient wsClient;

  public PropertiesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchGlobalPropertiesTo(Path dest) {
    ProtobufUtil.writeToFile(fetchGlobalProperties(), dest.resolve(StorageManager.PROPERTIES_PB));
  }

  public GlobalProperties fetchGlobalProperties() {
    GlobalProperties.Builder builder = GlobalProperties.newBuilder();
    fetchProperties(null, (k, v) -> true, builder::putProperties);
    return builder.build();
  }

  public void fetchProjectProperties(String moduleKey, GlobalProperties globalProps, ModuleConfiguration.Builder projectConfigurationBuilder) {
    fetchProperties(moduleKey, (k, v) -> !v.equals(globalProps.getPropertiesMap().get(k)), projectConfigurationBuilder::putProperties);
  }

  private void fetchProperties(@Nullable String moduleKey, BiPredicate<String, String> filter, BiConsumer<String, String> consumer) {
    String url = API_PROPERTIES_PATH;
    if (moduleKey != null) {
      url += "&resource=" + StringUtils.urlEncode(moduleKey);
    }
    WsResponse response = wsClient.get(url);
    try (JsonReader reader = new JsonReader(response.contentReader())) {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginObject();
        parseProperty(filter, consumer, reader);
        reader.endObject();
      }
      reader.endArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse properties from: " + response.content(), e);
    }
  }

  private static void parseProperty(BiPredicate<String, String> filter, BiConsumer<String, String> consumer, JsonReader reader) throws IOException {
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
    // Storage optimisation: don't store properties having same value than global properties
    if (filter.test(key, value)) {
      consumer.accept(key, value);
    }
  }

}
