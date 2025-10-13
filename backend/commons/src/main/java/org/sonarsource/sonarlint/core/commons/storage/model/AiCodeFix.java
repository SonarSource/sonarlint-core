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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entity representing AI CodeFix settings persisted in the local storage (H2).
 * This mirrors org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings but
 * lives in commons to avoid cross-module dependencies.
 */
public final class AiCodeFix {
  public enum Enablement {
    DISABLED,
    ENABLED_FOR_SOME_PROJECTS,
    ENABLED_FOR_ALL_PROJECTS
  }

  private final Set<String> supportedRules;
  private final boolean organizationEligible;
  private final Enablement enablement;
  private final Set<String> enabledProjectKeys;

  public AiCodeFix(Set<String> supportedRules, boolean organizationEligible, Enablement enablement, Set<String> enabledProjectKeys) {
    this.supportedRules = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(supportedRules, "supportedRules")));
    this.organizationEligible = organizationEligible;
    this.enablement = Objects.requireNonNull(enablement, "enablement");
    this.enabledProjectKeys = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(enabledProjectKeys, "enabledProjectKeys")));
  }

  public Set<String> getSupportedRules() {
    return supportedRules;
  }

  public boolean isOrganizationEligible() {
    return organizationEligible;
  }

  public Enablement getEnablement() {
    return enablement;
  }

  public Set<String> getEnabledProjectKeys() {
    return enabledProjectKeys;
  }
}
