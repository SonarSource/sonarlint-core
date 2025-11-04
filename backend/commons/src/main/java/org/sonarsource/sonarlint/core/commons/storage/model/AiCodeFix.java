/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Entity representing AI CodeFix settings persisted in the local storage (H2).
 * This mirrors org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings but
 * lives in commons to avoid cross-module dependencies.
 */
public record AiCodeFix(
  String connectionId,
  String[] supportedRules,
  boolean organizationEligible,
  Enablement enablement,
  String[] enabledProjectKeys
) {

  public AiCodeFix(String connectionId, Collection<String> supportedRules, boolean organizationEligible, Enablement enablement, Collection<String> enabledProjectKeys) {
    this(connectionId, supportedRules.toArray(String[]::new), organizationEligible, enablement, enabledProjectKeys.toArray(String[]::new));
  }

  public enum Enablement {
    DISABLED,
    ENABLED_FOR_ALL_PROJECTS,
    ENABLED_FOR_SOME_PROJECTS
  }

  public AiCodeFix {
    Objects.requireNonNull(connectionId, "connectionId");
    Objects.requireNonNull(supportedRules, "supportedRules");
    Objects.requireNonNull(enablement, "enablement");
    Objects.requireNonNull(enabledProjectKeys, "enabledProjectKeys");
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    var aiCodeFix = (AiCodeFix) o;
    return organizationEligible == aiCodeFix.organizationEligible && Objects.equals(connectionId, aiCodeFix.connectionId) && enablement == aiCodeFix.enablement
      && Objects.deepEquals(supportedRules, aiCodeFix.supportedRules) && Objects.deepEquals(enabledProjectKeys, aiCodeFix.enabledProjectKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, Arrays.hashCode(supportedRules), organizationEligible, enablement, Arrays.hashCode(enabledProjectKeys));
  }

  @Override
  public String toString() {
    return "AiCodeFix{" +
      "connectionId='" + connectionId + '\'' +
      ", supportedRules=" + Arrays.toString(supportedRules) +
      ", organizationEligible=" + organizationEligible +
      ", enablement=" + enablement +
      ", enabledProjectKeys=" + Arrays.toString(enabledProjectKeys) +
      '}';
  }
}
