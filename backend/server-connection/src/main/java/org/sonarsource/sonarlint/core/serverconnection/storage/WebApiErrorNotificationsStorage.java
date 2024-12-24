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

public class WebApiErrorNotificationsStorage {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String WEB_API_ERROR_NOTIFICATIONS = "web_api_error_notifications.pb";
  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public WebApiErrorNotificationsStorage(Path projectStorageRoot) {
    this.storageFilePath = projectStorageRoot.resolve(WEB_API_ERROR_NOTIFICATIONS);
  }

  public void store(Long lastWebApiErrorNotification) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var valueToStore = adapt(lastWebApiErrorNotification);
    LOG.debug("Storing last web API error notification in {}", storageFilePath);
    rwLock.write(() -> writeToFile(valueToStore, storageFilePath));
  }

  public Optional<Long> readLastWebApiErrorNotification() {
    return rwLock.read(() -> Files.exists(storageFilePath) ?
      Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.LastWebApiErrorNotification.parser()))) : Optional.empty());
  }

  private static Sonarlint.LastWebApiErrorNotification adapt(Long lastWebApiErrorNotification) {
    return Sonarlint.LastWebApiErrorNotification.newBuilder().setLastWrongTokenNotification(lastWebApiErrorNotification).build();
  }

  private static Long adapt(Sonarlint.LastWebApiErrorNotification lastWebApiErrorNotification) {
    return lastWebApiErrorNotification.getLastWrongTokenNotification();
  }

}
