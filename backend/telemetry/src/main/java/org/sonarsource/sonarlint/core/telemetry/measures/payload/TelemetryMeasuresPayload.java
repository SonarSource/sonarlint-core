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

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.time.OffsetDateTime;
import java.util.List;
import org.sonarsource.sonarlint.core.commons.storage.adapter.OffsetDateTimeAdapter;

public record TelemetryMeasuresPayload(@SerializedName("message_uuid") String messageUuid,
                                       @SerializedName("os") String os,
                                       @SerializedName("install_time") OffsetDateTime installTime,
                                       @SerializedName("sonarlint_product") String product,
                                       @SerializedName("dimension") TelemetryMeasuresDimension dimension,
                                       @SerializedName("metric_values") List<TelemetryMeasuresValue> values) {

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
