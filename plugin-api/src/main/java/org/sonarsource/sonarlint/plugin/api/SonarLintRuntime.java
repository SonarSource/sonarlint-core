/*
 * SonarLint Plugin API
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
package org.sonarsource.sonarlint.plugin.api;

import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.utils.Version;

/**
 * Provides extra runtime related information when in the context of SonarLint.
 *
 * An instance of this class can be accessed through the context passed in {@link org.sonar.api.Plugin.Context#define(Plugin.Context)}.
 * @since 6.0
 */
public interface SonarLintRuntime extends SonarRuntime {
  /**
   * @since 6.0
   * @return the version of the sonarlint-plugin-api
   */
  Version getSonarLintPluginApiVersion();

  /**
   * @since 6.2
   * @return the PID of the client (IDE)
   */
  long getClientPid();
}
