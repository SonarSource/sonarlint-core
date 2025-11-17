/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class BlameParserTests {

  @Test
  void shouldNotPopulateGitBlameResultForEmptyBlameOutput() {
    var gitBlameReader = new GitBlameReader();

    gitBlameReader.readLine("");

    assertThat(gitBlameReader.getResult().lineCommitDates())
      .isEmpty();
  }

  @Test
  void shouldSplitBlameOutputCorrectlyWhenLinesContainSplitPattern() {
    var blameOutput = """
      5746f09bf53067450843eaddff52ea7b0f16cde3 1 1 2
      author Some One
      author-mail <some.one@sonarsource.com>
      author-time 1553598120
      author-tz +0100
      committer Some One
      committer-mail <some.one@sonarsource.com>
      committer-time 1554191055
      committer-tz +0200
      summary Initial revision
      previous 35c9ca0b1f41231508e706707d76ca0485b8a3ad file.txt
      filename file.txt
              First line with filename in it
      5746f09bf53067450843eaddff52ea7b0f16cde3 2 2
      author Some One
      author-mail <some.one@sonarsource.com>
      author-time 1553598120
      author-tz +0100
      committer Some One
      committer-mail <some.one@sonarsource.com>
      committer-time 1554191057
      committer-tz +0200
      summary Initial revision
      previous 35c9ca0b1f41231508e706707d76ca0485b8a3ad file.txt
      filename file.txt
              Second line also with filename in it
      """;
    var gitBlameReader = new GitBlameReader();
    var blameLines = blameOutput.split("\\n");

    for (String blameLine : blameLines) {
      gitBlameReader.readLine(blameLine);
    }

    assertThat(gitBlameReader.getResult().lineCommitDates())
      .containsExactly(Instant.ofEpochSecond(1554191055), Instant.ofEpochSecond(1554191057));
  }
}
