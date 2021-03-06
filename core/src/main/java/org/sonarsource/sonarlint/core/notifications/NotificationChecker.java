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
package org.sonarsource.sonarlint.core.notifications;

import com.google.gson.JsonArray;
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
import javax.annotation.CheckForNull;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.container.model.DefaultServerNotification;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

class NotificationChecker {
  private static final Logger LOG = Loggers.get(NotificationChecker.class);
  private static final String API_PATH = "api/developers/search_events";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DateUtils.DATETIME_FORMAT);

  private final ServerApiHelper serverApiHelper;

  NotificationChecker(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  /**
   * Get all notification events for a set of projects after a given timestamp.
   * Returns an empty list if an error occurred making the request or parsing the response.
   */
  @CheckForNull
  public List<ServerNotification> request(Map<String, ZonedDateTime> projectTimestamps) {
    String path = getWsPath(projectTimestamps);
    try (HttpClient.Response wsResponse = serverApiHelper.rawGet(path)) {
      if (!wsResponse.isSuccessful()) {
        LOG.debug("Failed to get notifications: {}, {}", wsResponse.code(), wsResponse.bodyAsString());
        return Collections.emptyList();
      }

      return parseResponse(wsResponse.bodyAsString());
    }
  }

  /**
   * Checks whether a server supports notifications. Throws exception is the server can't be contacted.
   */
  public boolean isSupported() {
    String path = getWsPath(Collections.emptyMap());
    try (HttpClient.Response wsResponse = serverApiHelper.rawGet(path)) {
      return wsResponse.isSuccessful();
    }
  }

  private static String getWsPath(Map<String, ZonedDateTime> projectTimestamps) {
    StringBuilder builder = new StringBuilder();
    builder.append(API_PATH);

    builder.append("?projects=");
    builder.append(projectTimestamps.keySet().stream()
      .map(StringUtils::urlEncode)
      .collect(Collectors.joining(",")));

    builder.append("&from=");
    builder.append(projectTimestamps.values().stream()
      .map(timestamp -> timestamp.format(TIME_FORMATTER))
      .map(StringUtils::urlEncode)
      .collect(Collectors.joining(",")));

    return builder.toString();
  }

  private static List<ServerNotification> parseResponse(String contents) {
    List<ServerNotification> notifications = new ArrayList<>();

    try {
      JsonObject root = JsonParser.parseString(contents).getAsJsonObject();
      JsonArray events = root.get("events").getAsJsonArray();

      for (JsonElement el : events) {
        JsonObject event = el.getAsJsonObject();
        String category = getOrFail(event, "category");
        String message = getOrFail(event, "message");
        String link = getOrFail(event, "link");
        String projectKey = getOrFail(event, "project");
        String dateTime = getOrFail(event, "date");
        ZonedDateTime time = ZonedDateTime.parse(dateTime, TIME_FORMATTER);
        notifications.add(new DefaultServerNotification(category, message, link, projectKey, time));
      }

    } catch (Exception e) {
      LOG.error("Failed to parse SonarQube notifications response", e);
      return Collections.emptyList();
    }
    return notifications;
  }

  private static String getOrFail(JsonObject parent, String name) {
    JsonElement element = parent.get(name);
    if (element == null) {
      throw new IllegalStateException("Failed to parse response. Missing field '" + name + "'.");
    }
    return element.getAsString();
  }
}
