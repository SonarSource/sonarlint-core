/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.client.api.common.SonarLintPathManager.SONARLINT_USER_HOME_ENV;

public class SonarLintPathManagerTest {
  @Rule
  public final EnvironmentVariables env = new EnvironmentVariables();

  @Test
  public void env_setting_should_override_default_home() {
    String customHome = "/custom/home";
    env.set(SONARLINT_USER_HOME_ENV, customHome);
    assertThat(SonarLintPathManager.home()).isEqualTo(Paths.get(customHome));
  }

  @Test
  public void default_home_should_be_in_user_home() {
    assertThat(SonarLintPathManager.home()).isEqualTo(Paths.get(System.getProperty("user.home")).resolve(".sonarlint"));
  }
}
