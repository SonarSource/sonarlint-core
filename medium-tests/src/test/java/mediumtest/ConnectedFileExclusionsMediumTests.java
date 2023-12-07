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
package mediumtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mediumtest.fixtures.ServerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static testutils.TestUtils.protobufBody;

class ConnectedFileExclusionsMediumTests {

  private static final String MYSONAR = "mysonar";
  private static final String CONFIG_SCOPE_ID = "myProject1";
  private static final String PROJECT_KEY = "test-project-2";

  private SonarLintRpcServer backend;


  @AfterEach
  void stop() throws ExecutionException, InterruptedException, TimeoutException {
    backend.shutdown().get(5, TimeUnit.SECONDS);
  }

  @Test
  void fileInclusionsExclusions(@TempDir Path tmp) throws IOException, ExecutionException, InterruptedException {
    var server = newSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    var mainFile1 = tmp.resolve("foo.xoo");
    var mainFile1Dto = new ClientFileDto(mainFile1.toUri(), tmp.resolve(mainFile1), CONFIG_SCOPE_ID, false, StandardCharsets.UTF_8.name(), mainFile1, null);
    var mainFile2 = tmp.resolve("src/foo2.xoo");
    var mainFile2Dto = new ClientFileDto(mainFile2.toUri(), tmp.resolve(mainFile2), CONFIG_SCOPE_ID, false, StandardCharsets.UTF_8.name(), mainFile2, null);
    var testFile1 = tmp.resolve("fooTest.xoo");
    var testFile1Dto = new ClientFileDto(testFile1.toUri(), tmp.resolve(testFile1), CONFIG_SCOPE_ID, true, StandardCharsets.UTF_8.name(), testFile1, null);
    var testFile2 = tmp.resolve("test/foo2Test.xoo");
    var testFile2Dto = new ClientFileDto(testFile2.toUri(), tmp.resolve(testFile2), CONFIG_SCOPE_ID, true, StandardCharsets.UTF_8.name(), testFile2, null);

    var fakeClient = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID,
        List.of(mainFile1Dto, mainFile2Dto, testFile1Dto, testFile2Dto))
      .build();

    backend = newBackend()
      .withSonarQubeConnection(MYSONAR, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, MYSONAR, PROJECT_KEY)
      .withFullSynchronization()
      .build(fakeClient);

    var future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), false),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), false),
        tuple(testFile2.toUri(), false));

    mockSonarProjectSettings(server, Map.of("sonar.inclusions", "src/**"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), true),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), false),
        tuple(testFile2.toUri(), false));

    mockSonarProjectSettings(server, Map.of("sonar.inclusions", "file:**/src/**"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), true),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), false),
        tuple(testFile2.toUri(), false));

    mockSonarProjectSettings(server, Map.of("sonar.exclusions", "src/**"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), false),
        tuple(mainFile2.toUri(), true),
        tuple(testFile1.toUri(), false),
        tuple(testFile2.toUri(), false));

    mockSonarProjectSettings(server, Map.of("sonar.test.inclusions", "test/**"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), false),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), true),
        tuple(testFile2.toUri(), false));

    mockSonarProjectSettings(server, Map.of("sonar.test.exclusions", "test/**"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), false),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), false),
        tuple(testFile2.toUri(), true));

    mockSonarProjectSettings(server, Map.of("sonar.inclusions", "file:**/src/**", "sonar.test.exclusions", "**/*Test.*"));
    forceSyncOfConfigScope();

    future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(mainFile1.toUri(), mainFile2.toUri(), testFile1.toUri(), testFile2.toUri()))));
    assertThat(future.get().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(mainFile1.toUri(), true),
        tuple(mainFile2.toUri(), false),
        tuple(testFile1.toUri(), true),
        tuple(testFile2.toUri(), true));
  }

  private void forceSyncOfConfigScope() {
    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID, new BindingConfigurationDto(null, null, true)));
    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID, new BindingConfigurationDto(MYSONAR, PROJECT_KEY, true)));
  }

  private void mockSonarProjectSettings(ServerFixture.Server server, Map<String, String> settings) {
    var reponseBuilder = Settings.ValuesWsResponse.newBuilder();
    settings.forEach((k, v) -> reponseBuilder.addSettings(Settings.Setting.newBuilder()
      .setKey(k)
      .setValue(v)));
    server.getMockServer().stubFor(get("/api/settings/values.protobuf?component=" + PROJECT_KEY)
      .willReturn(aResponse().withResponseBody(protobufBody(reponseBuilder.build()))));
  }

}
