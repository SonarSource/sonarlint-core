/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.serverapi.settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.stream.Collectors.joining;

public class SettingsApi {
  private static final Logger LOG = Loggers.get(SettingsApi.class);
  private static final String API_SETTINGS_PATH = "/api/settings/values.protobuf";

  private final ServerApiHelper helper;

  public SettingsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Sonarlint.GlobalProperties getGlobalSettings() {
    Sonarlint.GlobalProperties.Builder builder = Sonarlint.GlobalProperties.newBuilder();
    getSettings(null, builder::putProperties);
    return builder.build();
  }

  public void getProjectSettings(String projectKey, Sonarlint.ProjectConfiguration.Builder projectConfigurationBuilder) {
    getSettings(projectKey, projectConfigurationBuilder::putProperties);
  }

  private void getSettings(@Nullable String projectKey, BiConsumer<String, String> consumer) {
    StringBuilder url = new StringBuilder();
    url.append(API_SETTINGS_PATH);
    if (projectKey != null) {
      url.append("?component=").append(StringUtils.urlEncode(projectKey));
    }
    ServerApiHelper.consumeTimed(
      () -> helper.get(url.toString()),
      response -> {
        try (InputStream is = response.bodyAsStream()) {
          Settings.ValuesWsResponse values = Settings.ValuesWsResponse.parseFrom(is);
          for (Settings.Setting s : values.getSettingsList()) {
            // Storage optimisation: don't store settings having same value than global settings
            if (!s.getInherited()) {
              processSetting(consumer, s);
            }
          }
        } catch (IOException e) {
          throw new IllegalStateException("Unable to parse properties from: " + response.bodyAsString(), e);
        }
      },
      duration -> LOG.info("Downloaded settings in {}ms", duration));
  }

  private static void processSetting(BiConsumer<String, String> consumer, Settings.Setting s) {
    switch (s.getValueOneOfCase()) {
      case VALUE:
        consumer.accept(s.getKey(), s.getValue());
        break;
      case VALUES:
        consumer.accept(s.getKey(), s.getValues().getValuesList().stream().collect(joining(",")));
        break;
      case FIELDVALUES:
        processPropertySet(s, consumer);
        break;
      default:
        throw new IllegalStateException("Unknow property value for " + s.getKey());
    }
  }

  private static void processPropertySet(Settings.Setting s, BiConsumer<String, String> consumer) {
    List<String> ids = new ArrayList<>();
    int id = 1;
    for (Settings.FieldValues.Value v : s.getFieldValues().getFieldValuesList()) {
      for (Map.Entry<String, String> entry : v.getValue().entrySet()) {
        consumer.accept(s.getKey() + "." + id + "." + entry.getKey(), entry.getValue());
      }
      ids.add(String.valueOf(id));
      id++;
    }
    consumer.accept(s.getKey(), ids.stream().collect(Collectors.joining(",")));
  }


}
