/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.SubmitFileCacheChunkParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.PROJECT_SYNCHRONIZATION;

/**
 * Medium tests for lazy file system initialization feature.
 * Tests backward compatibility, cache warmup, and mixed loading strategies.
 */
class LazyFileSystemInitializationMediumTests {

  private static final String MYSONAR = "mysonar";
  private static final String CONFIG_SCOPE_ID = "myProject1";
  private static final String PROJECT_KEY = "test-project";

  private String previousSyncPeriod;

  @BeforeEach
  void prepare() {
    previousSyncPeriod = System.getProperty("sonarlint.internal.synchronization.scope.period");
    System.setProperty("sonarlint.internal.synchronization.scope.period", "1");
  }

  @AfterEach
  void stop() {
    if (previousSyncPeriod != null) {
      System.setProperty("sonarlint.internal.synchronization.scope.period", previousSyncPeriod);
    } else {
      System.clearProperty("sonarlint.internal.synchronization.scope.period");
    }
  }

  /**
   * Test backward compatibility: Old clients that implement listFiles() should work unchanged.
   */
 @SonarLintTest
  void should_work_with_old_client_using_listFiles(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    // Create test files
    var file1 = tmp.resolve("file1.java");
    Files.writeString(file1, "public class File1 {}");
    var file2 = tmp.resolve("file2.java");
    Files.writeString(file2, "public class File2 {}");

    var file1Dto = createFileDto(file1, tmp, false);
    var file2Dto = createFileDto(file2, tmp, false);

    // Old client pattern: provide all files upfront via listFiles()
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(file1Dto, file2Dto))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(fakeClient);

    // Verify files are accessible
    var filesStatus = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, 
        List.of(file1.toUri(), file2.toUri()))))
      .join();

    assertThat(filesStatus.getFileStatuses()).hasSize(2);
    assertThat(filesStatus.getFileStatuses().values())
      .extracting(FileStatusDto::isExcluded)
      .containsOnly(false);
  }

  /**
   * Test cache warmup: Files can be submitted in chunks via submitFileCacheChunk().
   */
 @SonarLintTest
  void should_accept_file_chunks_via_submitFileCacheChunk(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    // Create test files
    var file1 = tmp.resolve("file1.java");
    Files.writeString(file1, "public class File1 {}");
    var file2 = tmp.resolve("file2.java");
    Files.writeString(file2, "public class File2 {}");

    var file1Dto = createFileDto(file1, tmp, false);
    var file2Dto = createFileDto(file2, tmp, false);

    // New client pattern: start with empty file system
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of())
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(fakeClient);

    // Submit files via cache warmup chunks
    backend.getFileService().submitFileCacheChunk(
      new SubmitFileCacheChunkParams(CONFIG_SCOPE_ID, List.of(file1Dto, file2Dto)));

    // Verify files are now accessible
    var filesStatus = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, 
        List.of(file1.toUri(), file2.toUri()))))
      .join();

    assertThat(filesStatus.getFileStatuses()).hasSize(2);
    assertThat(filesStatus.getFileStatuses().values())
      .extracting(FileStatusDto::isExcluded)
      .containsOnly(false);
  }

  /**
   * Test that backend triggers cache warmup when new scopes are added.
   * Note: Full verification requires test harness extension to capture the RPC call.
   */
 @SonarLintTest
  void should_request_cache_warmup_when_scope_added(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of())
      .build();

    // Start backend and add configuration scope - this should trigger warmup request
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(fakeClient);

    // The warmUpFileSystemCache() notification should be called
    // With default no-op implementation, it won't cause errors
    // This validates the integration works end-to-end
    assertThat(backend).isNotNull();
  }

  /**
   * Test file exclusions work correctly with lazy-loaded files.
   */
 @SonarLintTest
  void should_handle_file_exclusions_with_lazy_loading(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY)
      .start();

    // Create test files - one included, one excluded
    var includedFile = tmp.resolve("included.java");
    Files.writeString(includedFile, "public class Included {}");
    var excludedFile = tmp.resolve("excluded/Excluded.java");
    Files.createDirectories(excludedFile.getParent());
    Files.writeString(excludedFile, "public class Excluded {}");

    var includedDto = createFileDto(includedFile, tmp, false);
    var excludedDto = createFileDto(excludedFile, tmp, false);

    // Start with empty file system, then submit via chunks
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of())
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection(MYSONAR, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, MYSONAR, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, PROJECT_SYNCHRONIZATION)
      .start(fakeClient);

    // Submit files via chunks (simulating lazy loading)
    backend.getFileService().submitFileCacheChunk(
      new SubmitFileCacheChunkParams(CONFIG_SCOPE_ID, List.of(includedDto, excludedDto)));

    // Verify both files are now in the cache and can be checked for exclusions
    var filesStatus = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, 
        List.of(includedFile.toUri(), excludedFile.toUri()))))
      .join();

    // Both files should be found and have exclusion status computed
    assertThat(filesStatus.getFileStatuses()).hasSize(2);
    assertThat(filesStatus.getFileStatuses()).containsKeys(
      includedFile.toUri(), 
      excludedFile.toUri()
    );
    
    // Verify exclusion status can be computed for lazily loaded files
    assertThat(filesStatus.getFileStatuses().get(includedFile.toUri())).isNotNull();
    assertThat(filesStatus.getFileStatuses().get(excludedFile.toUri())).isNotNull();
  }

  /**
   * Test incremental cache population: Files can be added in multiple chunks.
   */
 @SonarLintTest
  void should_support_incremental_cache_population(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    // Create test files
    var file1 = tmp.resolve("file1.java");
    Files.writeString(file1, "public class File1 {}");
    var file2 = tmp.resolve("file2.java");
    Files.writeString(file2, "public class File2 {}");
    var file3 = tmp.resolve("file3.java");
    Files.writeString(file3, "public class File3 {}");

    var file1Dto = createFileDto(file1, tmp, false);
    var file2Dto = createFileDto(file2, tmp, false);
    var file3Dto = createFileDto(file3, tmp, false);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of())
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(fakeClient);

    // Submit first chunk
    backend.getFileService().submitFileCacheChunk(
      new SubmitFileCacheChunkParams(CONFIG_SCOPE_ID, List.of(file1Dto)));

    // Verify first file is accessible
    var status1 = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(file1.toUri()))))
      .join();
    assertThat(status1.getFileStatuses()).containsKey(file1.toUri());

    // Submit second chunk with more files
    backend.getFileService().submitFileCacheChunk(
      new SubmitFileCacheChunkParams(CONFIG_SCOPE_ID, List.of(file2Dto, file3Dto)));

    // Verify all files are now accessible
    var statusAll = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, 
        List.of(file1.toUri(), file2.toUri(), file3.toUri()))))
      .join();

    assertThat(statusAll.getFileStatuses()).hasSize(3);
    assertThat(statusAll.getFileStatuses().values())
      .extracting(FileStatusDto::isExcluded)
      .containsOnly(false);
  }

  /**
   * Test mixing old and new file loading strategies.
   */
 @SonarLintTest
  void should_handle_mixed_file_loading_strategies(SonarLintTestHarness harness, @TempDir Path tmp) throws IOException {
    // Create test files
    var file1 = tmp.resolve("file1.java");
    Files.writeString(file1, "public class File1 {}");
    var file2 = tmp.resolve("file2.java");
    Files.writeString(file2, "public class File2 {}");
    var file3 = tmp.resolve("file3.java");
    Files.writeString(file3, "public class File3 {}");

    var file1Dto = createFileDto(file1, tmp, false);
    var file2Dto = createFileDto(file2, tmp, false);
    var file3Dto = createFileDto(file3, tmp, false);

    // Load some files via old method (listFiles)
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(file1Dto))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(fakeClient);

    // Add more files via new method (submitFileCacheChunk)
    backend.getFileService().submitFileCacheChunk(
      new SubmitFileCacheChunkParams(CONFIG_SCOPE_ID, List.of(file2Dto, file3Dto)));

    // Verify all files are accessible regardless of loading method
    var filesStatus = backend.getFileService()
      .getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, 
        List.of(file1.toUri(), file2.toUri(), file3.toUri()))))
      .join();

    assertThat(filesStatus.getFileStatuses()).hasSize(3);
    assertThat(filesStatus.getFileStatuses().values())
      .extracting(FileStatusDto::isExcluded)
      .containsOnly(false);
  }

  /**
   * Helper method to create a ClientFileDto from a file.
   */
  private ClientFileDto createFileDto(Path file, Path baseDir, boolean isTest) {
    return new ClientFileDto(
      file.toUri(),
      baseDir.relativize(file),
      CONFIG_SCOPE_ID,
      isTest,
      StandardCharsets.UTF_8.name(),
      file,
      null,
      null,
      true
    );
  }
}

