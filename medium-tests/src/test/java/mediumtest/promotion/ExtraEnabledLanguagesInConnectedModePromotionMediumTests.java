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
package mediumtest.promotion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExtraEnabledLanguagesInConnectedModePromotionMediumTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);
  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_notify_clients_for_a_detected_language_that_is_enabled_only_in_connected_mode(@TempDir Path tempDir) throws IOException {
    var abapFile = tempDir.resolve("file.abap");
    Files.createFile(abapFile);
    var fakeClient = newFakeClient()
      .withInitialFs("configScopeId", tempDir, List.of(new ClientFileDto(abapFile.toUri(), tempDir.relativize(abapFile), "configScopeId", false, null, abapFile, null, null, true)))
      .build();
    backend = newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.ABAP)
      .withUnboundConfigScope("configScopeId")
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams("configScopeId", UUID.randomUUID(), List.of(abapFile.toUri()), Map.of(), 0)).join();

    verify(fakeClient).promoteExtraEnabledLanguagesInConnectedMode("configScopeId", Set.of(Language.ABAP));
  }

  @Test
  void it_should_not_notify_clients_when_already_in_connected_mode(@TempDir Path tempDir) throws IOException {
    var abapFile = tempDir.resolve("file.abap");
    Files.createFile(abapFile);
    var fakeClient = newFakeClient()
      .withInitialFs("configScopeId", tempDir, List.of(new ClientFileDto(abapFile.toUri(), tempDir.relativize(abapFile), "configScopeId", false, null, abapFile, null, null, true)))
      .build();
    backend = newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.ABAP)
      .withSonarQubeConnection("connectionId", storage -> storage.withProject("projectKey"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams("configScopeId", UUID.randomUUID(), List.of(abapFile.toUri()), Map.of(), 0)).join();

    verify(fakeClient, after(200).never()).promoteExtraEnabledLanguagesInConnectedMode("configScopeId", Set.of(Language.ABAP));
  }

  @Test
  void it_should_not_notify_clients_when_detected_language_is_not_an_extra_language(@TempDir Path tempDir) throws IOException {
    var abapFile = tempDir.resolve("file.abap");
    Files.createFile(abapFile);
    var fakeClient = newFakeClient()
      .withInitialFs("configScopeId", tempDir, List.of(new ClientFileDto(abapFile.toUri(), tempDir.relativize(abapFile), "configScopeId", false, null, abapFile, null, null, true)))
      .build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.ABAP)
      .withUnboundConfigScope("configScopeId")
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams("configScopeId", UUID.randomUUID(), List.of(abapFile.toUri()), Map.of(), 0)).join();

    verify(fakeClient, after(200).never()).promoteExtraEnabledLanguagesInConnectedMode("configScopeId", Set.of(Language.ABAP));
  }

  @Test
  void it_should_not_notify_clients_when_no_language_was_detected_during_analysis(@TempDir Path tempDir) throws IOException {
    var randomFile = tempDir.resolve("file.abc");
    Files.createFile(randomFile);
    var fakeClient = newFakeClient()
      .withInitialFs("configScopeId", tempDir, List.of(new ClientFileDto(randomFile.toUri(), tempDir.relativize(randomFile), "configScopeId", false, null, randomFile, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope("configScopeId")
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams("configScopeId", UUID.randomUUID(), List.of(randomFile.toUri()), Map.of(), 0)).join();

    verify(fakeClient, after(200).never()).promoteExtraEnabledLanguagesInConnectedMode(eq("configScopeId"), anySet());
    verify(fakeClient, never()).log(argThat(logParams -> logParams.getLevel().equals(LogLevel.ERROR)));
  }
}
