/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBindingTests {
  @Test
  void should_assign_all_parameters_in_constructor() {
    ProjectBinding projectBinding = new ProjectBinding("key", "sqPrefix", "localPrefix");
    assertThat(projectBinding.projectKey()).isEqualTo("key");
    assertThat(projectBinding.sqPathPrefix()).isEqualTo("sqPrefix");
    assertThat(projectBinding.idePathPrefix()).isEqualTo("localPrefix");
  }

  @Test
  void equals_and_hashCode_should_use_all_fields() {
    ProjectBinding projectBinding1 = new ProjectBinding("key", "sqPrefix", "localPrefix");
    ProjectBinding projectBinding2 = new ProjectBinding("key1", "sqPrefix", "localPrefix");
    ProjectBinding projectBinding3 = new ProjectBinding("key", "sqPrefix1", "localPrefix");
    ProjectBinding projectBinding4 = new ProjectBinding("key", "sqPrefix", "localPrefix1");
    ProjectBinding projectBinding5 = new ProjectBinding("key", "sqPrefix", "localPrefix");

    assertThat(projectBinding1.equals(projectBinding2)).isFalse();
    assertThat(projectBinding1.equals(projectBinding3)).isFalse();
    assertThat(projectBinding1.equals(projectBinding4)).isFalse();
    assertThat(projectBinding1.equals(projectBinding5)).isTrue();

    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding2.hashCode());
    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding3.hashCode());
    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding4.hashCode());
    assertThat(projectBinding1.hashCode()).isEqualTo(projectBinding5.hashCode());
  }
}
