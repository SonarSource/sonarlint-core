/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.monitoring;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class MonitoringUserIdStore {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String USER_ID_FILE_NAME = "id";

  private final Path path;
  @Nullable
  private volatile UUID cachedUserId;

  public MonitoringUserIdStore(UserPaths userPaths) {
    this.path = userPaths.getUserHome().resolve(USER_ID_FILE_NAME);
  }

  public synchronized Optional<UUID> getOrCreate() {
    var cached = cachedUserId;
    if (cached != null) {
      return Optional.of(cached);
    }
    try {
      Files.createDirectories(path.getParent());
      try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
        var ignored = fileChannel.lock()) {
        var userId = readOrCreateUserId(fileChannel);
        cachedUserId = userId;
        return Optional.of(userId);
      }
    } catch (Exception e) {
      LOG.debug("Failed to read or create user ID", e);
      return Optional.empty();
    }
  }

  private static UUID readOrCreateUserId(FileChannel fileChannel) throws IOException {
    var existingId = readUserId(fileChannel);
    if (existingId != null) {
      return existingId;
    }
    var newId = UUID.randomUUID();
    writeUserId(fileChannel, newId);
    return newId;
  }

  @Nullable
  private static UUID readUserId(FileChannel fileChannel) throws IOException {
    if (fileChannel.size() == 0) {
      return null;
    }
    var buffer = ByteBuffer.allocate((int) fileChannel.size());
    fileChannel.read(buffer);
    try {
      var decoded = Base64.getDecoder().decode(buffer.array());
      var content = new String(decoded, StandardCharsets.UTF_8).trim();
      if (content.isEmpty()) {
        return null;
      }
      return UUID.fromString(content);
    } catch (IllegalArgumentException e) {
      LOG.debug("Invalid encoded UUID in " + USER_ID_FILE_NAME, e);
      return null;
    }
  }

  private static void writeUserId(FileChannel fileChannel, UUID userId) throws IOException {
    fileChannel.truncate(0);
    fileChannel.position(0);
    var encoded = Base64.getEncoder().encode(userId.toString().getBytes(StandardCharsets.UTF_8));
    fileChannel.write(ByteBuffer.wrap(encoded));
  }
}
