/*
 * SonarLint Core - Medium Tests
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
package mediumtest.rules;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;

@ExtendWith(LogTestStartAndEnd.class)
public class RuleExtractionMediumTests {

  @SonarLintTest
  void should_gracefully_skip_plugin_when_rules_definitions_fail(SonarLintTestHarness harness, @TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient()
      .build();

    // First plugin actually declares a PHP rule
    var okPluginDir = baseDir.resolve("ok-plugin");
    okPluginDir.toFile().mkdirs();
    var okPluginPath = newSonarPlugin("ok-plugin")
      .withRulesDefinition(OkRulesDefinition.class)
      .generate(okPluginDir);

    // Second plugin throws an IllegalStateException when defining rules
    var throwingPluginDir = baseDir.resolve("throwing-plugin");
    throwingPluginDir.toFile().mkdirs();
    var throwingPluginPath = newSonarPlugin("throwing-plugin")
      .withRulesDefinition(ThrowingRulesDefinition.class)
      .generate(throwingPluginDir);

    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(okPluginPath)
      .withStandaloneEmbeddedPlugin(throwingPluginPath)
      .withEnabledLanguageInStandaloneMode(Language.PHP)
      .start(client);

    var allRules = backend.getRulesService().listAllStandaloneRulesDefinitions().get();

    assertThat(allRules.getRulesByKey()).containsOnlyKeys("ok-rules:S001");
  }

}
