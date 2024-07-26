/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.HashMap;
import java.util.Map;
import org.sonarsource.sonarlint.core.telemetry.TelemetryFixSuggestionReceivedCounter;

public class TelemetryFixSuggestionReceivedPayload {

  @SerializedName("count_by_fix_suggestion_id")
  private final Map<String, Integer> counters;

  public TelemetryFixSuggestionReceivedPayload(Map<String, TelemetryFixSuggestionReceivedCounter> counters) {
    this.counters = new HashMap<>();
    counters.forEach((link, count) -> this.counters.put(link, count.getFixSuggestionReceivedCount()));
  }

  public Map<String, Integer> getCounters() {
    return counters;
  }
}
