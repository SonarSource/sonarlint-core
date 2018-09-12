/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import java.util.Date;

import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;

public class DefaultProjectStorageStatus implements ProjectStorageStatus {
  private final Date lastUpdateDate;
  private final boolean stale;

  public DefaultProjectStorageStatus(Date lastUpdateDate, boolean stale) {
    this.lastUpdateDate = lastUpdateDate;
    this.stale = stale;
  }

  @Override
  public Date getLastUpdateDate() {
    return lastUpdateDate;
  }

  @Override
  public boolean isStale() {
    return stale;
  }
}
