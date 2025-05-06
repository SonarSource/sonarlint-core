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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commitAtDate;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.modifyFile;

class NativeGitWrapperTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static NativeGitWrapper underTest;
  @TempDir
  private Path projectDirPath;
  private Git git;

  @BeforeEach
  void prepare() throws Exception {
    git = createRepository(projectDirPath);
    underTest = spy(new NativeGitWrapper());
  }

  @AfterEach
  void cleanup() throws IOException {
    org.eclipse.jgit.util.FileUtils.delete(projectDirPath.toFile(), RECURSIVE);
  }

  @Test
  void shouldConsiderNativeGitNotAvailableOnNull() {
    doReturn(Optional.empty()).when(underTest).getGitExecutable();

    assertThat(underTest.checkIfNativeGitEnabled(Path.of(""))).isFalse();
  }

  @Test
  void it_should_default_to_instant_now_git_blame_history_limit_if_older_than_one_year() throws IOException, GitAPIException {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.add(Calendar.YEAR, -2);
    var fileAStr = "fileA";
    createFile(projectDirPath, fileAStr, "line1");
    var yearAgo = calendar.toInstant();
    // initial commit 2 years ago
    commitAtDate(git, yearAgo, fileAStr);
    var lines = new String[3];

    // second commit 4 months after initial commit
    calendar.add(Calendar.MONTH, 4);
    lines[0] = "line1";
    lines[1] = "line2";
    var eightMonthsAgo = calendar.toInstant();
    modifyFile(projectDirPath.resolve(fileAStr), lines);
    commitAtDate(git, eightMonthsAgo, fileAStr);

    // third commit 4 months after second commit
    calendar.add(Calendar.MONTH, 4);
    lines[2] = "line3";
    var oneYearAndFourMonthsAgo = calendar.toInstant();
    modifyFile(projectDirPath.resolve(fileAStr), lines);
    commitAtDate(git, oneYearAndFourMonthsAgo, fileAStr);
    var fileA = Path.of(fileAStr);

    var blameResult = underTest.blameFromNativeCommand(projectDirPath, Set.of(projectDirPath.resolve(fileA).toUri()), Instant.now());

    var line1Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(1)).get();
    var line2Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(2)).get();
    var line3Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(3)).get();

    assertThat(ChronoUnit.MINUTES.between(line1Date, oneYearAndFourMonthsAgo)).isZero();
    assertThat(ChronoUnit.MINUTES.between(line2Date, oneYearAndFourMonthsAgo)).isZero();
    assertThat(ChronoUnit.MINUTES.between(line3Date, oneYearAndFourMonthsAgo)).isZero();
  }

  @Test
  void it_should_blame_file_since_effective_blame_period() throws IOException, GitAPIException {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.add(Calendar.MONTH, -18);
    var fileAStr = "fileA";
    createFile(projectDirPath, fileAStr, "line1");
    var yearAgo = calendar.toInstant();
    // initial commit 1 year ago
    commitAtDate(git, yearAgo, fileAStr);
    var lines = new String[3];

    // second commit 4 months after initial commit
    calendar.add(Calendar.MONTH, 4);
    lines[0] = "line1";
    lines[1] = "line2";
    var eightMonthsAgo = calendar.toInstant();
    modifyFile(projectDirPath.resolve(fileAStr), lines);
    commitAtDate(git, eightMonthsAgo, fileAStr);

    // third commit 4 months after second commit
    calendar.add(Calendar.MONTH, 4);
    lines[2] = "line3";
    var fourMonthsAgo = calendar.toInstant();
    modifyFile(projectDirPath.resolve(fileAStr), lines);
    commitAtDate(git, fourMonthsAgo, fileAStr);
    var fileA = Path.of(fileAStr);

    var blameResult = underTest.blameFromNativeCommand(projectDirPath, Set.of(projectDirPath.resolve(fileA).toUri()), Instant.now().minus(Period.ofDays(180)));

    var line1Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(1)).get();
    var line2Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(2)).get();
    var line3Date = blameResult.getLatestChangeDateForLinesInFile(fileA, List.of(3)).get();
    // provided blame time limit is 180 days, but effective period will be 1 year
    // line 1 was committed 1 year ago but should have commit date of the first commit made earlier than blame time window - 8 months ago
    assertThat(ChronoUnit.MINUTES.between(line2Date, line1Date)).isZero();
    // line 2 was committed 8 months ago, it's outside the blame time window, but it's a first commit outside the range, so it has real commit date
    assertThat(ChronoUnit.MINUTES.between(line2Date, eightMonthsAgo)).isZero();
    // line 3 was committed 4 months ago, it's inside the blame time window, so it has real commit date
    assertThat(ChronoUnit.MINUTES.between(line3Date, fourMonthsAgo)).isZero();
  }

  @Test
  void it_should_not_blame_file_on_git_command_error() {
    assumeTrue(new NativeGitWrapper().getNativeGitExecutable().isPresent());
    var fileAStr = "fileA";
    var fileA = projectDirPath.resolve(fileAStr);

    var blameResult = underTest.blameFromNativeCommand(projectDirPath, Set.of(fileA.toUri()), Instant.now());

    assertThat(logTester.logs()).contains("Command failed with code: 128 and output fatal: no such path 'fileA' in HEAD");
    assertThat(blameResult.isEmpty()).isTrue();
  }
}
