/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.progress;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ProgressUpdateNotification {
  /**
   * If the message is null, the previously set non-null message should be re-used by the client.
   */
  private final String message;
  /**
   * If the progress is indeterminate, pass null.
   * If non-null is passed and the progress was indeterminate, the client should set it to determinate.
   */
  private final Integer percentage;

  public ProgressUpdateNotification(@Nullable String message, @Nullable Integer percentage) {
    this.message = message;
    this.percentage = percentage;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  @CheckForNull
  public Integer getPercentage() {
    return percentage;
  }
}
