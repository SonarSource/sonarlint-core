/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/** A separate JVM keeps the original AUTO_SERVER owner alive throughout each sharing test. */
public class H2DatabaseProcessMain {
  private H2DatabaseProcessMain() {
  }

  @SuppressWarnings("removal")
  public static void main(String[] args) throws Exception {
    var storage = Path.of(args[0]);
    var gates = Path.of(args[1]);
    var logger = SonarLintLogger.get();
    logger.setLevel(LogOutput.Level.DEBUG);
    logger.setTarget(new LogOutput() {
      @Override
      public void log(String message, Level level, String stacktrace) {
        System.out.println(level + " " + message);
        if (stacktrace != null) {
          System.out.println(stacktrace);
        }
        if (message != null && message.contains("retrying connection acquisition")) {
          signal(gates.resolve("retry"));
        }
      }
    });
    if (args[2].equals("delayed-owner")) {
      // Java 21 test-only gate: H2 acquires its file lock before its first AUTO_SERVER bind.
      System.setSecurityManager(new SecurityManager() {
        private final AtomicBoolean firstListen = new AtomicBoolean(true);

        @Override
        public void checkPermission(Permission permission) {
          // Permissive: observe the first listen without restricting this child JVM.
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
          // Permissive.
        }

        @Override
        public void checkListen(int port) {
          if (firstListen.compareAndSet(true, false)) {
            signal(gates.resolve("locked"));
            awaitGate(gates.resolve("release"));
          }
        }
      });
    }
    signal(gates.resolve("ready"));
    awaitGate(args[2].equals("simultaneous") ? gates.getParent().resolve("simultaneous-start") : gates.resolve("start"));
    var database = new SonarLintDatabase(storage);
    try {
      // Use a migrated table, and leave a row another process can read while this owner is alive.
      database.dsl().execute("MERGE INTO AI_CODEFIX_SETTINGS (connection_id) KEY (connection_id) VALUES (?)", gates.getFileName().toString());
      database.dsl().fetch("SELECT connection_id FROM AI_CODEFIX_SETTINGS");
      signal(gates.resolve("initialized"));
      awaitGate(gates.resolve("query"));
      if (database.dsl().fetchCount(org.jooq.impl.DSL.table("AI_CODEFIX_SETTINGS")) < 2) {
        throw new IllegalStateException("Other process's migrated data is not visible");
      }
      signal(gates.resolve("queried"));
      awaitGate(gates.resolve("stop"));
    } finally {
      database.shutdown();
    }
  }

  static void awaitGate(Path gate) {
    var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    try {
      while (!Files.exists(gate)) {
        if (System.nanoTime() >= deadline) {
          throw new IllegalStateException("Timed out waiting for test gate " + gate.getFileName());
        }
        Thread.sleep(10);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  static void signal(Path gate) {
    try {
      Files.writeString(gate, "ready");
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
