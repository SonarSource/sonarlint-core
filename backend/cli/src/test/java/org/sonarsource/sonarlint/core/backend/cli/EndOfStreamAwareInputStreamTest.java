/*
 * SonarLint Core - Backend CLI
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
package org.sonarsource.sonarlint.core.backend.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndOfStreamAwareInputStreamTest {
  @Test
  void it_should_complete_onExit_when_reading_single_byte_and_stream_is_empty() throws IOException {
    var stream = new EndOfStreamAwareInputStream(new ByteArrayInputStream(new byte[0]));

    var bytesRead = stream.read();

    assertThat(bytesRead).isEqualTo(-1);
    assertThat(stream.onExit()).isCompleted();
  }

  @Test
  void it_should_complete_onExit_when_reading_byte_array_and_stream_is_empty() throws IOException {
    var stream = new EndOfStreamAwareInputStream(new ByteArrayInputStream(new byte[0]));

    var bytesRead = stream.read(new byte[5]);

    assertThat(bytesRead).isEqualTo(-1);
    assertThat(stream.onExit()).isCompleted();
  }

  @Test
  void it_should_complete_onExit_when_reading_byte_array_slice_and_stream_is_empty() throws IOException {
    var stream = new EndOfStreamAwareInputStream(new ByteArrayInputStream(new byte[0]));

    var bytesRead = stream.read(new byte[5], 0, 3);

    assertThat(bytesRead).isEqualTo(-1);
    assertThat(stream.onExit()).isCompleted();
  }

  @Test
  void it_should_not_complete_onExit_if_stream_is_not_empty() throws IOException {
    var stream = new EndOfStreamAwareInputStream(new ByteArrayInputStream(new byte[] {0b01}));

    var bytesRead = stream.read();

    assertThat(bytesRead).isEqualTo(1);
    assertThat(stream.onExit()).isNotCompleted();
  }
}
