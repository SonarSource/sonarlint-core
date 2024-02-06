/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import java.util.UUID;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;

public class ServerMatchedSecurityHotspotDto {
  private final UUID id;
  private final String serverKey;
  private final long introductionDate;
  private final HotspotStatus status;
  private final boolean isOnNewCode;

  public ServerMatchedSecurityHotspotDto(UUID id, String serverKey, long introductionDate, HotspotStatus status, boolean isOnNewCode) {
    this.id = id;
    this.serverKey = serverKey;
    this.introductionDate = introductionDate;
    this.status = status;
    this.isOnNewCode = isOnNewCode;
  }

  public UUID getId() {
    return id;
  }

  public String getServerKey() {
    return serverKey;
  }

  public long getIntroductionDate() {
    return introductionDate;
  }

  public HotspotStatus getStatus() {
    return status;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }

}
