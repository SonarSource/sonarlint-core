/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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

import java.util.Set;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.ProgressReport;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ProgressReportTest {
  private static final String THREAD_NAME = "progress";
  private ProgressReport progressReport;

  @Rule
  public LogTester logTester = new LogTester();

  @Before
  public void setUp() {
    progressReport = new ProgressReport(THREAD_NAME, 100);
  }

  @AfterClass
  public static void after() {
    // to avoid conflicts with SonarLintLogging
    new LogTester().setLevel(LoggerLevel.TRACE);

  }

  @Test
  public void die_on_stop() {
    progressReport.start("start");
    assertThat(isThreadAlive(THREAD_NAME)).isTrue();
    progressReport.stop("stop");
    assertThat(isThreadAlive(THREAD_NAME)).isFalse();
  }

  @Test
  public void accept_no_stop_msg() {
    progressReport.start("start");
    assertThat(isThreadAlive(THREAD_NAME)).isTrue();
    progressReport.stop(null);
    assertThat(isThreadAlive(THREAD_NAME)).isFalse();
  }

  @Test
  public void do_not_block_app() {
    progressReport.start("start");
    assertThat(isDaemon(THREAD_NAME)).isTrue();
    progressReport.stop("stop");
  }

  @Test
  public void do_log() {
    progressReport.start("start");
    progressReport.message(() -> "Some message");
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(logTester.logs()).contains("start", "Some message"));
    progressReport.stop("stop");
    assertThat(logTester.logs()).contains("start", "Some message", "stop");
  }

  private static boolean isDaemon(String name) {
    Thread t = getThread(name);
    return (t != null) && t.isDaemon();
  }

  private static boolean isThreadAlive(String name) {
    Thread t = getThread(name);
    return (t != null) && t.isAlive();
  }

  private static Thread getThread(String name) {
    Set<Thread> threads = Thread.getAllStackTraces().keySet();

    for (Thread t : threads) {
      if (t.getName().equals(name)) {
        return t;
      }
    }
    return null;
  }
}
