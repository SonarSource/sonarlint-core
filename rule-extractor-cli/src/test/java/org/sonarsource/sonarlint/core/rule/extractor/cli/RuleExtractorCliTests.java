/*
 * SonarLint Core - Rule Extractor - CLI
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
package org.sonarsource.sonarlint.core.rule.extractor.cli;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorCliTests {

  @Test
  void shouldReturnSuccessfullyIfNoArguments() {
    var exitCode = RuleExtractorCli.execute();
    assertThat(exitCode).isZero();
  }

  @Test
  void shouldWriteEmptyToFile(@TempDir Path outputDir) {
    var outputFile = outputDir.resolve("out.json");
    var exitCode = RuleExtractorCli.execute("--output", outputFile.toString());
    assertThat(exitCode).isZero();
    assertThat(outputFile).content().isEqualTo("[]");
  }

  @Test
  void shouldExitWithErrorCode() {
    var exitCode = RuleExtractorCli.execute("--output", "invalid / \\ file name");
    assertThat(exitCode).isNotZero();
  }

}