/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.progress;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutorServiceShutdownWatchableTests {

  @Test
  void should_cancel_all_monitors() {
    ExecutorServiceShutdownWatchable<?> underTest = new ExecutorServiceShutdownWatchable<>(mock(ExecutorService.class));

    var monitors = new ArrayList<SonarLintCancelMonitor>();
    for (int i = 0; i < 1000; i++) {
      var monitor = new SonarLintCancelMonitor();
      underTest.cancelOnShutdown(monitor);
      monitors.add(monitor);
    }
    underTest.shutdown();

    assertThat(monitors).allMatch(SonarLintCancelMonitor::isCanceled);
  }

}