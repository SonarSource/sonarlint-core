/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

public class ToolCallCounter {
  private int success;
  private int error;

  public ToolCallCounter() {
  }

  public ToolCallCounter(int success, int error) {
    this.success = success;
    this.error = error;
  }

  public void incrementCount(boolean succeeded) {
    if (succeeded) {
      success++;
    } else {
      error++;
    }
  }

  public int getSuccess() {
    return success;
  }

  public int getError() {
    return error;
  }
}
