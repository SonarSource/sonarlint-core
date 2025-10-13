/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage;

import java.nio.file.Path;
import java.sql.ResultSet;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Helper main class used by tests to simulate a second Java process
 * connecting to the same file-based H2 database.
 */
public class H2ExternalProcessMain {
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Missing argument: storageRootPath");
      System.exit(2);
      return;
    }

    // Configure logger for standalone process to avoid IllegalStateException
    SonarLintLogger.get().setTarget(new LogOutput() {
      @Override
      public void log(String formattedMessage, Level level, String stacktrace) {
        // keep it quiet; print only errors for debugging
        if (level == Level.ERROR || level == Level.WARN) {
          if (formattedMessage != null) System.out.println(level + ": " + formattedMessage);
          if (stacktrace != null) System.out.println(stacktrace);
        }
      }
    });

    var storageRoot = Path.of(args[0]);
    var db = new H2Database(new StorageInitParams(storageRoot));

    int attempts = 10;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try (var c = db.getConnection(); var st = c.createStatement()) {
        // Confirm existing row from the first process is visible
        try (ResultSet rs = st.executeQuery("SELECT VAL FROM T WHERE ID=1")) {
          if (!rs.next()) {
            System.err.println("Row id=1 not found by external process");
            System.exit(3);
            return;
          }
        }
        // Write a new row from the external process
        st.executeUpdate("MERGE INTO T KEY(ID) VALUES (2, 'from-external-process')");
        db.shutdown();
        System.exit(0);
        return;
      } catch (Exception e) {
        last = e;
        try {
          Thread.sleep(200);
        } catch (InterruptedException ie) {
          // ignore
        }
      }
    }
    if (last != null) {
      System.err.println("External process failed after retries: " + last);
      last.printStackTrace(System.err);
    }
    db.shutdown();
    System.exit(4);
  }
}
