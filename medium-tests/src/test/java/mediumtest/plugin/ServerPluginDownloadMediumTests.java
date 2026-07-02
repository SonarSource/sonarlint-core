/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.plugin;

import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class ServerPluginDownloadMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String ORGANIZATION_KEY = "org";
  private static final String PROJECT_KEY = "projectKey";
  private static final String PLUGIN_DOWNLOAD_URL = "/api/plugins/download?plugin=java";

  @SonarLintTest
  void failed_server_plugin_download_should_not_trigger_infinite_reload_loop_for_sonarqube_cloud_us_region(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization(ORGANIZATION_KEY, organization -> organization.withProject(PROJECT_KEY, project -> project.withBranch("main")))
      .withPlugin(TestPlugin.JAVA)
      .start();
    // Keep the failing response in flight briefly so a regression would have time to rearm
    // another download attempt while the first one is still completing.
    server.getMockServer().stubFor(get(urlEqualTo(PLUGIN_DOWNLOAD_URL))
      .atPriority(1)
      .willReturn(aResponse().withStatus(404).withFixedDelay(200)));

    var backend = harness.newBackend()
      .withSonarQubeCloudUsRegionUri(server.baseUrl())
      .withSonarQubeCloudUsRegionApiUri(server.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, ORGANIZATION_KEY, "US", true, storage -> storage
        .withProject(PROJECT_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S106", "MAJOR"))
          .withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .start();

    try {
      // We expect exactly one failed download attempt. The `during` window proves the count stays
      // stable for a while instead of entering the old reload/download loop.
      await().during(2, SECONDS).atMost(15, SECONDS)
        .untilAsserted(() -> assertThat(countPluginDownloadRequests(server)).isEqualTo(1));
    } finally {
      harness.shutdown(backend);
    }
  }

  private static long countPluginDownloadRequests(ServerFixture.Server server) {
    return server.getMockServer().getAllServeEvents().stream()
      .filter(event -> PLUGIN_DOWNLOAD_URL.equals(event.getRequest().getUrl()))
      .count();
  }

}
