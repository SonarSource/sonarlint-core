/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisQueueTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_prioritize_unregister_module_commands_over_analyses() throws InterruptedException {
    var analysisQueue = new AnalysisQueue();
    var taskManager = mock(TaskManager.class);
    analysisQueue.post(new AnalyzeCommand("key", UUID.randomUUID(), null, null, null, null, new SonarLintCancelMonitor(), taskManager, null, () -> true, Set.of(), Map.of()));
    var unregisterModuleCommand = new UnregisterModuleCommand("key");
    analysisQueue.post(unregisterModuleCommand);

    var command = analysisQueue.takeNextCommand();

    assertThat(command).isEqualTo(unregisterModuleCommand);
  }

  @Test
  void it_should_not_queue_a_canceled_command() throws InterruptedException {
    var canceledProgressMonitor = new SonarLintCancelMonitor();
    var progressMonitor = new SonarLintCancelMonitor();
    var analysisQueue = new AnalysisQueue();
    var taskManager = mock(TaskManager.class);
    var canceledCommand = new AnalyzeCommand("1", UUID.randomUUID(), TriggerType.FORCED, null, null, null, canceledProgressMonitor, taskManager, null, () -> true, Set.of(), Map.of());
    var command = new AnalyzeCommand("2", UUID.randomUUID(), TriggerType.FORCED, null, null, null, progressMonitor, taskManager, null, () -> true, Set.of(), Map.of());
    canceledProgressMonitor.cancel();
    analysisQueue.post(canceledCommand);
    analysisQueue.post(command);

    var nextCommand = analysisQueue.takeNextCommand();

    assertThat(nextCommand).isEqualTo(command);
  }

}
