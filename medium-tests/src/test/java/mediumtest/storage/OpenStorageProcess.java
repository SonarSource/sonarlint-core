/*
 * SonarLint Core - Medium Tests
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
package mediumtest.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;

/**
 * Run in a separate process by {@link StorageConcurrentStartupMediumTests}, like the backend of one IDE window: reports that it is ready, opens the storage once the start
 * file exists, prints the outcome, and keeps the storage open until the stop file exists.
 * <p>
 * Arguments: the storage root, the start file, the stop file.
 */
public class OpenStorageProcess {

  static final String READY = "READY";
  static final String OPENED = "OPENED server_findings=";
  static final String FAILED = "FAILED ";

  private OpenStorageProcess() {
    // only used through main()
  }

  public static void main(String[] args) throws Exception {
    var storageRoot = Path.of(args[0]);
    var startFile = Path.of(args[1]);
    var stopFile = Path.of(args[2]);
    // Load what opening the storage needs, so that the processes only differ by when they get the start signal
    Class.forName("org.h2.Driver");
    Class.forName(SonarLintDatabase.class.getName());
    System.out.println(READY);
    System.out.flush();
    waitFor(startFile);
    try {
      var database = new SonarLintDatabase(storageRoot);
      System.out.println(OPENED + database.dsl().fetchCount(SERVER_FINDINGS));
    } catch (Exception e) {
      System.out.println(FAILED + ExceptionUtils.getRootCauseMessage(e));
    }
    System.out.flush();
    waitFor(stopFile);
    // Exit without closing: when the owner of the database leaves, the others would race to replace it, which is not what this test is about
    Runtime.getRuntime().halt(0);
  }

  static void waitFor(Path file) {
    await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofMinutes(5)).until(() -> Files.exists(file));
  }
}
