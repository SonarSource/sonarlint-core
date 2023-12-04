/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBindingTests {
  @Test
  void should_assign_all_parameters_in_constructor() {
    var projectBinding = new ProjectBinding("key", "sqPrefix", "localPrefix");
    assertThat(projectBinding.projectKey()).isEqualTo("key");
    assertThat(projectBinding.serverPathPrefix()).isEqualTo("sqPrefix");
    assertThat(projectBinding.idePathPrefix()).isEqualTo("localPrefix");
  }

  @Test
  void equals_and_hashCode_should_use_all_fields() {
    var projectBinding1 = new ProjectBinding("key", "sqPrefix", "localPrefix");
    var projectBinding2 = new ProjectBinding("key1", "sqPrefix", "localPrefix");
    var projectBinding3 = new ProjectBinding("key", "sqPrefix1", "localPrefix");
    var projectBinding4 = new ProjectBinding("key", "sqPrefix", "localPrefix1");
    var projectBinding5 = new ProjectBinding("key", "sqPrefix", "localPrefix");

    assertThat(projectBinding1.equals(projectBinding2)).isFalse();
    assertThat(projectBinding1.equals(projectBinding3)).isFalse();
    assertThat(projectBinding1.equals(projectBinding4)).isFalse();
    assertThat(projectBinding1.equals(projectBinding5)).isTrue();

    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding2.hashCode());
    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding3.hashCode());
    assertThat(projectBinding1.hashCode()).isNotEqualTo(projectBinding4.hashCode());
    assertThat(projectBinding1.hashCode()).isEqualTo(projectBinding5.hashCode());
  }

  @Test
  void serverPathToIdePath_no_match_from_server_path() {
    var projectBinding = new ProjectBinding("key", "sqPrefix", "localPrefix");
    assertThat(projectBinding.serverPathToIdePath("notSqPrefix/some/path")).isEmpty();
  }

  @Test
  void serverPathToIdePath_general_case() {
    var projectBinding = new ProjectBinding("key", "sq/path/prefix", "local/prefix");
    assertThat(projectBinding.serverPathToIdePath("sq/path/prefix/some/path")).hasValue("local/prefix/some/path");
  }

  @Test
  void serverPathToIdePath_empty_local_path() {
    var projectBinding = new ProjectBinding("key", "sq/path/prefix", "");
    assertThat(projectBinding.serverPathToIdePath("sq/path/prefix/some/path")).hasValue("some/path");
  }

  @Test
  void serverPathToIdePath_empty_sq_path() {
    var projectBinding = new ProjectBinding("key", "", "local/prefix");
    assertThat(projectBinding.serverPathToIdePath("some/path")).hasValue("local/prefix/some/path");
  }
}
