/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarQubeNotification;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class NotificationChecker {
  private static final Logger LOG = Loggers.get(NotificationChecker.class);
  private static final String API_PATH = "api/developers/search_events";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DateUtils.DATETIME_FORMAT);

  private final SonarLintWsClient wsClient;

  NotificationChecker(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Get all notification events for a set of projects after a given timestamp. 
   * Returns an empty list if an error occurred making the request or parsing the response.
   */
  @CheckForNull
  public List<SonarQubeNotification> request(Map<String, ZonedDateTime> projectTimestamps) {
    String path = getWsPath(projectTimestamps);
    WsResponse wsResponse = wsClient.rawGet(path);
    if (!wsResponse.isSuccessful()) {
      LOG.debug("Failed to get notifications: {}, {}", wsResponse.code(), wsResponse.content());
      return Collections.emptyList();
    }

    return parseResponse(wsResponse.content());
  }

  /**
   * Checks whether a server supports notifications
   */
  public boolean isSupported() {
    String path = getWsPath(Collections.emptyMap());
    WsResponse wsResponse = wsClient.rawGet(path);
    return wsResponse.isSuccessful();
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

  private static List<SonarQubeNotification> parseResponse(String contents) {
    List<SonarQubeNotification> notifications = new ArrayList<>();

    try {
      JsonParser parser = new JsonParser();
      JsonObject root = parser.parse(contents).getAsJsonObject();
      JsonArray events = root.get("events").getAsJsonArray();

      for (JsonElement el : events) {
        JsonObject event = el.getAsJsonObject();
        String category = getOrFail(event, "category");
        String message = getOrFail(event, "message");
        String link = getOrFail(event, "link");
        String projectKey = getOrFail(event, "project");
        String dateTime = getOrFail(event, "date");
        ZonedDateTime time = ZonedDateTime.parse(dateTime, TIME_FORMATTER);
        notifications.add(new DefaultSonarQubeNotification(category, message, link, projectKey, time));
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
