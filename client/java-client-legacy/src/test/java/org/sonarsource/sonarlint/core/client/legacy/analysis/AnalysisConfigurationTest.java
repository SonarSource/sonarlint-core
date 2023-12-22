/*
 * SonarLint Core - Java Client Legacy
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.legacy.testutils.OnDiskTestClientInputFile;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisConfigurationTest {
  @Test
  void it_should_generate_a_readable_toString() {
    var filePath = Paths.get("filePath");
    var configuration = AnalysisConfiguration.builder()
      .addInputFile(new OnDiskTestClientInputFile(filePath, "relativePath", false, StandardCharsets.UTF_8, SonarLanguage.ABAP))
      .putAllExtraProperties(Map.of("key", "value"))
      .setBaseDir(Paths.get("baseDir"))
      .setModuleKey("moduleKey")
      .build();

    var string = configuration.toString();

    assertThat(string).isEqualTo("[\n" +
      "  baseDir: baseDir\n" +
      "  extraProperties: {key=value}\n" +
      "  moduleKey: moduleKey\n" +
      "  inputFiles: [\n" +
      "    " + filePath.toUri() + " (UTF-8) [abap]\n" +
      "  ]\n" +
      "]\n");
  }

}