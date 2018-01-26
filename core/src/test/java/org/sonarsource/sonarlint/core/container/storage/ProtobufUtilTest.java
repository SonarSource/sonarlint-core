/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtil.readMessages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;

public class ProtobufUtilTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void test_readMessages_empty() throws IOException {
    try (InputStream inputStream = newEmptyStream()) {
      assertThat(readMessages(inputStream, ScannerInput.ServerIssue.parser())).isEmpty();
    }
  }

  @Test
  public void test_readMessages_multiple() throws IOException {
    ScannerInput.ServerIssue issue1 = ScannerInput.ServerIssue.newBuilder().build();
    ScannerInput.ServerIssue issue2 = ScannerInput.ServerIssue.newBuilder().build();

    try (InputStream inputStream = new ByteArrayInputStream(toByteArray(issue1, issue2))) {
      assertThat(readMessages(inputStream, issue1.getParserForType())).containsOnly(issue1, issue2);
    }
  }

  @Test
  public void test_readMessages_error() throws IOException {
    exception.expect(IllegalStateException.class);
    exception.expectMessage("failed to parse protobuf message");

    InputStream inputStream = new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8));
    readMessages(inputStream, ScannerInput.ServerIssue.parser());
  }

  @Test
  public void test_readFile_error() throws IOException {
    exception.expect(StorageException.class);
    exception.expectMessage("Failed to read file");

    Path p = Paths.get("invalid_non_existing_file");
    ProtobufUtil.readFile(p, ScannerInput.ServerIssue.parser());
  }

  @Test
  public void test_writeFile_error() throws IOException {
    exception.expect(StorageException.class);
    exception.expectMessage("Unable to write protocol buffer data to file");

    Path p = Paths.get("invalid", "non_existing", "file");
    ProtobufUtil.writeToFile(ScannerInput.ServerIssue.newBuilder().build(), p);
  }

  @Test
  public void test_writeMessage_error() throws IOException {
    OutputStream out = mock(OutputStream.class);
    doThrow(IOException.class).when(out).write(any(), Mockito.anyInt(), Mockito.anyInt());

    exception.expect(IllegalStateException.class);
    exception.expectMessage("failed to write message");
    ProtobufUtil.writeMessage(out, ScannerInput.ServerIssue.newBuilder().build());
  }

  public static byte[] toByteArray(ScannerInput.ServerIssue... issues) throws IOException {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
      for (ScannerInput.ServerIssue issue : issues) {
        issue.writeDelimitedTo(byteStream);
      }
      return byteStream.toByteArray();
    }
  }

  public static ByteArrayInputStream newEmptyStream() {
    return new ByteArrayInputStream(new byte[0]);
  }
}
