/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectNotFoundExceptionTests {

  @Test
  void show_organization_key() {
    var ex = new ProjectNotFoundException("module", "organization");
    assertThat(ex.getMessage()).isEqualTo("Project with key 'module' in organization 'organization' not found on SonarQube server (was it deleted?)");
  }

  @Test
  void organization_key_missing() {
    var ex = new ProjectNotFoundException("module", null);
    assertThat(ex.getMessage()).isEqualTo("Project with key 'module' not found on SonarQube server (was it deleted?)");
  }
}
