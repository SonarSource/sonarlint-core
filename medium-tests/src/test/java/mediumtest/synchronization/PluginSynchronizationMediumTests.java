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
package mediumtest.synchronization;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import testutils.PluginLocator;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.ServerFixture.ServerStatus.UP;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class PluginSynchronizationMediumTests {

  @Test
  void it_should_pull_plugins_at_startup_from_the_server() {
    var server = newSonarQubeServer("10.3")
      .withStatus(UP)
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build();

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-java-plugin-7.15.0.30507.jar"));
      assertThat(getPluginReferencesFilePath())
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-7.15.0.30507.jar").setKey("java").setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build()));
    });
  }

  @Test
  void it_should_notify_clients_when_plugins_have_been_pulled() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPluginsStorageFolder()).isNotEmptyDirectory());
    verify(client, timeout(2000)).didUpdatePlugins("connectionId");
  }

  @Test
  void it_should_not_pull_plugins_if_server_is_down() {
    var server = newSonarQubeServer("10.3")
      .withStatus(DOWN)
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  @Test
  void it_should_not_pull_already_pulled_plugin() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-java-plugin-7.15.0.30507.jar"));
      assertThat(getPluginReferencesFilePath())
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-7.15.0.30507.jar").setKey("java").setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build()));
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is up-to-date. Skip downloading it.");
    });
  }

  @Test
  void it_should_pull_a_plugin_if_already_pulled_but_hash_is_different() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(TestPlugin.JAVA.getPluginKey(), TestPlugin.JAVA.getPath(), "differentHash"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPluginReferencesFilePath())
      .exists()
      .extracting(this::readPluginReferences, as(MAP))
      .containsOnly(
        entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-7.15.0.30507.jar").setKey("java").setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build())));
  }

  @Test
  void it_should_not_pull_plugins_that_do_not_support_sonarlint() {
    var server = newSonarQubeServer("10.3")
      .withPlugin("pluginKey", plugin -> plugin.withJarPath(TestPlugin.JAVA.getPath()).withHash(TestPlugin.JAVA.getHash()).withSonarLintSupported(false))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'pluginKey' does not support SonarLint. Skip downloading it.");
    });
  }

  @Test
  void it_should_not_pull_plugins_with_unsupported_version() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA.getPluginKey(), plugin -> plugin.withJarPath(Path.of("sonar-java-plugin-5.12.0.jar")).withHash(TestPlugin.JAVA.getHash()))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' version '5.12.0' is not supported (minimal version is '5.13.1.18282'). Skip downloading it.");
    });
  }

  @Test
  void it_should_not_pull_embedded_plugins() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is embedded in SonarLint. Skip downloading it.");
    });
  }

  @Test
  void it_should_not_pull_plugins_for_not_enabled_languages() {
    var server = newSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is disabled in SonarLint (language not enabled). Skip downloading it.");
    });
  }

  @Test
  void it_should_pull_third_party_plugins_for_custom_rules() {
    var server = newSonarQubeServer("10.3")
      .withPlugin("java-custom", plugin -> plugin.withJarPath(Path.of("java-custom-plugin-4.3.0.1456.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).isDirectoryContaining(path -> path.getFileName().toString().equals("java-custom-plugin-4.3.0.1456.jar"));
      assertThat(getPluginReferencesFilePath())
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java-custom",
            PluginReference.newBuilder().setFilename("java-custom-plugin-4.3.0.1456.jar").setKey("java-custom").setHash("de5308f43260d357acc97712ce4c5475").build()));
    });
  }

  @Test
  void it_should_pull_the_old_typescript_plugin_if_language_enabled() {
    var server = newSonarQubeServer("10.3")
      .withPlugin("typescript", plugin -> plugin.withJarPath(Path.of("sonar-typescript-plugin-1.9.0.3766.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.TS)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-typescript-plugin-1.9.0.3766.jar"));
      assertThat(getPluginReferencesFilePath())
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("typescript",
            PluginReference.newBuilder().setFilename("sonar-typescript-plugin-1.9.0.3766.jar").setKey("typescript").setHash("de5308f43260d357acc97712ce4c5475").build()));
    });
  }

  @Test
  void it_should_not_pull_the_old_typescript_plugin_if_language_not_enabled() {
    var server = newSonarQubeServer("10.3")
      .withPlugin("typescript", plugin -> plugin.withJarPath(Path.of("sonar-typescript-plugin-1.9.0.3766.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder()).doesNotExist();
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'typescript' is disabled in SonarLint (language not enabled). Skip downloading it.");
    });
  }

  @NotNull
  private Map<String, PluginReference> readPluginReferences(Path filePath) {
    return ProtobufFileUtil.readFile(filePath, Sonarlint.PluginReferences.parser()).getPluginsByKeyMap();
  }

  @NotNull
  private Path getPluginsStorageFolder() {
    return backend.getStorageRoot().resolve(encodeForFs("connectionId")).resolve("plugins");
  }

  @NotNull
  private Path getPluginReferencesFilePath() {
    return getPluginsStorageFolder().resolve("plugin_references.pb");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private SonarLintTestRpcServer backend;
}
