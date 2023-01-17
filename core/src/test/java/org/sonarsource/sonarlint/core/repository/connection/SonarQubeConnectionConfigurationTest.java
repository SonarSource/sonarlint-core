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

class SonarQubeConnectionConfigurationTest {

  @Test
  void test_isSameServerUrl() {
    var underTest = new SonarQubeConnectionConfiguration("id", "https://mycompany.org");
    assertThat(underTest.isSameServerUrl("https://mycompany.org")).isTrue();
    // URL are case insensitive
    assertThat(underTest.isSameServerUrl("https://Mycompany.Org")).isTrue();
    // We can ignore trailing slash difference, as we are looking for a base URL
    assertThat(underTest.isSameServerUrl("https://mycompany.org/")).isTrue();
    // Protocol difference, let's play it safe and not assume it is the same server
    assertThat(underTest.isSameServerUrl("http://mycompany.org")).isFalse();
    // Different path
    assertThat(underTest.isSameServerUrl("https://mycompany.org/sonarqube")).isFalse();
    // Different domain
    assertThat(underTest.isSameServerUrl("https://sq.mycompany.org")).isFalse();
  }

  @Test
  void testEqualsAndHashCode() {
    var underTest = new SonarQubeConnectionConfiguration("id1", "http://server1");

    assertThat(underTest)
      .isEqualTo(new SonarQubeConnectionConfiguration("id1", "http://server1"))
      .isNotEqualTo(new SonarQubeConnectionConfiguration("id2", "http://server1"))
      .isNotEqualTo(new SonarQubeConnectionConfiguration("id1", "http://server2"))
      .isNotEqualTo(new SonarCloudConnectionConfiguration("id1", "org1"))
      .hasSameHashCodeAs(new SonarQubeConnectionConfiguration("id1", "http://server1"));
  }


}