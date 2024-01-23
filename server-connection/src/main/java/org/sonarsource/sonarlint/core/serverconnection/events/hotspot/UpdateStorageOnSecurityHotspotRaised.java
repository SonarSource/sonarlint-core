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
package org.sonarsource.sonarlint.core.serverconnection.events.hotspot;

import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventHandler;

import static org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityRaised.adapt;

public class UpdateStorageOnSecurityHotspotRaised implements ServerEventHandler<SecurityHotspotRaisedEvent> {
  private final ConnectionStorage storage;

  public UpdateStorageOnSecurityHotspotRaised(ConnectionStorage storage) {
    this.storage = storage;
  }
  @Override
  public void handle(SecurityHotspotRaisedEvent event) {
    var hotspot = new ServerHotspot(
      event.getHotspotKey(),
      event.getRuleKey(),
      event.getMainLocation().getMessage(),
      event.getMainLocation().getFilePath(),
      adapt(event.getMainLocation().getTextRange()),
      event.getCreationDate(),
      event.getStatus(),
      event.getVulnerabilityProbability(),
      null
    );
    storage.project(event.getProjectKey()).findings().insert(event.getBranch(), hotspot);
  }
}
