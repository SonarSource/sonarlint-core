/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration.Builder;
import testutils.TestClientInputFile;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectedAnalysisConfigurationTests {

  @Test
  void testToString(@TempDir Path temp) throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.java.libraries", "foo bar");

    final var srcFile1 = createDirectory(temp.resolve("src1"));
    final var srcFile2 = createDirectory(temp.resolve("src2"));

    ClientInputFile inputFile = new TestClientInputFile(temp, srcFile1, false, StandardCharsets.UTF_8, null);
    ClientInputFile testInputFile = new TestClientInputFile(temp, srcFile2, true, StandardCharsets.UTF_8, null);

    var baseDir = createDirectory(temp.resolve("baseDir"));
    var config = ConnectedAnalysisConfiguration.builder()
      .setProjectKey("foo")
      .setBaseDir(baseDir)
      .addInputFiles(inputFile, testInputFile)
      .putAllExtraProperties(props)
      .build();
    assertThat(config).hasToString("[\n" +
      "  projectKey: foo\n" +
      "  baseDir: " + baseDir.toString() + "\n" +
      "  extraProperties: {sonar.java.libraries=foo bar}\n" +
      "  moduleKey: null\n" +
      "  inputFiles: [\n" +
      "    " + srcFile1.toUri().toString() + " (UTF-8)\n" +
      "    " + srcFile2.toUri().toString() + " (UTF-8) [test]\n" +
      "  ]\n" +
      "]\n");
    assertThat(config.baseDir()).isEqualTo(baseDir);
    assertThat(config.inputFiles()).containsExactly(inputFile, testInputFile);
    assertThat(config.getProjectKey()).isEqualTo("foo");
    assertThat(config.extraProperties()).containsExactly(entry("sonar.java.libraries", "foo bar"));
  }

  @Test
  void test_projectKey_is_mandatory() {
    Builder builder = ConnectedAnalysisConfiguration.builder();
    var e = assertThrows(IllegalStateException.class, builder::build);
    assertThat(e).hasMessage("'projectKey' is mandatory");
  }
}
