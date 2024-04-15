/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.sync;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Binding;

import static org.assertj.core.api.Assertions.assertThat;

class BranchBindingTest {

  @Test
  void testEquals() {
    var branchBinding = new BranchBinding(new Binding("connection", "projectKey"), "branch");

    assertThat(branchBinding.equals(branchBinding)).isTrue();
    assertThat(branchBinding.equals(null)).isFalse();
    assertThat(branchBinding.equals(new Object())).isFalse();
    assertThat(branchBinding.equals(new BranchBinding(new Binding("connection2", "projectKey"), "branch"))).isFalse();
    assertThat(branchBinding.equals(new BranchBinding(new Binding("connection", "projectKey2"), "branch"))).isFalse();
    assertThat(branchBinding.equals(new BranchBinding(new Binding("connection", "projectKey"), "branch2"))).isFalse();
    assertThat(branchBinding.equals(new BranchBinding(new Binding("connection", "projectKey"), "branch"))).isTrue();
  }
}
