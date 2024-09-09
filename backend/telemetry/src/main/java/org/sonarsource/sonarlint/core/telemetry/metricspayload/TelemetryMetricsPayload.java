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
package org.sonarsource.sonarlint.core.telemetry.metricspayload;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.time.OffsetDateTime;
import java.util.List;
import org.sonarsource.sonarlint.core.telemetry.OffsetDateTimeAdapter;

public class TelemetryMetricsPayload {

  @SerializedName("message_uuid")
  private final String messageUuid;

  @SerializedName("os")
  private final String os;

  @SerializedName("install_time")
  private final OffsetDateTime installTime;

  @SerializedName("sonarlint_product")
  private final String product;

  @SerializedName("dimension")
  private final TelemetryMetricsDimension dimension;

  @SerializedName("metric_values")
  private final List<TelemetryMetricsValue> values;

  public TelemetryMetricsPayload(String messageUuid, String os, OffsetDateTime installTime, String product, TelemetryMetricsDimension dimension,
      List<TelemetryMetricsValue> values) {
    this.messageUuid = messageUuid;
    this.os = os;
    this.installTime = installTime;
    this.product = product;
    this.dimension = dimension;
    this.values = values;
  }

  public String toJson() {
    var gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
      .serializeNulls()
      .create();
    var jsonPayload = gson.toJsonTree(this).getAsJsonObject();
    return gson.toJson(jsonPayload);
  }

  public boolean hasMetrics() {
    return !values.isEmpty();
  }
}
