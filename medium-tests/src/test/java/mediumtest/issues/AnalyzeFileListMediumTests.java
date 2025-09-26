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
package mediumtest.issues;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.embedded.server.AnalyzeFileListRequestHandler;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerTaintIssueFixtures.aServerTaintIssue;
import static utils.AnalysisUtils.createFile;

class AnalyzeFileListMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Bar.java";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  
  private final Gson gson = new Gson();

  @SonarLintTest
  void it_should_analyze_single_file_and_return_issues_and_taints(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), CONFIG_SCOPE_ID, false, null, inputFile1, null,
          Language.JAVA, true)
      ))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java")
        .withActiveRule("java:S1220", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR))
        .withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR)))
      .withProject("projectKey",
        project -> project
          .withQualityProfile("qpKey")
          .withMainBranch("main"))
      .withPlugin(TestPlugin.JAVA)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(FULL_SYNCHRONIZATION, EMBEDDED_SERVER)
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withTaintIssue(aServerTaintIssue("key")
              .withFilePath(inputFile1.toAbsolutePath().toString())
              .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withClientName("client")
      .start(fakeClient);

    fakeClient.waitForSynchronization();

    var requestBody = gson.toJson(new AnalyzeFileListRequestHandler.AnalyzeFileListRequest(
      List.of(inputFile1.toAbsolutePath().normalize().toString()
      )));

    var response = executeAnalyzeRequest(backend, server.baseUrl(), requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeFileListRequestHandler.AnalyzeFileListResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.findings()).hasSize(3);
    assertThat(analysisResult.findings()).extracting(AnalyzeFileListRequestHandler.RawFindingResponse::ruleKey,
        AnalyzeFileListRequestHandler.RawFindingResponse::message,
        AnalyzeFileListRequestHandler.RawFindingResponse::severity,
        AnalyzeFileListRequestHandler.RawFindingResponse::textRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(
        tuple("java:S1220", "Move this file to a named package.", "MINOR", null),
        tuple("java:S2094", "Remove this empty class, write its code or make it an \"interface\".", "MINOR",
          new TextRange(1, 13, 1, 16)),
        tuple("ruleKey", "message", "MEDIUM", new TextRange(1, 2, 3, 4))
      );
  }

  @SonarLintTest
  void it_should_analyze_multiple_files_and_return_issues(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir);
    var inputFile2 = prepareJavaInputFile2(baseDir);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), CONFIG_SCOPE_ID, false, null, inputFile1, null,
          Language.JAVA, true),
        new ClientFileDto(inputFile2.toUri(), baseDir.relativize(inputFile2), CONFIG_SCOPE_ID, false, null, inputFile2, null,
          Language.JAVA, true)
      ))
      .build();

    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java")
        .withActiveRule("java:S1220", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR))
        .withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR)))
      .withProject("projectKey",
        project -> project
          .withQualityProfile("qpKey")
          .withMainBranch("main"))
      .withPlugin(TestPlugin.JAVA)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(FULL_SYNCHRONIZATION, EMBEDDED_SERVER)
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withClientName("client")
      .start(fakeClient);

    fakeClient.waitForSynchronization();

    var requestBody = gson.toJson(new AnalyzeFileListRequestHandler.AnalyzeFileListRequest(
      List.of(inputFile1.toAbsolutePath().normalize().toString(),
        inputFile2.toAbsolutePath().normalize().toString()
      )));

    var response = executeAnalyzeRequest(backend, server.baseUrl(), requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeFileListRequestHandler.AnalyzeFileListResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.findings()).hasSize(4);
    assertThat(analysisResult.findings()).extracting(AnalyzeFileListRequestHandler.RawFindingResponse::ruleKey,
        AnalyzeFileListRequestHandler.RawFindingResponse::message,
        AnalyzeFileListRequestHandler.RawFindingResponse::severity,
        AnalyzeFileListRequestHandler.RawFindingResponse::textRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", "Move this file to a named package.", "MINOR", null),
        tuple("java:S2094", "Remove this empty class, write its code or make it an \"interface\".", "MINOR",
          new TextRange(1, 13, 1, 16)),
        tuple("java:S1220", "Move this file to a named package.", "MINOR", null),
        tuple("java:S2094", "Remove this empty class, write its code or make it an \"interface\".", "MINOR",
          new TextRange(1, 13, 1, 16))
      );
  }

  @SonarLintTest
  void it_should_return_error_when_no_files_found_to_be_indexed(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir);

    var fakeClient = harness.newFakeClient().build();

    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java")
        .withActiveRule("java:S1220", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR))
        .withActiveRule("java:S2094", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR)))
      .withProject("projectKey",
        project -> project
          .withQualityProfile("qpKey")
          .withMainBranch("main"))
      .withPlugin(TestPlugin.JAVA)
      .start();

    var backend = harness.newBackend()
      .withBackendCapability(FULL_SYNCHRONIZATION, EMBEDDED_SERVER)
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withTaintIssue(aServerTaintIssue("key")
              .withFilePath(inputFile1.toAbsolutePath().toString())
              .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withClientName("client")
      .start(fakeClient);

    fakeClient.waitForSynchronization();

    var requestBody = gson.toJson(new AnalyzeFileListRequestHandler.AnalyzeFileListRequest(
      List.of(inputFile1.toAbsolutePath().normalize().toString())
    ));

    var response = executeAnalyzeRequest(backend, server.baseUrl(), requestBody);
    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.body()).isEqualTo("Failed to analyze files, reason: No files were found to be indexed by SonarQube for IDE");
  }

  @SonarLintTest
  void it_should_return_bad_request_for_empty_file_list(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var requestBody = gson.toJson(new AnalyzeFileListRequestHandler.AnalyzeFileListRequest(Collections.emptyList()));

    var response = executeAnalyzeRequest(backend, "", requestBody);
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_return_bad_request_for_invalid_json(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var response = executeAnalyzeRequest(backend, "", "invalid json");
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_return_bad_request_for_get_request(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/analysis/files"))
      .header("Origin", "http://localhost")
      .header("Content-Type", "application/json; charset=utf-8")
      .GET()
      .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  private HttpResponse<String> executeAnalyzeRequest(SonarLintTestRpcServer backend, String baseUrl, String requestBody) throws IOException,
    InterruptedException {
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/analysis/files"))
      .header("Origin", baseUrl)
      .header("Content-Type", "application/json; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build();

    // Print request sent
    System.out.println("Sending request: " + request);
    System.out.println("Sending request to: " + request.uri());
    System.out.println("Request body sent: " + requestBody);
    System.out.println("Using Origin: " + baseUrl);

    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static Path prepareJavaInputFile(Path baseDir) {
    return createFile(baseDir, FILE1_PATH, "public class Foo {\n}");
  }

  private static Path prepareJavaInputFile2(Path baseDir) {
    return createFile(baseDir, FILE2_PATH, "public class Bar {\n}");
  }

}
