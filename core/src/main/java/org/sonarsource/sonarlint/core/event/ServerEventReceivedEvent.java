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
package org.sonarsource.sonarlint.core.event;

import org.sonarsource.sonarlint.core.commons.push.ServerEvent;

public class ServerEventReceivedEvent {
  private final String connectionid;
  private final ServerEvent event;

  public ServerEventReceivedEvent(String connectionid, ServerEvent event) {
    this.connectionid = connectionid;
    this.event = event;
  }

  public String getConnectionid() {
    return connectionid;
  }

  public ServerEvent getEvent() {
    return event;
  }
}
