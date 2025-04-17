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

import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.newSonarQubeServer;

class NotebookLanguageMediumTests {

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  public static final String SCOPE_ID = "scopeId";

  @SonarLintTest
  void should_not_enable_sync_for_notebook_python_language(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .build();
    var server = newSonarQubeServer()
      .withProject(JAVA_MODULE_KEY, project -> project.withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withPlugin(TestPlugin.JAVASCRIPT)
        .withPlugin(TestPlugin.PYTHON)
        .withProject(JAVA_MODULE_KEY))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withEnabledLanguageInStandaloneMode(Language.JS)
      .withEnabledLanguageInStandaloneMode(Language.IPYTHON)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);

    backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(CONNECTION_ID));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("[SYNC] Languages enabled for synchronization: [java, js]"));
  }

}
