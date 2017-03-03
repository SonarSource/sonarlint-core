/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class TelemetryPayload {
  @SerializedName("days_since_installation")
  long daysSinceInstallation;

  @SerializedName("days_of_use")
  long daysOfUse;

  @SerializedName("sonarlint_version")
  String version;

  @SerializedName("sonarlint_product")
  String product;

  @SerializedName("connected_mode_used")
  boolean connectedMode;

  public TelemetryPayload(long daysSinceInstallation, long daysOfUse, String product, String version, boolean connectedMode) {
    this.daysSinceInstallation = daysSinceInstallation;
    this.daysOfUse = daysOfUse;
    this.product = product;
    this.version = version;
    this.connectedMode = connectedMode;
  }

  public long daysSinceInstallation() {
    return daysSinceInstallation;
  }

  public long daysOfUse() {
    return daysOfUse;
  }

  public String version() {
    return version;
  }

  public String product() {
    return product;
  }

  public boolean connectedMode() {
    return connectedMode;
  }

  public String toJson() {
    Gson gson = new Gson();
    return gson.toJson(this);
  }
}
