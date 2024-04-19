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
package org.sonarsource.sonarlint.core.fs;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemUpdatedEvent {

  private final List<ClientFile> removed;
  private final List<ClientFile> added;
  private final List<ClientFile> updated;

  public FileSystemUpdatedEvent(List<ClientFile> removed, List<ClientFile> added, List<ClientFile> updated) {
    this.removed = removed;
    this.added = added;
    this.updated = updated;
  }

  public List<ClientFile> getRemoved() {
    return removed;
  }

  public List<ClientFile> getAdded() {
    return added;
  }

  public List<ClientFile> getUpdated() {
    return updated;
  }

  public List<ClientFile> getAddedOrUpdated() {
    return Stream.concat(getAdded().stream(), getUpdated().stream())
      .collect(Collectors.toList());
  }

}
