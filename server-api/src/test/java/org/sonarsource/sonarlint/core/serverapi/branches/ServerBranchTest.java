/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.branches;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ServerBranchTest {

  @Test
  void  serverBranchTest() {
    ServerBranch branch = new ServerBranch("foo", true);

    assertThat(branch.getName()).isEqualTo("foo");
    assertThat(branch.isMain()).isTrue();
  }

  @Test
  void serverBranchEqualsTest() {
    ServerBranch a = new ServerBranch("foo", true);
    ServerBranch b = new ServerBranch("foo", true);
    ServerBranch c = new ServerBranch("bar", true);
    ServerBranch d = new ServerBranch("foo", false);
    ServerBranch e = new ServerBranch("bar", false);
    String string = "string";

    assertThat(a).isEqualTo(a);
    assertThat(a.equals(null)).isFalse();
    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(string);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isNotEqualTo(d);
    assertThat(a).isNotEqualTo(e);
  }

  @Test
  void serverBranchHashcodeTest() {
    ServerBranch a = new ServerBranch("foo", true);
    ServerBranch b = new ServerBranch("foo", true);
    ServerBranch c = new ServerBranch("bar", true);
    ServerBranch d = new ServerBranch("foo", false);
    ServerBranch e = new ServerBranch("bar", false);

    assertThat(a).hasSameHashCodeAs(b);
    assertThat(a.hashCode()).isNotEqualTo(c.hashCode());
    assertThat(a.hashCode()).isNotEqualTo(d.hashCode());
    assertThat(a.hashCode()).isNotEqualTo(e.hashCode());
  }

}
