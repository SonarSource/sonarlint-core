/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.smartnotifications;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.sonarsource.sonarlint.core.serverconnection.repository.SmartNotificationsRepository;

public class LastEventPolling {

  private final SmartNotificationsRepository smartNotificationsRepository;

  public LastEventPolling(SmartNotificationsRepository smartNotificationsRepository) {
    this.smartNotificationsRepository = smartNotificationsRepository;
  }

  public ZonedDateTime getLastEventPolling(String connectionId, String projectKey) {
    var lastEventPollingEpoch = smartNotificationsRepository.readLastEventPolling(connectionId, projectKey);
    return lastEventPollingEpoch.map(aLong -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault()))
      .orElseGet(ZonedDateTime::now);
  }

  public void setLastEventPolling(ZonedDateTime dateTime, String connectionId, String projectKey) {
    var lastEventPolling = smartNotificationsRepository.readLastEventPolling(connectionId, projectKey);
    var dateTimeEpoch = dateTime.toInstant().toEpochMilli();
    if (lastEventPolling.isPresent() && dateTimeEpoch <= lastEventPolling.get()) {
      // this can happen if the settings changed between the read and write
      return;
    }
    smartNotificationsRepository.store(connectionId, projectKey, dateTimeEpoch);
  }

}
