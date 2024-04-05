/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

/**
 * Telemetry issues are silently ignored to not annoy users. In order to ease detection of issues, people (usually SonarSourcers) can define
 * this env variable to see telemetry errors in their logs.
 *
 */
public class InternalDebug {

  static final String INTERNAL_DEBUG_ENV = "SONARLINT_INTERNAL_DEBUG";

  private static boolean isEnabled = "true".equals(System.getenv(INTERNAL_DEBUG_ENV));

  private InternalDebug() {
    // utility class, forbidden constructor
  }

  public static boolean isEnabled() {
    return isEnabled;
  }

  // For testing
  public static void setEnabled(boolean isEnabled) {
    InternalDebug.isEnabled = isEnabled;
  }
}
