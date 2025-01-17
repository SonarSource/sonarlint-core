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
package org.sonarsource.sonarlint.core.commons;

import java.util.Objects;

public class Binding {

  private final String connectionId;
  private final String sonarProjectKey;

  public Binding(String connectionId, String sonarProjectKey) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getSonarProjectKey() {
    return sonarProjectKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var binding = (Binding) o;
    return Objects.equals(connectionId, binding.connectionId) && Objects.equals(sonarProjectKey, binding.sonarProjectKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, sonarProjectKey);
  }

  @Override
  public String toString() {
    return "Binding{" +
      "connectionId='" + connectionId + '\'' +
      ", sonarProjectKey='" + sonarProjectKey + '\'' +
      '}';
  }
}
