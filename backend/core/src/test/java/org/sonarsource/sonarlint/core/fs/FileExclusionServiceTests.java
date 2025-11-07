/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetFileExclusionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetFileExclusionsResponse;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfigurationStorage;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

class FileExclusionServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private ConfigurationRepository configRepo;
  private StorageService storageService;
  private ClientFileSystemService clientFileSystemService;
  private FileExclusionService underTest;
  private SonarLintRpcClient client;

  @BeforeEach
  void setup() {
    configRepo = mock(ConfigurationRepository.class);
    storageService = mock(StorageService.class);
    var pathTranslationService = mock(PathTranslationService.class);
    clientFileSystemService = mock(ClientFileSystemService.class);
    client = mock(SonarLintRpcClient.class);
    
    underTest = new FileExclusionService(configRepo, storageService, pathTranslationService, clientFileSystemService, client);
  }

  @Test
  void should_return_false_and_log_warning_when_analyzer_storage_is_not_valid() {
    var fileUri = URI.create("file:///path/to/file.java");
    var configScopeId = "configScope1";
    var connectionId = "connectionId";
    var projectKey = "projectKey";
    var clientFile = mock(ClientFile.class);

    when(clientFile.getConfigScopeId()).thenReturn(configScopeId);
    var binding = new Binding(connectionId, projectKey);

    var connectionStorage = mock(ConnectionStorage.class);
    var projectStorage = mock(SonarProjectStorage.class);
    var analyzerStorage = mock(AnalyzerConfigurationStorage.class);
    
    // Setup the mocks to return the invalid analyzer storage
    when(clientFileSystemService.getClientFile(fileUri)).thenReturn(clientFile);
    when(configRepo.getEffectiveBinding(configScopeId)).thenReturn(Optional.of(binding));
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionStorage.project(projectKey)).thenReturn(projectStorage);
    when(projectStorage.analyzerConfiguration()).thenReturn(analyzerStorage);
    when(analyzerStorage.isValid()).thenReturn(false); // This is the key setup for our test
    var cancelMonitor = mock(SonarLintCancelMonitor.class);

    var result = underTest.computeIfExcluded(fileUri, cancelMonitor);

    assertThat(result).isFalse();
    assertThat(logTester.logs()).contains("Unable to read settings in local storage, analysis storage is not ready");
  }

  @Test
  void should_return_false_when_no_client_file_found() {
    var fileUri = URI.create("file:///path/to/nonexistent.java");
    var cancelMonitor = mock(SonarLintCancelMonitor.class);
    when(clientFileSystemService.getClientFile(fileUri)).thenReturn(null);

    var result = underTest.computeIfExcluded(fileUri, cancelMonitor);

    assertThat(result).isFalse();
    assertThat(logTester.logs()).contains("Unable to find client file for uri file:///path/to/nonexistent.java");
  }

  @Test
  void should_return_false_when_no_effective_binding() {
    var fileUri = URI.create("file:///path/to/file.java");
    var configScopeId = "configScope1";
    var cancelMonitor = mock(SonarLintCancelMonitor.class);
    var clientFile = mock(ClientFile.class);
    when(clientFile.getConfigScopeId()).thenReturn(configScopeId);
    when(clientFileSystemService.getClientFile(fileUri)).thenReturn(clientFile);
    when(configRepo.getEffectiveBinding(configScopeId)).thenReturn(Optional.empty());

    var result = underTest.computeIfExcluded(fileUri, cancelMonitor);

    assertThat(result).isFalse();
  }

  @Test
  void should_filter_out_files_exceeding_5mb_in_auto_trigger() throws IOException {
    var configScopeId = "scope";
    var baseDir = Files.createTempDirectory("sl-auto-size-base");

    // Create a small file (~10 KB) and a large file (~6 MB)
    var smallFile = baseDir.resolve("small.js");
    var largeFile = baseDir.resolve("large.js");
    Files.write(smallFile, new byte[10 * 1024]);
    Files.write(largeFile, new byte[6 * 1024 * 1024]);

    var smallUri = smallFile.toUri();
    var largeUri = largeFile.toUri();

    var smallClientFile = mock(ClientFile.class);
    when(smallClientFile.getUri()).thenReturn(smallUri);
    when(smallClientFile.getClientRelativePath()).thenReturn(Paths.get("small.js"));
    when(smallClientFile.isUserDefined()).thenReturn(true);

    var largeClientFile = mock(ClientFile.class);
    when(largeClientFile.getUri()).thenReturn(largeUri);
    when(largeClientFile.getClientRelativePath()).thenReturn(Paths.get("large.js"));
    when(largeClientFile.isUserDefined()).thenReturn(true);

    when(clientFileSystemService.getClientFiles(configScopeId, smallUri)).thenReturn(smallClientFile);
    when(clientFileSystemService.getClientFiles(configScopeId, largeUri)).thenReturn(largeClientFile);
    when(client.getFileExclusions(any(GetFileExclusionsParams.class))).thenReturn(CompletableFuture.completedFuture(new GetFileExclusionsResponse(Collections.emptySet())));

    // Avoid interference from server-side exclusions
    var spy = Mockito.spy(underTest);
    Mockito.doReturn(false).when(spy).isExcludedFromServer(any(URI.class));

    var result = spy.refineAnalysisScope(configScopeId, Set.of(smallUri, largeUri), TriggerType.AUTO, baseDir);

    assertThat(result).extracting(ClientFile::getUri).containsExactlyInAnyOrder(smallUri);
    assertThat(logTester.logs()).anySatisfy(s -> assertThat(s).contains("Filtered out URIs exceeding max allowed size"));
  }

  @Test
  void should_not_filter_out_large_files_in_forced_trigger() throws IOException {
    var configScopeId = "scope";
    var baseDir = Files.createTempDirectory("sl-forced-size-base");

    var largeFile = baseDir.resolve("large2.js");
    Files.write(largeFile, new byte[6 * 1024 * 1024]);

    var largeUri = largeFile.toUri();

    var largeClientFile = mock(ClientFile.class);
    when(largeClientFile.getUri()).thenReturn(largeUri);
    when(largeClientFile.getClientRelativePath()).thenReturn(Paths.get("large2.js"));
    when(largeClientFile.isUserDefined()).thenReturn(true);

    when(clientFileSystemService.getClientFiles(configScopeId, largeUri)).thenReturn(largeClientFile);
    when(client.getFileExclusions(any(GetFileExclusionsParams.class))).thenReturn(CompletableFuture.completedFuture(new GetFileExclusionsResponse(Collections.emptySet())));

    var spy = Mockito.spy(underTest);
    Mockito.doReturn(false).when(spy).isExcludedFromServer(any(URI.class));

    var result = spy.refineAnalysisScope(configScopeId, Set.of(largeUri), TriggerType.FORCED, baseDir);

    assertThat(result).extracting(ClientFile::getUri).containsExactly(largeUri);
  }

}
