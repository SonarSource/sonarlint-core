/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class TelemetryNotificationsPayload {
  private final boolean disabled;

  @SerializedName("count_by_type")
  private final Map<String, TelemetryNotificationsCounterPayload> counters;

  public TelemetryNotificationsPayload(boolean disabled, Map<String, TelemetryNotificationsCounterPayload> counters) {
    this.disabled = disabled;
    this.counters = counters;
  }

  public boolean disabled() {
    return disabled;
  }

  public Map<String, TelemetryNotificationsCounterPayload> counters() {
    return counters;
  }

}
