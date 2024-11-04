/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;

import static org.assertj.core.api.Assertions.assertThat;

class PluginsServiceTest {

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_language_requirements_passed() {
    var underTest = new PluginsService.CSharpSupport(null);
    assertThat(underTest.csharpOssPluginPath).isNull();
    assertThat(underTest.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_omnisharp_requirements_passed() {
    var underTest = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, null));
    assertThat(underTest.csharpOssPluginPath).isNull();
    assertThat(underTest.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_paths_when_omnisharp_requirements_passed(@TempDir Path tempDir) {
    var monoPath = tempDir.resolve("mono");
    var net6Path = tempDir.resolve("net6Path");
    var net472Path = tempDir.resolve("net472Path");
    var ossPath = tempDir.resolve("ossPath");
    var enterprisePath = tempDir.resolve("enterprisePath");
    var underTest = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, new OmnisharpRequirementsDto(monoPath, net6Path, net472Path, ossPath, enterprisePath)));
    assertThat(underTest.csharpOssPluginPath).isEqualTo(ossPath);
    assertThat(underTest.csharpEnterprisePluginPath).isEqualTo(enterprisePath);
  }
}
