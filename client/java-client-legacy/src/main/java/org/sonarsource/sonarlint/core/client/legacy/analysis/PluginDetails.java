/*
 * SonarLint Core - Java Client Legacy
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason;

public class PluginDetails {
  private final String key;
  private final String name;
  private final String version;
  private final SkipReason skipReason;

  public PluginDetails(String key, String name, @Nullable String version, @Nullable SkipReason skipReason) {
    this.key = key;
    this.name = name;
    this.version = version;
    this.skipReason = skipReason;
  }

  public String key() {
    return key;
  }

  public String name() {
    return name;
  }

  @CheckForNull
  public String version() {
    return version;
  }

  public Optional<SkipReason> skipReason() {
    return Optional.ofNullable(skipReason);
  }

}
