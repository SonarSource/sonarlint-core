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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.telemetry.OffsetDateTimeAdapter;

/**
 * Models the usage data uploaded
 */
public class TelemetryPayload {
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

  @SerializedName("platform")
  private final String platform;

  @SerializedName("architecture")
  private final String architecture;

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

  @SerializedName("nodejs")
  private final String nodejs;

  @SerializedName("analyses")
  private final TelemetryAnalyzerPerformancePayload[] analyses;

  @SerializedName("server_notifications")
  private final TelemetryNotificationsPayload notifications;

  @SerializedName("show_hotspot")
  private final ShowHotspotPayload showHotspotPayload;

  @SerializedName("taint_vulnerabilities")
  private final TaintVulnerabilitiesPayload taintVulnerabilitiesPayload;

  @SerializedName("rules")
  private final TelemetryRulesPayload telemetryRulesPayload;

  @SerializedName("hotspot")
  private final HotspotPayload hotspotPayload;

  private final transient Map<String, Object> additionalAttributes;

  public TelemetryPayload(long daysSinceInstallation, long daysOfUse, String product, String version, String ideVersion, @Nullable String platform, @Nullable String architecture,
    boolean connectedMode, boolean connectedModeSonarcloud, OffsetDateTime systemTime, OffsetDateTime installTime, String os, String jre, @Nullable String nodejs,
    TelemetryAnalyzerPerformancePayload[] analyses, TelemetryNotificationsPayload notifications, ShowHotspotPayload showHotspotPayload,
    TaintVulnerabilitiesPayload taintVulnerabilitiesPayload, TelemetryRulesPayload telemetryRulesPayload, HotspotPayload hotspotPayload, Map<String, Object> additionalAttributes) {
    this.daysSinceInstallation = daysSinceInstallation;
    this.daysOfUse = daysOfUse;
    this.product = product;
    this.version = version;
    this.ideVersion = ideVersion;
    this.platform = platform;
    this.architecture = architecture;
    this.connectedMode = connectedMode;
    this.connectedModeSonarcloud = connectedModeSonarcloud;
    this.systemTime = systemTime;
    this.installTime = installTime;
    this.os = os;
    this.jre = jre;
    this.nodejs = nodejs;
    this.analyses = analyses;
    this.notifications = notifications;
    this.showHotspotPayload = showHotspotPayload;
    this.taintVulnerabilitiesPayload = taintVulnerabilitiesPayload;
    this.telemetryRulesPayload = telemetryRulesPayload;
    this.hotspotPayload = hotspotPayload;
    this.additionalAttributes = additionalAttributes;
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

  public String os() {
    return os;
  }

  public String jre() {
    return jre;
  }

  public String nodejs() {
    return nodejs;
  }

  public OffsetDateTime systemTime() {
    return systemTime;
  }

  public TelemetryNotificationsPayload notifications() {
    return notifications;
  }

  public Map<String, Object> additionalAttributes() {
    return additionalAttributes;
  }

  public String toJson() {
    var gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
      .create();
    var jsonPayload = gson.toJsonTree(this).getAsJsonObject();
    var jsonAdditional = gson.toJsonTree(additionalAttributes, new TypeToken<Map<String, Object>>() {
    }.getType()).getAsJsonObject();
    return gson.toJson(mergeObjects(jsonAdditional, jsonPayload));
  }

  static JsonObject mergeObjects(JsonObject source, JsonObject target) {
    for (Entry<String, JsonElement> entry : source.entrySet()) {
      var value = entry.getValue();
      if (!target.has(entry.getKey())) {
        // new value for "key":
        target.add(entry.getKey(), value);
      } else if (value.isJsonObject()) {
        // existing value for "key" - recursively deep merge:
        var valueJson = (JsonObject) value;
        mergeObjects(valueJson, target.getAsJsonObject(entry.getKey()));
      }
      // Don't override value if it already exists in the target
    }
    return target;
  }
}
