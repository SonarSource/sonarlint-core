/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.client.api.connected;

import javax.annotation.CheckForNull;

import java.util.Date;

public interface GlobalUpdateStatus {
  /**
   * A String representing the server version last time the storage was updated
   * @return Server version or null if storage is stale.
   */
  @CheckForNull
  String getServerVersion();

  /**
   * Last time the module data was written.
   * @return A Date when it was updated, never null.
   */
  Date getLastUpdateDate();
  
  /**
   * Returns true if the storage was created with a different version of SonarLint Core. 
   * An update is needed to ensure data format compatibility.
   * @return whether data is invalid and needs to be updated.
   */
  boolean isStale();

}
