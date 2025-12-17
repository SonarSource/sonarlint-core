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
package org.sonarsource.sonarlint.core.analysis.command;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeCommandTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_cancel_posting_command() throws Exception {
    var files = Set.of(new URI("file:///test1"));
    var props = Map.of("a", "b");
    var cmd1 = newAnalyzeCommand(files, props);
    var cmd2 = newAnalyzeCommand(files, props);

    assertThat(cmd1.shouldCancelPost(cmd2)).isTrue();
  }

  @Test
  void it_should_cancel_posting_command_if_canceled() throws Exception {
    var cmd1 = newAnalyzeCommand(Set.of(new URI("file:///test1")), Map.of());
    var cmd2 = newAnalyzeCommand(Set.of(new URI("file:///test2")), Map.of());
    cmd1.cancel();

    assertThat(cmd1.shouldCancelPost(cmd2)).isTrue();
  }

  @Test
  void it_should_not_cancel_when_files_are_different() throws Exception {
    var cmd1 = newAnalyzeCommand(Set.of(new URI("file:///test1")), Map.of());
    var cmd2 = newAnalyzeCommand(Set.of(new URI("file:///test2")), Map.of());

    assertThat(cmd1.shouldCancelPost(cmd2)).isFalse();
  }


  @Test
  void if_should_cancel_task_in_queue_when_canceled() {
    var cmd = newAnalyzeCommand(Set.of(), Map.of());
    cmd.cancel();

    assertThat(cmd.shouldCancelQueue()).isTrue();
  }

  @Test
  void it_should_not_cancel_task_in_queue_if_not_canceled() {
    var cmd = newAnalyzeCommand(Set.of(), Map.of());

    assertThat(cmd.shouldCancelQueue()).isFalse();
  }

  private static AnalyzeCommand newAnalyzeCommand(Set<URI> files, Map<String, String> extraProps) {
    return new AnalyzeCommand(
      "moduleKey",
      UUID.randomUUID(),
      TriggerType.FORCED,
      () -> AnalysisConfiguration.builder().addInputFiles().build(),
      issue -> {},
      null,
      new SonarLintCancelMonitor(),
      new TaskManager(),
      inputFiles -> {},
      () -> true,
      files,
      extraProps
    );
  }

}
