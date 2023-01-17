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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil.readMessages;

class ProtobufUtilTests {

  private static final Sonarlint.PluginReferences SOME_MESSAGE = Sonarlint.PluginReferences.newBuilder().build();
  private static final Parser<Sonarlint.PluginReferences> SOME_PARSER = Sonarlint.PluginReferences.parser();

  @Test
  void test_readMessages_empty() throws IOException {
    try (InputStream inputStream = newEmptyStream()) {
      assertThat(readMessages(inputStream, SOME_PARSER)).isEmpty();
    }
  }

  @Test
  void test_readMessages_multiple() throws IOException {
    var issue1 = SOME_MESSAGE;
    var issue2 = SOME_MESSAGE;

    try (InputStream inputStream = new ByteArrayInputStream(toByteArray(issue1, issue2))) {
      assertThat(readMessages(inputStream, issue1.getParserForType())).containsOnly(issue1, issue2);
    }
  }

  @Test
  void test_readMessages_error() {
    InputStream inputStream = new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8));

    var thrown = assertThrows(IllegalStateException.class, () -> readMessages(inputStream, SOME_PARSER));
    assertThat(thrown).hasMessage("failed to parse protobuf message");
  }

  @Test
  void test_readFile_error() {
    var p = Paths.get("invalid_non_existing_file");
    var thrown = assertThrows(StorageException.class, () -> ProtobufUtil.readFile(p, SOME_PARSER));
    assertThat(thrown).hasMessageStartingWith("Failed to read file");
  }

  @Test
  void test_writeFile_error() {
    var p = Paths.get("invalid", "non_existing", "file");
    var thrown = assertThrows(StorageException.class, () -> ProtobufUtil.writeToFile(SOME_MESSAGE, p));
    assertThat(thrown).hasMessageStartingWith("Unable to write protocol buffer data to file");
  }

  @Test
  void test_writeMessage_error() throws IOException {
    var out = mock(OutputStream.class);
    doThrow(IOException.class).when(out).write(any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());

    var thrown = assertThrows(IllegalStateException.class, () -> ProtobufUtil.writeMessage(out, SOME_MESSAGE));
    assertThat(thrown).hasMessageStartingWith("failed to write message");
  }

  public static byte[] toByteArray(Message... messages) throws IOException {
    try (var byteStream = new ByteArrayOutputStream()) {
      for (Message msg : messages) {
        msg.writeDelimitedTo(byteStream);
      }
      return byteStream.toByteArray();
    }
  }

  public static ByteArrayInputStream newEmptyStream() {
    return new ByteArrayInputStream(new byte[0]);
  }
}
