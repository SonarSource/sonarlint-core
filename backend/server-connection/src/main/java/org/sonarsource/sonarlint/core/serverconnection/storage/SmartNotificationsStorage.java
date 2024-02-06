/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class SmartNotificationsStorage {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String LAST_EVENT_POLLING_PB = "last_event_polling.pb";
  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public SmartNotificationsStorage(Path projectStorageRoot) {
    this.storageFilePath = projectStorageRoot.resolve(LAST_EVENT_POLLING_PB);
  }

  public void store(Long lastEventPolling) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var serverInfoToStore = adapt(lastEventPolling);
    LOG.debug("Storing last event polling in {}", storageFilePath);
    rwLock.write(() -> writeToFile(serverInfoToStore, storageFilePath));
  }

  public Optional<Long> readLastEventPolling() {
    return rwLock.read(() -> Files.exists(storageFilePath) ?
      Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.LastEventPolling.parser()))) : Optional.empty());
  }

  private static Sonarlint.LastEventPolling adapt(Long lastEventPolling) {
    return Sonarlint.LastEventPolling.newBuilder().setLastEventPolling(lastEventPolling).build();
  }

  private static Long adapt(Sonarlint.LastEventPolling lastEventPolling) {
    return lastEventPolling.getLastEventPolling();
  }

}
