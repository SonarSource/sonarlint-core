/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;

public class EventDispatcher implements ServerEventHandler<ServerEvent> {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Map<Class<?>, List<ServerEventHandler>> routes = new HashMap<>();

  public <E extends ServerEvent> EventDispatcher dispatch(Class<E> eventType, ServerEventHandler<E> handler) {
    routes.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    return this;
  }

  @Override
  public void handle(ServerEvent event) {
    Class<? extends ServerEvent> eventType = event.getClass();
    if (routes.containsKey(eventType)) {
      routes.get(eventType).forEach(handler -> handler.handle(event));
    } else {
      LOG.error("No handler for event '{}'", eventType);
    }
  }
}
