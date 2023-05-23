/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.events.hotspot;

import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventHandler;

public class UpdateStorageOnSecurityHotspotChanged implements ServerEventHandler<SecurityHotspotChangedEvent> {
  private final ConnectionStorage storage;

  public UpdateStorageOnSecurityHotspotChanged(ConnectionStorage storage) {
    this.storage = storage;
  }
  @Override
  public void handle(SecurityHotspotChangedEvent event) {
    var hotspotKey = event.getHotspotKey();
    var projectKey = event.getProjectKey();
    var status = event.getStatus();
    var assignee = event.getAssignee();
    update(hotspotKey, projectKey, assignee, status);
  }

  private void update(String hotspotKey, String projectKey, String assignee, HotspotReviewStatus status) {
    storage.project(projectKey).findings().updateHotspot(hotspotKey, hotspot -> {
      if(status != null) {
        hotspot.setStatus(status);
      }
      if(assignee != null) {
        hotspot.setAssignee(assignee);
      }
    });
  }
}
