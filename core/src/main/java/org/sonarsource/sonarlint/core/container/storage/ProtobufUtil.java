/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProtobufUtil {
  private ProtobufUtil() {
    // only static stuff
  }

  public static <T extends Message> T readFile(Path file, Parser<T> parser) {
    try (InputStream input = Files.newInputStream(file)) {
      return parser.parseFrom(input);
    } catch (IOException e) {
      throw new StorageException("Failed to read file: " + file, e);
    }
  }

  public static void writeToFile(Message message, Path toFile) {
    try (OutputStream out = Files.newOutputStream(toFile)) {
      message.writeTo(out);
    } catch (IOException e) {
      throw new StorageException("Unable to write protocol buffer data to file " + toFile, e);
    }
  }

  public static <T extends Message> List<T> readMessages(InputStream input, Parser<T> parser) {
    List<T> list = new ArrayList<>();
    while (true) {
      T message;
      try {
        message = parser.parseDelimitedFrom(input);
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException("failed to parse protobuf message", e);
      }
      if (message == null) {
        break;
      }
      list.add(message);
    }
    return list;
  }

  public static <T extends Message> void writeMessages(OutputStream output, Iterator<T> messages) {
    while (messages.hasNext()) {
      writeMessage(output, messages.next());
    }
  }

  public static <T extends Message> void writeMessages(OutputStream output, Iterable<T> messages) {
    for (Message message : messages) {
      writeMessage(output, message);
    }
  }

  public static <T extends Message> void writeMessage(OutputStream output, T message) {
    try {
      message.writeDelimitedTo(output);
    } catch (IOException e) {
      throw new IllegalStateException("failed to write message: " + message, e);
    }
  }
}
