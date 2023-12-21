/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarCloudConnectionConfigurationTest {

  @Test
  void testEqualsAndHashCode() {
    var underTest = new SonarCloudConnectionConfiguration("id1", "org1", true);
    assertThat(underTest)
      .isEqualTo(new SonarCloudConnectionConfiguration("id1", "org1", true))
      .isNotEqualTo(new SonarCloudConnectionConfiguration("id2", "org1", true))
      .isNotEqualTo(new SonarCloudConnectionConfiguration("id1", "org2", true))
      .isNotEqualTo(new SonarQubeConnectionConfiguration("id1", "http://server1", true))
      .hasSameHashCodeAs(new SonarCloudConnectionConfiguration("id1", "org1", true));
  }

}
