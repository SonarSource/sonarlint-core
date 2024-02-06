/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UpdateSummary<T> {
  private final Set<UUID> deletedTaintVulnerabilityIds;
  private final List<T> addedTaintVulnerabilities;
  private final List<T> updatedTaintVulnerabilities;

  public UpdateSummary(Set<UUID> deletedTaintVulnerabilityIds, List<T> addedTaintVulnerabilities, List<T> updatedTaintVulnerabilities) {
    this.deletedTaintVulnerabilityIds = deletedTaintVulnerabilityIds;
    this.addedTaintVulnerabilities = addedTaintVulnerabilities;
    this.updatedTaintVulnerabilities = updatedTaintVulnerabilities;
  }

  public Set<UUID> getDeletedTaintVulnerabilityIds() {
    return deletedTaintVulnerabilityIds;
  }

  public List<T> getAddedTaintVulnerabilities() {
    return addedTaintVulnerabilities;
  }

  public List<T> getUpdatedTaintVulnerabilities() {
    return updatedTaintVulnerabilities;
  }

  public boolean hasAnythingChanged() {
    return !deletedTaintVulnerabilityIds.isEmpty() || !addedTaintVulnerabilities.isEmpty() || !updatedTaintVulnerabilities.isEmpty();
  }
}
