/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NotebookLanguageMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  public static final String SCOPE_ID = "scopeId";
  private static SonarLintTestRpcServer backend;
  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;

  @BeforeAll
  static void prepare() {
    client = newFakeClient()
      .build();

    var server = newSonarQubeServer()
      .withProject(JAVA_MODULE_KEY, project -> project.withBranch("main"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withPlugin(TestPlugin.JAVASCRIPT)
        .withPlugin(TestPlugin.PYTHON)
        .withProject(JAVA_MODULE_KEY))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withEnabledLanguageInStandaloneMode(Language.JS)
      .withEnabledLanguageInStandaloneMode(Language.IPYTHON)
      .withFullSynchronization()
      .build(client);
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_not_enable_sync_for_notebook_python_language() {
    backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams(CONNECTION_ID));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("[SYNC] Languages enabled for synchronization: [java, js]"));
  }

}
