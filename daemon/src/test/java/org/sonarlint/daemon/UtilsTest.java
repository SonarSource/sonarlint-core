/*
 * SonarLint Daemon
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
package org.sonarlint.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilsTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void test() {
    assertThat(Utils.getStandaloneHome()).isNotNull();
  }

  @Test
  public void fail_if_sonarlint_inst_home_not_defined() {
    Assume.assumeFalse(System.getProperty("sonarlint.home") != null);
    exception.expect(IllegalStateException.class);
    exception.expectMessage("The system property 'sonarlint.home' must be defined");
    assertThat(Utils.getSonarLintInstallationHome()).isNotNull();
  }
}
