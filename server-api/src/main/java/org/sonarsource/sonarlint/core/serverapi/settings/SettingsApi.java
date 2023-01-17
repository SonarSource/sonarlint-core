/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;

public class SettingsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String API_SETTINGS_PATH = "/api/settings/values.protobuf";

  private final ServerApiHelper helper;

  public SettingsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Map<String, String> getProjectSettings(String projectKey) {
    Map<String, String> settings = new HashMap<>();
    var url = new StringBuilder();
    url.append(API_SETTINGS_PATH);
    url.append("?component=").append(UrlUtils.urlEncode(projectKey));
    ServerApiHelper.consumeTimed(
      () -> helper.get(url.toString()),
      response -> {
        try (var is = response.bodyAsStream()) {
          var values = Settings.ValuesWsResponse.parseFrom(is);
          for (Settings.Setting s : values.getSettingsList()) {
            processSetting(settings::put, s);
          }
        } catch (IOException e) {
          throw new IllegalStateException("Unable to parse properties from: " + response.bodyAsString(), e);
        }
      },
      duration -> LOG.info("Downloaded settings in {}ms", duration));
    return settings;
  }

  private static void processSetting(BiConsumer<String, String> consumer, Settings.Setting s) {
    switch (s.getValueOneOfCase()) {
      case VALUE:
        consumer.accept(s.getKey(), s.getValue());
        break;
      case VALUES:
        consumer.accept(s.getKey(), String.join(",", s.getValues().getValuesList()));
        break;
      case FIELDVALUES:
        processPropertySet(s, consumer);
        break;
      default:
        throw new IllegalStateException("Unknown property value for " + s.getKey());
    }
  }

  private static void processPropertySet(Settings.Setting s, BiConsumer<String, String> consumer) {
    List<String> ids = new ArrayList<>();
    var id = 1;
    for (Settings.FieldValues.Value v : s.getFieldValues().getFieldValuesList()) {
      for (Map.Entry<String, String> entry : v.getValue().entrySet()) {
        consumer.accept(s.getKey() + "." + id + "." + entry.getKey(), entry.getValue());
      }
      ids.add(String.valueOf(id));
      id++;
    }
    consumer.accept(s.getKey(), String.join(",", ids));
  }

}
