/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class UniqueTaskExecutor {
  
  private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor;

  public UniqueTaskExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  public void scheduleIfAbsent(String key, Runnable task) {
    if (inProgress.add(key)) {
      executor.submit(() -> {
        try {
          task.run();
        } finally {
          inProgress.remove(key);
        }
      });
    }
  }

}
