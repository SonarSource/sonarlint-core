/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.grip;

import java.util.UUID;
import javax.annotation.Nullable;

public class FixSuggestion {
  private final UUID correlationId;
  private final String ruleKey;
  private final long responseTime;
  private final String before;
  private final String after;

  public FixSuggestion(UUID correlationId, String ruleKey, long responseTime, @Nullable String before, @Nullable String after) {
    this.correlationId = correlationId;
    this.ruleKey = ruleKey;
    this.responseTime = responseTime;
    this.before = before;
    this.after = after;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public long getResponseTime() {
    return responseTime;
  }

  public String getBefore() {
    return before;
  }

  public String getAfter() {
    return after;
  }
}
