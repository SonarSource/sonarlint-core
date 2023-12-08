/*
 * SonarLint Core - Medium Tests
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
package mediumtest.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import testutils.ConsoleConsumer;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;

class NodeJsMediumTests {

  private static final String JAVASCRIPT_S1481 = "javascript:S1481";

  private Path sonarlintUserHome;
  private SonarLintTestRpcServer backend;
  private Path fakeTypeScriptProjectPath;
  private Path baseDir;

  @BeforeEach
  void prepare(@TempDir Path temp) throws Exception {
    sonarlintUserHome = temp.resolve("home");
    fakeTypeScriptProjectPath = temp.resolve("ts");
    baseDir = temp.resolve("basedir");
    var packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    var pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .redirectErrorStream(true);
    var process = pb.start();
    new Thread(new ConsoleConsumer(process)).start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

  }

  @AfterEach
  void stop() throws IOException {
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void wrong_node_path_can_still_load_rules() throws Exception {
    var client = newFakeClient().build();
    backend = newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .withClientNodeJsPath(Paths.get("wrong"))
      .build(client);

    var globalConfig = backend.getAnalysisService().getGlobalStandaloneConfiguration().join();

    assertThat(globalConfig.getNodeJsPath()).isEqualTo(Paths.get("wrong"));
    assertThat(globalConfig.getNodeJsVersion()).isNull();

    assertThat(client.getLogMessages()).contains("Unable to query node version", "Node.js path set to: wrong (version null)");

    verify(client, Mockito.timeout(5000)).didChangeNodeJs(Paths.get("wrong"), null);

    var ruleDetails = backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams(JAVASCRIPT_S1481)).join().getRuleDefinition();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS);
    assertThat(ruleDetails.getSeverity()).isEqualTo(IssueSeverity.MINOR);
  }

}
