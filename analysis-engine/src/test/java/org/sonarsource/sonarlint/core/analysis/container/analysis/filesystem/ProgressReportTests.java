/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ProgressReportTests {
  private static final String THREAD_NAME = "progress";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void die_on_stop() {
    var underTest = new ProgressReport(THREAD_NAME, 100);
    underTest.start("start");
    assertThat(isThreadAlive(THREAD_NAME)).isTrue();
    underTest.stop("stop");
    assertThat(isThreadAlive(THREAD_NAME)).isFalse();
  }

  @Test
  void accept_no_stop_msg() {
    var underTest = new ProgressReport(THREAD_NAME, 100);
    underTest.start("start");
    assertThat(isThreadAlive(THREAD_NAME)).isTrue();
    underTest.stop(null);
    assertThat(isThreadAlive(THREAD_NAME)).isFalse();
  }

  @Test
  void do_not_block_app() {
    var underTest = new ProgressReport(THREAD_NAME, 100);
    underTest.start("start");
    assertThat(isDaemon(THREAD_NAME)).isTrue();
    underTest.stop("stop");
  }

  @Test
  void do_log() {
    var underTest = new ProgressReport(THREAD_NAME, 100);
    underTest.start("start");
    underTest.message(() -> "Some message");
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(logTester.logs()).contains("start", "Some message"));
    underTest.stop("stop");
    assertThat(logTester.logs()).contains("start", "Some message", "stop");
  }

  private static boolean isDaemon(String name) {
    var t = getThread(name);
    return (t != null) && t.isDaemon();
  }

  private static boolean isThreadAlive(String name) {
    var t = getThread(name);
    return (t != null) && t.isAlive();
  }

  private static Thread getThread(String name) {
    var threads = Thread.getAllStackTraces().keySet();

    for (Thread t : threads) {
      if (t.getName().equals(name)) {
        return t;
      }
    }
    return null;
  }
}
