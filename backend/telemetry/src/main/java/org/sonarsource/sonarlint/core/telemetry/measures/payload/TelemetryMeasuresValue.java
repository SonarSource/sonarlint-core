/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.measures.payload;

import com.google.gson.annotations.SerializedName;

public class TelemetryMeasuresValue {

  private static final String KEY_PATTERN = "^([a-z_][a-z0-9_]{1,126}\\.)[a-z_][a-z0-9_]{1,126}$";

  @SerializedName("key")
  private final String key;

  @SerializedName("value")
  private final String value;

  @SerializedName("type")
  private final TelemetryMeasuresValueType type;

  @SerializedName("granularity")
  private final TelemetryMeasuresValueGranularity granularity;

  public TelemetryMeasuresValue(String key, String value, TelemetryMeasuresValueType type, TelemetryMeasuresValueGranularity granularity) {
    this.key = validateKey(key);
    this.value = value;
    this.type = type;
    this.granularity = granularity;
  }

  /*
   * From the telemetry measures specification:
   *  - Entire key: ^([a-z_][a-z0-9_]{1,126}\.)[a-z_][a-z0-9_]{1,126}$
   *  - Group name: ^[a-z_][a-z0-9_]{1,126}\.$
   *  - Measure name: ^[a-z_][a-z0-9_]{1,126}$
   */
  private static String validateKey(String maybeKey) throws IllegalArgumentException {
    if (maybeKey.matches(KEY_PATTERN)) {
      return maybeKey;
    }
    throw new IllegalArgumentException("Invalid measure key: " + maybeKey);
  }
}
