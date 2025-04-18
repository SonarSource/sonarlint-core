/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProtobufFileUtil {
  private ProtobufFileUtil() {
    // only static stuff
  }

  public static <T extends Message> T readFile(Path file, Parser<T> parser) {
    try (var input = Files.newInputStream(file)) {
      return parser.parseFrom(input);
    } catch (IOException e) {
      throw new StorageException("Failed to read file: " + file, e);
    }
  }

  public static void writeToFile(Message message, Path toFile) {
    try (var out = Files.newOutputStream(toFile)) {
      message.writeTo(out);
      out.flush();
    } catch (IOException e) {
      throw new StorageException("Unable to write protocol buffer data to file " + toFile, e);
    }
  }
}
