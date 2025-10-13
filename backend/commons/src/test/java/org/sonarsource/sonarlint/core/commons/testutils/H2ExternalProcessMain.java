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
package org.sonarsource.sonarlint.core.commons.testutils;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseInitParams;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseMode;

import static org.sonarsource.sonarlint.core.commons.testutils.H2Utils.ensureTestTableExists;
import static org.sonarsource.sonarlint.core.commons.testutils.H2Utils.insertRecords;

/**
 * Helper main class used by tests to simulate a second Java process
 * connecting to the same file-based H2 database.
 */
public class H2ExternalProcessMain {

  public static void main(String[] args) throws Exception {
    configureLogger();
    var autoServer = args.length <= 1 || Boolean.parseBoolean(args[1]);
    var path = args.length > 2 ? Path.of(args[2]) : Path.of(".");
    System.out.println("Starting H2ExternalProcessMain with autoServer=" + autoServer);
    System.out.println("External process PID: " + ProcessHandle.current().pid());
    var initParams = new SonarLintDatabaseInitParams(path, SonarLintDatabaseMode.FILE, autoServer);
    var sonarLintDatabase = new SonarLintDatabase(initParams);

    ensureTestTableExists(sonarLintDatabase);
    insertRecords(sonarLintDatabase, 1000);
  }

  private static void configureLogger() {
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
  }
}
