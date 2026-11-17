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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;

/**
 * Run in a separate process by {@link StorageOwnerLeavesMediumTests}, like the backend of one IDE window: opens the storage, keeps several connections to it, and reports
 * that it is open. Then, depending on its role:
 * <ul>
 *   <li>{@link #OWNER}: once the go file exists, closes the storage and exits, like an IDE window being closed</li>
 *   <li>{@link #USER}: once the go file exists, uses the storage from all its connections at the same time, prints the outcome, and keeps running until the stop file
 *   exists</li>
 * </ul>
 * Arguments: the role, the storage root, the go file, the stop file.
 */
public class ShareStorageProcess {

  static final String OWNER = "owner";
  static final String USER = "user";
  static final String OPENED = "OPENED";
  static final String USED = "USED server_findings=";
  static final String FAILED = "FAILED ";
  // Like several requests of the IDE being served at the same time
  private static final int CONNECTIONS = 4;

  private ShareStorageProcess() {
    // only used through main()
  }

  public static void main(String[] args) {
    var role = args[0];
    var storageRoot = Path.of(args[1]);
    var goFile = Path.of(args[2]);
    var stopFile = Path.of(args[3]);
    SonarLintDatabase database;
    try {
      database = new SonarLintDatabase(storageRoot);
      holdConnectionsTogether(database);
    } catch (Exception e) {
      report(FAILED + ExceptionUtils.getRootCauseMessage(e));
      return;
    }
    report(OPENED);
    OpenStorageProcess.waitFor(goFile);
    if (OWNER.equals(role)) {
      database.shutdown();
      System.exit(0);
    }
    report(useFromAllConnections(database));
    OpenStorageProcess.waitFor(stopFile);
    Runtime.getRuntime().halt(0);
  }

  /**
   * Leaves that many connections in the pool, as each one is in use while the others are acquired
   */
  private static void holdConnectionsTogether(SonarLintDatabase database) {
    var allAcquired = new CyclicBarrier(CONNECTIONS);
    runOnAllConnections(() -> {
      database.dsl().connection(connection -> allAcquired.await());
      return null;
    });
  }

  private static String useFromAllConnections(SonarLintDatabase database) {
    try {
      var counts = runOnAllConnections(() -> database.dsl().fetchCount(SERVER_FINDINGS));
      return counts.stream().distinct().count() == 1 ? (USED + counts.get(0)) : (FAILED + "different counts " + counts);
    } catch (Exception e) {
      // Shown in the test output, to tell where it failed
      e.printStackTrace();
      return FAILED + ExceptionUtils.getRootCauseMessage(e);
    }
  }

  private static <T> List<T> runOnAllConnections(Callable<T> task) {
    try (var executor = Executors.newFixedThreadPool(CONNECTIONS)) {
      var results = new ArrayList<CompletableFuture<T>>();
      for (var i = 0; i < CONNECTIONS; i++) {
        results.add(CompletableFuture.supplyAsync(() -> {
          try {
            return task.call();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        }, executor));
      }
      return results.stream().map(CompletableFuture::join).toList();
    }
  }

  private static void report(String outcome) {
    System.out.println(outcome);
    System.out.flush();
  }
}
