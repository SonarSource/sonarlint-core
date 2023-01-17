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
package org.sonarsource.sonarlint.core.serverapi.developers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class DevelopersApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String API_PATH = "api/developers/search_events";
  public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);

  private final ServerApiHelper helper;

  public DevelopersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public boolean isSupported() {
    var path = getWsPath(Collections.emptyMap());
    try (var wsResponse = helper.rawGet(path)) {
      return wsResponse.isSuccessful();
    }
  }

  public List<Event> getEvents(Map<String, ZonedDateTime> projectTimestamps) {
    var path = getWsPath(projectTimestamps);
    try (var wsResponse = helper.rawGet(path)) {
      if (!wsResponse.isSuccessful()) {
        LOG.debug("Failed to get notifications: {}, {}", wsResponse.code(), wsResponse.bodyAsString());
        return Collections.emptyList();
      }

      return parseResponse(wsResponse.bodyAsString());
    }
  }

  private static List<Event> parseResponse(String contents) {
    List<Event> notifications = new ArrayList<>();

    try {
      var root = JsonParser.parseString(contents).getAsJsonObject();
      var events = root.get("events").getAsJsonArray();

      for (JsonElement el : events) {
        var event = el.getAsJsonObject();
        var category = getOrFail(event, "category");
        var message = getOrFail(event, "message");
        var link = getOrFail(event, "link");
        var projectKey = getOrFail(event, "project");
        var dateTime = getOrFail(event, "date");
        var time = ZonedDateTime.parse(dateTime, TIME_FORMATTER);
        notifications.add(new Event(category, message, link, projectKey, time));
      }

    } catch (Exception e) {
      LOG.error("Failed to parse SonarQube notifications response", e);
      return Collections.emptyList();
    }
    return notifications;
  }

  private static String getOrFail(JsonObject parent, String name) {
    var element = parent.get(name);
    if (element == null) {
      throw new IllegalStateException("Failed to parse response. Missing field '" + name + "'.");
    }
    return element.getAsString();
  }

  private static String getWsPath(Map<String, ZonedDateTime> projectTimestamps) {
    var builder = new StringBuilder();
    builder.append(API_PATH);
    builder.append("?projects=");
    builder.append(projectTimestamps.keySet().stream()
      .map(UrlUtils::urlEncode)
      .collect(Collectors.joining(",")));

    builder.append("&from=");
    builder.append(projectTimestamps.values().stream()
      .map(timestamp -> timestamp.format(TIME_FORMATTER))
      .map(UrlUtils::urlEncode)
      .collect(Collectors.joining(",")));

    return builder.toString();
  }
}
