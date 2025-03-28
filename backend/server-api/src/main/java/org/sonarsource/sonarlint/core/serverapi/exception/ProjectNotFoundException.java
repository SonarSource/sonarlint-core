/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.exception;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.SonarLintException;

public class ProjectNotFoundException extends SonarLintException {

  public ProjectNotFoundException(String moduleKey, @Nullable String organizationKey) {
    super(formatMessage(moduleKey, organizationKey), null);
  }

  private static String formatMessage(String moduleKey, @Nullable String organizationKey) {
    if (organizationKey != null) {
      return String.format("Project with key '%s' in organization '%s' not found on SonarQube Cloud (was it deleted?)", moduleKey, organizationKey);
    }
    return String.format("Project with key '%s' not found on your SonarQube Server instance (was it deleted?)", moduleKey);
  }
}
