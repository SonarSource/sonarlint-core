/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class DevelopersApi {
  private static final String API_PATH = "api/developers/search_events";
  public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);

  private final ServerApiHelper helper;

  public DevelopersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchEventsResponseDto searchEvents(Map<String, ZonedDateTime> projectTimestamps, SonarLintCancelMonitor cancelMonitor) {
    return helper.getJson(getWsPath(projectTimestamps), SearchEventsResponseDto.class, cancelMonitor);
  }

  private static String getWsPath(Map<String, ZonedDateTime> projectTimestamps) {
    // Sort project keys to simplify testing
    var sortedProjectKeys = projectTimestamps.keySet().stream().sorted().toList();
    var builder = new StringBuilder();
    builder.append(API_PATH);
    builder.append("?projects=");
    builder.append(sortedProjectKeys.stream()
      .map(UrlUtils::urlEncode)
      .collect(Collectors.joining(",")));

    builder.append("&from=");
    builder.append(sortedProjectKeys.stream()
      .map(projectTimestamps::get)
      .map(timestamp -> timestamp.format(TIME_FORMATTER))
      .map(UrlUtils::urlEncode)
      .collect(Collectors.joining(",")));

    return builder.toString();
  }
}
