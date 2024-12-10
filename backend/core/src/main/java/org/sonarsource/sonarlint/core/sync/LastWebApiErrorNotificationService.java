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
package org.sonarsource.sonarlint.core.sync;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class LastWebApiErrorNotificationService {
  private final StorageService storage;

  public LastWebApiErrorNotificationService(StorageService storage) {
    this.storage = storage;
  }

  public void setLastWebApiErrorNotification(String connectionId, ZonedDateTime lastErrorNotificationTime) {
    storage.connection(connectionId).webApiErrorNotifications().store(lastErrorNotificationTime.toInstant().toEpochMilli());
  }

  @CheckForNull
  public ZonedDateTime getLastWebApiErrorNotification(String connectionId) {
    return storage.getStorageFacade().connection(connectionId)
      .webApiErrorNotifications().readLastWebApiErrorNotification()
      .map(aLong -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault())).orElse(null);
  }
}
