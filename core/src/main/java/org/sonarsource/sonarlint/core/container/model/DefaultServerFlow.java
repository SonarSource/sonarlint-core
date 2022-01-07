/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class DefaultServerFlow implements ServerIssue.Flow {
  private final List<ServerIssueLocation> locations;

  public DefaultServerFlow(List<Location> list) {
    this.locations = list.stream()
      .map(i -> new DefaultServerLocation(i.getPath(),
        i.hasTextRange() ? i.getTextRange() : null,
        i.getMsg(), trimToNull(i.getCodeSnippet())))
      .collect(Collectors.toList());
  }

  @Override
  public List<ServerIssueLocation> locations() {
    return locations;
  }
}
