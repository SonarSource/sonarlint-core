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
package org.sonarsource.sonarlint.core.client.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class StorageExceptionTests {
  @Test
  void withCauseAndMessage() {
    IOException cause = new IOException("cause");
    StorageException ex = new StorageException("msg", cause);
    assertThat(ex.getCause()).isEqualTo(cause);
    assertThat(ex.getMessage()).isEqualTo("msg");
    assertThat(ex.getStackTrace()).isNotEmpty();
  }

  @Test
  void withNoStack() {
    StorageException ex = new StorageException("msg", false);
    assertThat(ex.getCause()).isNull();
    assertThat(ex.getMessage()).isEqualTo("msg");
    assertThat(ex.getStackTrace()).isEmpty();
  }
}
