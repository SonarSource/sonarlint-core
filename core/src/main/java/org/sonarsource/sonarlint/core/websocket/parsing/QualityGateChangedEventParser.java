/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.websocket.parsing;

import com.google.gson.Gson;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.EventParser;
import org.sonarsource.sonarlint.core.websocket.events.QualityGateChangedEvent;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class QualityGateChangedEventParser implements EventParser<QualityGateChangedEvent> {

  private final Gson gson = new Gson();

  @Override
  public Optional<QualityGateChangedEvent> parse(String jsonData) {
    var payload = gson.fromJson(jsonData, QualityGateChangedEventPayload.class);
    if (payload.isInvalid()) {
      SonarLintLogger.get().error("Invalid payload for 'RuleSetChanged' event: {}", jsonData);
      return Optional.empty();
    }
    return Optional.of(new QualityGateChangedEvent(
      payload.message,
      payload.link,
      payload.project,
      payload.date));
  }

  private static class QualityGateChangedEventPayload {
    private String message;
    private String link;
    private String project;
    private String date;

    private boolean isInvalid() {
      return isBlank(message) || isBlank(link) || isBlank(project) || date == null;
    }
  }
}
