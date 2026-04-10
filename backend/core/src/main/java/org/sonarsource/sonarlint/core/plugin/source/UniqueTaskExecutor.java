/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.source;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class UniqueTaskExecutor {

  private final Map<String, Future<?>> inProgress = new ConcurrentHashMap<>();
  private final ExecutorService executor;

  public UniqueTaskExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  public Future<?> scheduleIfAbsent(String key, Runnable task) {
    return inProgress.computeIfAbsent(key, k -> {
      var logOutput = SonarLintLogger.get().getTargetForCopy();
      return executor.submit(() -> {
        SonarLintLogger.get().setTarget(logOutput);
        try {
          task.run();
        } finally {
          inProgress.remove(key);
          SonarLintLogger.get().setTarget(null);
        }
      });
    });
  }

}
