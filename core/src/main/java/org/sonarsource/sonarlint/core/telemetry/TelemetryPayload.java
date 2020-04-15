/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.time.OffsetDateTime;

/**
 * Models the usage data uploaded
 */
class TelemetryPayload {
  @SerializedName("days_since_installation")
  private final long daysSinceInstallation;

  @SerializedName("days_of_use")
  private final long daysOfUse;

  @SerializedName("sonarlint_version")
  private final String version;

  @SerializedName("sonarlint_product")
  private final String product;

  @SerializedName("ide_version")
  private final String ideVersion;

  @SerializedName("connected_mode_used")
  private final boolean connectedMode;

  @SerializedName("connected_mode_sonarcloud")
  private final boolean connectedModeSonarcloud;

  @SerializedName("system_time")
  private final OffsetDateTime systemTime;

  @SerializedName("install_time")
  private final OffsetDateTime installTime;

  @SerializedName("os")
  private final String os;

  @SerializedName("jre")
  private final String jre;

  @SerializedName("analyses")
  private final TelemetryAnalyzerPerformancePayload[] analyses;

  TelemetryPayload(long daysSinceInstallation, long daysOfUse, String product, String version, String ideVersion, boolean connectedMode, boolean connectedModeSonarcloud,
    OffsetDateTime systemTime, OffsetDateTime installTime, String os, String jre, TelemetryAnalyzerPerformancePayload[] analyses) {
    this.daysSinceInstallation = daysSinceInstallation;
    this.daysOfUse = daysOfUse;
    this.product = product;
    this.version = version;
    this.ideVersion = ideVersion;
    this.connectedMode = connectedMode;
    this.connectedModeSonarcloud = connectedModeSonarcloud;
    this.systemTime = systemTime;
    this.installTime = installTime;
    this.os = os;
    this.jre = jre;
    this.analyses = analyses;
  }

  public long daysSinceInstallation() {
    return daysSinceInstallation;
  }

  public long daysOfUse() {
    return daysOfUse;
  }

  public TelemetryAnalyzerPerformancePayload[] analyses() {
    return analyses;
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

  public boolean connectedModeSonarcloud() {
    return connectedModeSonarcloud;
  }

  public String getOs() {
    return os;
  }

  public String getJre() {
    return jre;
  }

  public OffsetDateTime systemTime() {
    return systemTime;
  }

  public String toJson() {
    Gson gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
      .create();
    return gson.toJson(this);
  }
}
