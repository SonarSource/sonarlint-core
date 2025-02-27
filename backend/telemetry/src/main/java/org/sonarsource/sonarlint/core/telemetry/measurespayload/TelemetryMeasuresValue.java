/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry.measurespayload;

import com.google.gson.annotations.SerializedName;

public class TelemetryMeasuresValue {

  @SerializedName("key")
  private final String key;

  @SerializedName("value")
  private final String value;

  @SerializedName("type")
  private final TelemetryMeasuresValueType type;

  @SerializedName("granularity")
  private final TelemetryMeasuresValueGranularity granularity;

  public TelemetryMeasuresValue(String key, String value, TelemetryMeasuresValueType type, TelemetryMeasuresValueGranularity granularity) {
    this.key = key;
    this.value = value;
    this.type = type;
    this.granularity = granularity;
  }
}
