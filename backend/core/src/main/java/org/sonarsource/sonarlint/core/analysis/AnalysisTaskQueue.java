/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AnalysisTaskQueue {
  private final List<AnalysisTask> queue = new ArrayList<>();
  private final Set<String> readyConfigScopeIds = new HashSet<>();

  public synchronized void enqueue(AnalysisTask task) throws InterruptedException {
    queue.add(task);
    notifyAll();
  }

  public synchronized void markAsReady(String configScopeId) {
    readyConfigScopeIds.add(configScopeId);
    notifyAll();
  }

  public synchronized AnalysisTask takeNextTask() throws InterruptedException {
    do {
      var firstReadyTask = getNextReadyTask();
      if (firstReadyTask.isPresent()) {
        var task = firstReadyTask.get();
        queue.remove(task);
        return task;
      }
      // wait for a new task to come in
      wait();
    } while (true);
  }

  private Optional<AnalysisTask> getNextReadyTask() {
    return queue.stream()
      .filter(task -> readyConfigScopeIds.contains(task.getConfigScopeId()))
      .findFirst();
  }

  public void drainTo(List<AnalysisTask> pendingTasks) {

  }
}
