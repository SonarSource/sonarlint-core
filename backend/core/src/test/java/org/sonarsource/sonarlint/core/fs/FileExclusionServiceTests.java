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

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfigurationStorage;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileExclusionServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private ConfigurationRepository configRepo;
  private StorageService storageService;
  private ClientFileSystemService clientFileSystemService;
  private FileExclusionService underTest;

  @BeforeEach
  void setup() {
    configRepo = mock(ConfigurationRepository.class);
    storageService = mock(StorageService.class);
    var pathTranslationService = mock(PathTranslationService.class);
    clientFileSystemService = mock(ClientFileSystemService.class);
    var client = mock(SonarLintRpcClient.class);
    
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

} 
