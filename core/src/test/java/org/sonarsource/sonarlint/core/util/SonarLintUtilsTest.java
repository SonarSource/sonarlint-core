/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.util.SonarLintUtils.INTERNAL_DEBUG_ENV;
import static org.sonarsource.sonarlint.core.util.SonarLintUtils.isInternalDebugEnabled;

public class SonarLintUtilsTest {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Test
  public void isInternalDebugEnabled_should_return_false_when_var_unset() {
    environmentVariables.set(INTERNAL_DEBUG_ENV, null);
    assertThat(isInternalDebugEnabled()).isFalse();
  }

  @Test
  public void isInternalDebugEnabled_should_return_false_when_var_empty() {
    environmentVariables.set(INTERNAL_DEBUG_ENV, "");
    assertThat(isInternalDebugEnabled()).isFalse();
  }

  @Test
  public void isInternalDebugEnabled_should_return_false_when_var_not_true() {
    environmentVariables.set(INTERNAL_DEBUG_ENV, "foo");
    assertThat(isInternalDebugEnabled()).isFalse();
  }

  @Test
  public void isInternalDebugEnabled_should_return_true_when_var_true() {
    environmentVariables.set(INTERNAL_DEBUG_ENV, "true");
    assertThat(isInternalDebugEnabled()).isTrue();
  }
}
