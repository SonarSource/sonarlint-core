/*
 * SonarLint Core - Telemetry
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.SonarUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import org.springframework.context.annotation.Lazy;

public class MachineIdProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String USER_FILE_NAME = "user";
  private static final int MAX_CREATE_ATTEMPTS = 2;
  // Prevents same-JVM callers from racing each other; cross-process races are still possible.
  private static final Object CREATE_LOCK = new Object();

  private final TelemetryUserSetting userSetting;
  private boolean resolved;
  private String cachedMachineId;

  public MachineIdProvider(@Lazy TelemetryUserSetting userSetting) {
    this.userSetting = userSetting;
  }

  @CheckForNull
  public synchronized String getMachineId() {
    if (!userSetting.isTelemetryEnabledByUser()) {
      return null;
    }
    if (!resolved) {
      cachedMachineId = resolveFromDisk();
      resolved = true;
    }
    return cachedMachineId;
  }

  @CheckForNull
  private static String resolveFromDisk() {
    try {
      var userFile = SonarUserHome.get().resolve(USER_FILE_NAME);
      var existing = tryRead(userFile);
      if (existing != null) {
        return existing;
      }
      Files.createDirectories(userFile.getParent());
      return createOrReadWinner(userFile);
    } catch (Exception e) {
      LOG.debug("Unable to resolve machine id", e);
      return null;
    }
  }

  @CheckForNull
  private static String createOrReadWinner(Path userFile) throws IOException {
    synchronized (CREATE_LOCK) {
      var candidate = UUID.randomUUID().toString();
      for (var attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
        try {
          return writeExclusively(userFile, candidate);
        } catch (FileAlreadyExistsException e) {
          var winner = tryRead(userFile);
          if (winner != null) {
            return winner;
          }
          Files.deleteIfExists(userFile);
        }
      }
      // All attempts lost the race; read the winner instead of returning null or an unpersisted candidate.
      return tryRead(userFile);
    }
  }

  private static String writeExclusively(Path userFile, String id) throws IOException {
    Files.writeString(userFile, id, StandardOpenOption.CREATE_NEW);
    return id;
  }

  @CheckForNull
  private static String tryRead(Path userFile) {
    try {
      var content = Files.readString(userFile).trim();
      return content.isEmpty() ? null : content;
    } catch (IOException e) {
      return null;
    }
  }
}
