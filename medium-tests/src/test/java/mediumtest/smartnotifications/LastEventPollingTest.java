/*
 * SonarLint Core - Medium Tests
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
package mediumtest.smartnotifications;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.smartnotifications.LastEventPolling;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class LastEventPollingTest {

  private static final ZonedDateTime STORED_DATE = ZonedDateTime.now().minusDays(5);
  private static final String PROJECT_KEY = "projectKey";
  private static final String CONNECTION_ID = "connectionId";
  private static final String FILE_NAME = "last_event_polling.pb";

  @Test
  void should_retrieve_stored_last_event_polling(@TempDir Path tmpDir) {
    var storageFile = tmpDir.resolve(encodeForFs(CONNECTION_ID)).resolve("projects").resolve(encodeForFs(PROJECT_KEY)).resolve(FILE_NAME);
    FileUtils.mkdirs(storageFile.getParent());
    ProtobufFileUtil.writeToFile(Sonarlint.LastEventPolling.newBuilder()
      .setLastEventPolling(STORED_DATE.toInstant().toEpochMilli())
      .build(), storageFile);
    var storage = new StorageService(tmpDir, tmpDir);
    var lastEventPolling = new LastEventPolling(storage);

    var result = lastEventPolling.getLastEventPolling(CONNECTION_ID, PROJECT_KEY);

    assertThat(result).isEqualTo(STORED_DATE.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void should_store_last_event_polling(@TempDir Path tmpDir) {
    var storage = new StorageService(tmpDir, tmpDir);
    var lastEventPolling = new LastEventPolling(storage);
    lastEventPolling.setLastEventPolling(STORED_DATE, CONNECTION_ID, PROJECT_KEY);

    var result = lastEventPolling.getLastEventPolling(CONNECTION_ID, PROJECT_KEY);

    assertThat(result).isEqualTo(STORED_DATE.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void should_not_retrieve_stored_last_event_polling(@TempDir Path tmpDir) {
    var storage = new StorageService(tmpDir, tmpDir);
    var lastEventPolling = new LastEventPolling(storage);

    var result = lastEventPolling.getLastEventPolling(CONNECTION_ID, PROJECT_KEY);

    assertThat(result).isBeforeOrEqualTo(ZonedDateTime.now()).isAfter(ZonedDateTime.now().minusSeconds(3));
  }

}
