/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

class IOExceptionUtilsTests {

  @Test
  void test_tryAndCollectIOException_no_exceptions() {
    var list = new ArrayDeque<IOException>();
    tryAndCollectIOException(() -> {
    }, list);
    assertThat(list).isEmpty();
  }

  @Test
  void test_tryAndCollectIOException_one_exception() {
    var list = new ArrayDeque<IOException>();
    tryAndCollectIOException(() -> {
      throw new IOException("e1");
    }, list);
    assertThat(list).hasSize(1);
  }

  @Test
  void test_tryAndCollectIOException_multiple_exceptions() {
    var list = new ArrayDeque<IOException>();
    tryAndCollectIOException(() -> {
      throw new IOException("e1");
    }, list);
    tryAndCollectIOException(() -> {
      throw new IOException("e2");
    }, list);
    assertThat(list).extracting(IOException::getMessage).containsExactlyInAnyOrder("e1", "e2");
  }

  @Test
  void test_throwFirstWithOtherSuppressed_no_exceptions() {
    assertDoesNotThrow(() -> throwFirstWithOtherSuppressed(new ArrayDeque<>(List.of())));
  }

  @Test
  void test_throwFirstWithOtherSuppressed_one_exception() {
    var thrown = assertThrows(IOException.class, () -> throwFirstWithOtherSuppressed(new ArrayDeque<>(List.of(new IOException("e1")))));
    assertThat(thrown).hasMessage("e1").hasNoSuppressedExceptions();
  }

  @Test
  void test_throwFirstWithOtherSuppressed_multiple_exceptions() {
    var thrown = assertThrows(IOException.class, () -> throwFirstWithOtherSuppressed(new ArrayDeque<>(List.of(new IOException("e1"), new IOException("e2"), new IOException("e3")))));
    assertThat(thrown).hasMessage("e1").hasSuppressedException(new IOException("e2")).hasSuppressedException(new IOException("e3"));
  }

}
