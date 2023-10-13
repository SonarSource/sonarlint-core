/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.time.Instant;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;

public class SecurityHotspotChangedEvent implements ServerHotspotEvent {
  private final String hotspotKey;
  private final String projectKey;
  private final Instant updateDate;
  private final HotspotReviewStatus status;
  private final String assignee;
  private final String filePath;

  public SecurityHotspotChangedEvent(String hotspotKey, String projectKey, Instant updateDate, HotspotReviewStatus status, String assignee, String filePath) {
    this.hotspotKey = hotspotKey;
    this.projectKey = projectKey;
    this.updateDate = updateDate;
    this.status = status;
    this.assignee = assignee;
    this.filePath = filePath;
  }

  public String getHotspotKey() {
    return hotspotKey;
  }

  @Override
  public String getProjectKey() {
    return projectKey;
  }

  public Instant getUpdateDate() {
    return updateDate;
  }

  public HotspotReviewStatus getStatus() {
    return status;
  }

  public String getAssignee() {
    return assignee;
  }

  @Override
  public String getFilePath() {
    return filePath;
  }
}
