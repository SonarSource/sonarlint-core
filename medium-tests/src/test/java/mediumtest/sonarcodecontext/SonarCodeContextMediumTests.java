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
package mediumtest.sonarcodecontext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import utils.TestPlugin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService.SONARSOURCE_DOGFOODING_ENV_VAR_KEY;

@ExtendWith(SystemStubsExtension.class)
class SonarCodeContextMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SystemStub
  private EnvironmentVariables environmentVariables;

  @BeforeEach
  void clearDogfoodFlag() {
    environmentVariables.remove(SONARSOURCE_DOGFOODING_ENV_VAR_KEY);
  }

  @SonarLintTest
  // Relies on bash script
  @DisabledOnOs(OS.WINDOWS)
  void should_regenerate_on_binding_change(SonarLintTestHarness harness, @TempDir Path baseDir, @TempDir Path binDir)
    throws IOException {
    var cliPath = createFakeCli(binDir);
    System.setProperty("sonar.code.context.executable", cliPath.toString());
    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");

    var filePath = baseDir.resolve("Foo.java");
    Files.writeString(filePath, "public class Foo {}", UTF_8);
    var fileDto = new ClientFileDto(filePath.toUri(), baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true);

    var client = harness.newFakeClient()
      .withToken(CONNECTION_ID, "token")
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(fileDto))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(BackendCapability.CONTEXT_GENERATION)
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject(PROJECT_KEY, p -> p.withMainBranch("main")))
      .start(client);

    // Initial add triggers generation
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
        new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true)))));

    var sonarMd = baseDir.resolve(".sonar-code-context").resolve("SONAR.md");
    await().untilAsserted(() -> assertThat(Files.exists(sonarMd)).isTrue());

    // Remove SONAR.md to verify it is re-generated on binding change
    Files.deleteIfExists(sonarMd);
    assertThat(Files.exists(sonarMd)).isFalse();

    var newProjectKey = PROJECT_KEY + "-updated";
    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID,
      new BindingConfigurationDto(CONNECTION_ID, newProjectKey, true), null, null));

    await().untilAsserted(() -> assertThat(Files.exists(sonarMd)).isTrue());
  }

  @SonarLintTest
  // Relies on bash script
  @DisabledOnOs(OS.WINDOWS)
  void should_generate_sonar_md_and_mdc_on_bound_scope_when_dogfooding(SonarLintTestHarness harness, @TempDir Path baseDir, @TempDir Path binDir)
    throws IOException {
    // Arrange PATH with a fake 'sonar-code-context' CLI
    var cliPath = createFakeCli(binDir);
    // Force the service to use our fake CLI
    System.setProperty("sonar.code.context.executable", cliPath.toString());
    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");

    var filePath = baseDir.resolve("Foo.java");
    Files.writeString(filePath, "public class Foo {}", UTF_8);
    var fileDto = new ClientFileDto(filePath.toUri(), baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true);

    var client = harness.newFakeClient()
      .withToken(CONNECTION_ID, "token")
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(fileDto))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(BackendCapability.CONTEXT_GENERATION)
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject(PROJECT_KEY, p -> p.withMainBranch("main")))
      .start(client);

    // Add a bound configuration scope (triggers the event listener)
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
        new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true)))));

    var sonarMd = baseDir.resolve(".sonar-code-context").resolve("SONAR.md");
    var mdc = baseDir.resolve(".cursor").resolve("rules").resolve("sonar-code-context.mdc");
    await().untilAsserted(() -> assertThat(Files.exists(sonarMd)).isTrue());
    await().untilAsserted(() -> assertThat(Files.exists(mdc)).isTrue());
    var mdContent = Files.readString(sonarMd);
    assertThat(mdContent).contains("SONAR.md");
    var mdcContent = Files.readString(mdc);
    assertThat(mdcContent).contains("sonar-code-context.mdc");
    assertThat(Files.isExecutable(cliPath)).isTrue();
  }

  @SonarLintTest
  void should_not_generate_files_when_not_dogfooding(SonarLintTestHarness harness, @TempDir Path baseDir, @TempDir Path binDir)
    throws IOException {
    // Arrange PATH with a fake 'sonar-code-context' CLI, but do not set dogfooding flag
    var cliPath = createFakeCli(binDir);
    System.setProperty("sonar.code.context.executable", cliPath.toString());

    var filePath = baseDir.resolve("Foo.java");
    Files.writeString(filePath, "public class Foo {}", UTF_8);
    var fileDto = new ClientFileDto(filePath.toUri(), baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true);

    var client = harness.newFakeClient()
      .withToken(CONNECTION_ID, "token")
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(fileDto))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(BackendCapability.CONTEXT_GENERATION)
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject(PROJECT_KEY, p -> p.withMainBranch("main")))
      .start(client);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
        new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true)))));

    var sonarMd = baseDir.resolve(".sonar-code-context").resolve("SONAR.md");
    var mdc = baseDir.resolve(".cursor").resolve("rules").resolve("sonar-code-context.mdc");
    await().during(java.time.Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(Files.exists(sonarMd)).isFalse();
      assertThat(Files.exists(mdc)).isFalse();
    });
  }

  @SonarLintTest
  void should_not_generate_when_dogfood_enabled_but_capability_missing(SonarLintTestHarness harness, @TempDir Path baseDir, @TempDir Path binDir)
    throws IOException {
    var cliPath = createFakeCli(binDir);
    System.setProperty("sonar.code.context.executable", cliPath.toString());
    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");

    var filePath = baseDir.resolve("Foo.java");
    Files.writeString(filePath, "public class Foo {}", UTF_8);
    var fileDto = new ClientFileDto(filePath.toUri(), baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true);

    var client = harness.newFakeClient()
      .withToken(CONNECTION_ID, "token")
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(fileDto))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject(PROJECT_KEY, p -> p.withMainBranch("main")))
      .start(client);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
        new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true)))));

    var sonarMd = baseDir.resolve(".sonar-code-context").resolve("SONAR.md");
    var mdc = baseDir.resolve(".cursor").resolve("rules").resolve("sonar-code-context.mdc");
    await().during(java.time.Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(Files.exists(sonarMd)).isFalse();
      assertThat(Files.exists(mdc)).isFalse();
    });
  }

  private Path createFakeCli(Path binDir) throws IOException {
    Files.createDirectories(binDir);
    var cli = binDir.resolve("sonar-code-context");
    var content = """
      #!/usr/bin/env bash
      set -e
      cmd="$1"
      shift
      mkdir -p .sonar-code-context
      if [ "$cmd" = "init" ]; then
        echo '{"version":1}' > .sonar-code-context/settings.json
      elif [ "$cmd" = "generate-md-guidelines" ]; then
        echo "SONAR_GUIDELINES generated $*" > .sonar-code-context/SONAR_GUIDELINES.md
      elif [ "$cmd" = "merge-md" ]; then
        echo "SONAR.md merged" > .sonar-code-context/SONAR.md
      elif [ "$cmd" = "install" ]; then
        mkdir -p .cursor/rules
        echo "sonar-code-context.mdc generated $*" > .cursor/rules/sonar-code-context.mdc
      fi""";
    Files.writeString(cli, content, UTF_8);
    try {
      Files.setPosixFilePermissions(cli, Set.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException e) {
      // On non-POSIX FS (e.g., Windows CI), fallback to default and hope exec works via PATHEXT
    }
    return cli;
  }

}


