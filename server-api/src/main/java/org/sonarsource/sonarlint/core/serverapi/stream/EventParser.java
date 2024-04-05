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
package org.sonarsource.sonarlint.core.serverapi.stream;

import java.util.List;

public class EventParser {
  private static final String EVENT_TYPE_PREFIX = "event: ";
  private static final String DATA_PREFIX = "data: ";

  static Event parse(String eventPayload) {
    var fields = List.of(eventPayload.split("\\n"));
    var type = "";
    var data = new StringBuilder();
    for (String field : fields) {
      if (field.startsWith(EVENT_TYPE_PREFIX)) {
        type = field.substring(EVENT_TYPE_PREFIX.length());
      } else if (field.startsWith(DATA_PREFIX)) {
        data.append(field.substring(DATA_PREFIX.length()));
      }
    }
    return new Event(type, data.toString());
  }

  private EventParser() {
    // static only
  }
}
