/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;

public class PluginRequirementsCheckResult {

  private final PluginInfo plugin;

  @CheckForNull
  private final SkipReason skipReason;

  public PluginRequirementsCheckResult(PluginInfo plugin, @Nullable SkipReason skipReason) {
    this.plugin = plugin;
    this.skipReason = skipReason;
  }

  public PluginInfo getPlugin() {
    return plugin;
  }

  public Optional<SkipReason> getSkipReason() {
    return Optional.ofNullable(skipReason);
  }

  public boolean isSkipped() {
    return skipReason != null;
  }

}
