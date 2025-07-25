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
package mediumtest.synchronization;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.PluginLocator;
import utils.TestPlugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.UP;

@ExtendWith(LogTestStartAndEnd.class)
class PluginSynchronizationMediumTests {

  @SonarLintTest
  void it_should_pull_plugins_at_startup_from_the_server(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withStatus(UP)
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start();

    waitAtMost(20, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder(backend))
        .isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-java-plugin-" + TestPlugin.JAVA.getVersion() + ".jar"));
      assertThat(getPluginReferencesFilePath(backend))
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-" + TestPlugin.JAVA.getVersion() + ".jar").setKey("java")
            .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build()));
    });
  }

  @SonarLintTest
  void it_should_not_pull_plugins_if_server_is_down(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withStatus(DOWN)
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder(backend)).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  @SonarLintTest
  void it_should_not_pull_already_pulled_plugin(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder(backend))
        .isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-java-plugin-" + TestPlugin.JAVA.getVersion() + ".jar"));
      assertThat(getPluginReferencesFilePath(backend))
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-" + TestPlugin.JAVA.getVersion() + ".jar").setKey("java")
            .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build()));
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is up-to-date. Skip downloading it.");
    });
  }

  @SonarLintTest
  void it_should_pull_a_plugin_if_already_pulled_but_hash_is_different(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(TestPlugin.JAVA.getPluginKey(), TestPlugin.JAVA.getPath(), "differentHash"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPluginReferencesFilePath(backend))
      .exists()
      .extracting(this::readPluginReferences, as(MAP))
      .containsOnly(
        entry("java", PluginReference.newBuilder().setFilename("sonar-java-plugin-" + TestPlugin.JAVA.getVersion() + ".jar").setKey("java")
          .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH).build())));
  }

  @SonarLintTest
  void it_should_not_pull_plugins_that_do_not_support_sonarlint(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin("pluginKey", plugin -> plugin.withJarPath(TestPlugin.JAVA.getPath()).withHash(TestPlugin.JAVA.getHash()).withSonarLintSupported(false))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      File[] files = getPluginsStorageFolder(backend).toFile().listFiles();
      assertThat(files).hasSize(1);
      assertThat(files[0]).hasName(PluginsStorage.PLUGIN_REFERENCES_PB);
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'pluginKey' does not support SonarLint. Skip downloading it.");
    });
  }

  @SonarLintTest
  void it_should_not_pull_embedded_plugins(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      File[] files = getPluginsStorageFolder(backend).toFile().listFiles();
      assertThat(files).hasSize(1);
      assertThat(files[0]).hasName(PluginsStorage.PLUGIN_REFERENCES_PB);
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is embedded in SonarLint. Skip downloading it.");
    });
  }

  @SonarLintTest
  void it_should_not_pull_plugins_for_not_enabled_languages(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin(TestPlugin.JAVA)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      File[] files = getPluginsStorageFolder(backend).toFile().listFiles();
      assertThat(files).hasSize(1);
      assertThat(files[0]).hasName(PluginsStorage.PLUGIN_REFERENCES_PB);
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'java' is disabled in SonarLint (language not enabled). Skip downloading it.");
    });
  }

  @SonarLintTest
  void it_should_pull_third_party_plugins_for_custom_rules(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin("java-custom", plugin -> plugin.withJarPath(Path.of("java-custom-plugin-4.3.0.1456.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder(backend)).isDirectoryContaining(path -> path.getFileName().toString().equals("java-custom-plugin-4.3.0.1456.jar"));
      assertThat(getPluginReferencesFilePath(backend))
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("java-custom",
            PluginReference.newBuilder().setFilename("java-custom-plugin-4.3.0.1456.jar").setKey("java-custom").setHash("de5308f43260d357acc97712ce4c5475").build()));
    });
  }

  @SonarLintTest
  void it_should_pull_the_old_typescript_plugin_if_language_enabled(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin("typescript", plugin -> plugin.withJarPath(Path.of("sonar-typescript-plugin-1.9.0.3766.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.TS)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getPluginsStorageFolder(backend)).isDirectoryContaining(path -> path.getFileName().toString().equals("sonar-typescript-plugin-1.9.0.3766.jar"));
      assertThat(getPluginReferencesFilePath(backend))
        .exists()
        .extracting(this::readPluginReferences, as(MAP))
        .containsOnly(
          entry("typescript",
            PluginReference.newBuilder().setFilename("sonar-typescript-plugin-1.9.0.3766.jar").setKey("typescript").setHash("de5308f43260d357acc97712ce4c5475").build()));
    });
  }

  @SonarLintTest
  void it_should_not_pull_the_old_typescript_plugin_if_language_not_enabled(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin("typescript", plugin -> plugin.withJarPath(Path.of("sonar-typescript-plugin-1.9.0.3766.jar")).withHash("de5308f43260d357acc97712ce4c5475"))
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      File[] files = getPluginsStorageFolder(backend).toFile().listFiles();
      assertThat(files).hasSize(1);
      assertThat(files[0]).hasName(PluginsStorage.PLUGIN_REFERENCES_PB);
      assertThat(client.getLogMessages()).contains("[SYNC] Code analyzer 'typescript' is disabled in SonarLint (language not enabled). Skip downloading it.");
    });
  }

  @SonarLintTest
  void it_should_clean_up_plugins_that_are_no_longer_relevant(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withPlugin(TestPlugin.PHP)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(TestPlugin.JAVA))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(Language.PHP)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    assertThat(getPluginsStorageFolder(backend).toFile().listFiles())
      .extracting(File::getName)
      .containsOnly(PluginsStorage.PLUGIN_REFERENCES_PB, TestPlugin.PHP.getPath().getFileName().toString());
    assertThat(client.getLogMessages()).contains("Cleaning up the plugins storage " + getPluginsStorageFolder(backend) + ", removing 1 unknown files:");
    assertThat(getPluginReferencesFilePath(backend))
      .exists()
      .extracting(this::readPluginReferences, as(MAP))
      .containsOnlyKeys("php");
  }

  @NotNull
  private Map<String, PluginReference> readPluginReferences(Path filePath) {
    return ProtobufFileUtil.readFile(filePath, Sonarlint.PluginReferences.parser()).getPluginsByKeyMap();
  }

  @NotNull
  private Path getPluginsStorageFolder(SonarLintTestRpcServer backend) {
    return backend.getStorageRoot().resolve(encodeForFs("connectionId")).resolve("plugins");
  }

  @NotNull
  private Path getPluginReferencesFilePath(SonarLintTestRpcServer backend) {
    return getPluginsStorageFolder(backend).resolve("plugin_references.pb");
  }
}
