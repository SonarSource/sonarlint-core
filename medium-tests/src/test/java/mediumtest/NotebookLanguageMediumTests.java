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
package mediumtest;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;

class NotebookLanguageMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";

  @SonarLintTest
  void should_not_enable_sync_for_notebook_python_language(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .build();
    var backend = harness.newBackend()
      .withStorage(CONNECTION_ID, s -> s.withPlugins(TestPlugin.JAVASCRIPT, TestPlugin.JAVA)
        .withProject("test-project")
        .withProject(JAVA_MODULE_KEY))
      .build(fakeClient);

    var serverConnection = new ServerConnection(backend.getStorageRoot(), CONNECTION_ID, false, Set.of(SonarLanguage.JAVA, SonarLanguage.JS,
      SonarLanguage.IPYTHON), Set.of(), backend.getWorkDir());

    assertThat(serverConnection.getEnabledLanguagesToSync()).containsOnly(SonarLanguage.JAVA, SonarLanguage.JS);
  }

}
