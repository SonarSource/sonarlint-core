/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.monitoring;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;

@Named
@Singleton
public class DogfoodEnvironmentDetectionService {
  public static final String SONARSOURCE_DOGFOODING_ENV_VAR_KEY = "SONARSOURCE_DOGFOODING";

  public boolean isDogfoodEnvironment() {
    return "1".equals(SystemUtils.getEnvironmentVariable(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "0"));
  }
}
