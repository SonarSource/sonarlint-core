/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import javax.annotation.Nullable;
import org.slf4j.MDC;

public class SonarLintMDC {

  public static final String CONFIG_SCOPE_ID_MDC_KEY = "configScopeId";

  private SonarLintMDC() {
    // only static methods
  }

  public static void putConfigScopeId(@Nullable String configScopeId) {
    if (configScopeId == null) {
      MDC.remove(CONFIG_SCOPE_ID_MDC_KEY);
    } else {
      MDC.put(CONFIG_SCOPE_ID_MDC_KEY, configScopeId);
    }
  }
}
