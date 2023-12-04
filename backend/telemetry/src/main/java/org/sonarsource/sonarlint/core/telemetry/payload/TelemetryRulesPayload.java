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
import java.util.Collection;

public class TelemetryRulesPayload {

  @SerializedName("non_default_enabled")
  private final Collection<String> nonDefaultEnabled;
  @SerializedName("default_disabled")
  private final Collection<String> defaultDisabled;
  @SerializedName("raised_issues")
  private final Collection<String> raisedIssues;
  @SerializedName("quick_fix_applied")
  private final Collection<String> quickFixesApplied;

  public TelemetryRulesPayload(Collection<String> nonDefaultEnabled, Collection<String> defaultDisabled, Collection<String> raisedIssues, Collection<String> quickFixesApplied) {
    this.nonDefaultEnabled = nonDefaultEnabled;
    this.defaultDisabled = defaultDisabled;
    this.raisedIssues = raisedIssues;
    this.quickFixesApplied = quickFixesApplied;
  }

}
