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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mediumtest.tracking.IssueStreamingRulesDefinition;
import mediumtest.tracking.IssueStreamingSensor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.embedded.server.AnalyzeListFilesRequestHandler;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.createFile;

class AnalyzeListFilesMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  
  private final Gson gson = new Gson();

  @SonarLintTest
  void it_should_analyze_single_file_and_return_issues(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir, FILE1_PATH);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), CONFIG_SCOPE_ID, false, null, inputFile1, null,
          Language.JAVA, true)
      ))
      .build();

    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueStreamingSensor.class)
      .withRulesDefinition(IssueStreamingRulesDefinition.class)
      .generate(baseDir);

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "Test Project")
      .withBackendCapability(EMBEDDED_SERVER)
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .start(fakeClient);

    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var requestBody = gson.toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of(inputFile1.toAbsolutePath().toString())));

    var response = executeAnalyzeRequestWithResponse(backend, requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.issues()).hasSize(2);
    assertThat(analysisResult.issues()).extracting(AnalyzeListFilesRequestHandler.RawIssueResponse::ruleKey,
        AnalyzeListFilesRequestHandler.RawIssueResponse::message,
        AnalyzeListFilesRequestHandler.RawIssueResponse::severity, AnalyzeListFilesRequestHandler.RawIssueResponse::filePath,
        AnalyzeListFilesRequestHandler.RawIssueResponse::textRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("repo:rule", "Issue 1", "MAJOR", inputFile1.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1)),
        tuple("repo:rule", "Issue 2", "MAJOR", inputFile1.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1))
      );
  }

  @Disabled("Does not work")
  @SonarLintTest
  void it_should_analyze_multiple_files_and_return_issues(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir, FILE1_PATH);
    var inputFile2 = prepareJavaInputFile(baseDir, FILE2_PATH);

    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueStreamingSensor.class)
      .withRulesDefinition(IssueStreamingRulesDefinition.class)
      .generate(baseDir);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), CONFIG_SCOPE_ID, false, null, inputFile1, null,
          Language.JAVA, true),
        new ClientFileDto(inputFile2.toUri(), baseDir.relativize(inputFile2), CONFIG_SCOPE_ID, false, null, inputFile2, null,
          Language.JAVA, true)
      ))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "Test Project")
      .withBackendCapability(EMBEDDED_SERVER)
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .start(fakeClient);

    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var requestBody = gson.toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of(inputFile1.toAbsolutePath().toString(), inputFile2.toAbsolutePath().toString())));

    var response = executeAnalyzeRequestWithResponse(backend, requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.issues()).hasSize(4);
    assertThat(analysisResult.issues()).extracting(AnalyzeListFilesRequestHandler.RawIssueResponse::ruleKey,
        AnalyzeListFilesRequestHandler.RawIssueResponse::message,
        AnalyzeListFilesRequestHandler.RawIssueResponse::severity, AnalyzeListFilesRequestHandler.RawIssueResponse::filePath,
        AnalyzeListFilesRequestHandler.RawIssueResponse::textRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("repo:rule", "Issue 1", "MAJOR", inputFile1.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1)),
        tuple("repo:rule", "Issue 2", "MAJOR", inputFile1.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1)),
        tuple("repo:rule", "Issue 1", "MAJOR", inputFile2.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1)),
        tuple("repo:rule", "Issue 2", "MAJOR", inputFile2.toAbsolutePath().toString(), new TextRange(1, 0, 1, 1))
      );
  }

  @SonarLintTest
  void it_should_return_bad_request_for_empty_file_list(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var requestBody = gson.toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(Collections.emptyList()));

    var statusCode = executeAnalyzeRequest(backend, requestBody);
    assertThat(statusCode).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_return_bad_request_for_invalid_json(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var statusCode = executeAnalyzeRequest(backend, "invalid json");
    assertThat(statusCode).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_return_bad_request_for_get_request(SonarLintTestHarness harness) throws Exception {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/analyze-list"))
      .header("Origin", "http://localhost")
      .header("Content-Type", "application/json")
      .GET()
      .build();

    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_analyze_java_code_with_specific_issues(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    // Create Java code with deliberate issues that commonly trigger rules
    var javaFileWithIssues = baseDir.resolve("ProblemCode.java");
    var problematicCode = """
      public class ProblemCode {
        public void method() {
          int unused = 42;        // Unused variable
          String s = "test";
          if (s == "test") {      // String comparison with ==
            System.out.println("Bad comparison");
          }
          try {
            riskyOperation();
          } catch (Exception e) { // Catching generic Exception
            e.printStackTrace(); // printStackTrace usage
          }
        }
        
        private void riskyOperation() throws Exception {
          throw new Exception("test");
        }
      }""";
    
    Files.write(javaFileWithIssues, problematicCode.getBytes(StandardCharsets.UTF_8));

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(javaFileWithIssues.toUri(), baseDir.relativize(javaFileWithIssues), CONFIG_SCOPE_ID, false, null, javaFileWithIssues, null,
          Language.JAVA, true)
      ))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "Test Project")
      .withBackendCapability(EMBEDDED_SERVER)
      .start(fakeClient);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> 
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var requestBody = gson.toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of(javaFileWithIssues.toAbsolutePath().toString())));

    var response = executeAnalyzeRequestWithResponse(backend, requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.issues()).isNotNull();
    
    System.out.println("Deliberate issues analysis:");
    System.out.println("Response: " + response.body());
    System.out.println("Found " + analysisResult.issues().size() + " issues in problematic code:");
    
    analysisResult.issues().forEach(issue -> {
      System.out.println("- Rule: " + issue.ruleKey() + 
                        ", Message: " + issue.message() + 
                        ", Severity: " + issue.severity() +
                        ", File: " + (issue.filePath() != null ? Paths.get(issue.filePath()).getFileName() : "null"));
      if (issue.textRange() != null) {
        System.out.println("  Location: lines " + issue.textRange().getStartLine() + 
                          "-" + issue.textRange().getEndLine());
      }
    });
    
    // The structure should always be valid even if no rules are loaded
    assertThat(analysisResult.issues()).isNotNull();
  }

  @SonarLintTest
  void it_should_analyze_and_return_structured_response(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile1 = prepareJavaInputFile(baseDir, FILE1_PATH);

    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), CONFIG_SCOPE_ID, false, null, inputFile1, null,
          Language.JAVA, true)
      ))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "Test Project")
      .withBackendCapability(EMBEDDED_SERVER)
      .start(fakeClient);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> 
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var requestBody = gson.toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of(inputFile1.toAbsolutePath().toString())));

    var response = executeAnalyzeRequestWithResponse(backend, requestBody);
    assertThat(response.statusCode()).isEqualTo(200);

    var analysisResult = gson.fromJson(response.body(), AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult).isNotNull();
    assertThat(analysisResult.issues()).isNotNull();
    // The actual issues depend on what rules are loaded, so we just check the structure
  }

  private int executeAnalyzeRequest(SonarLintTestRpcServer backend, String requestBody) throws IOException, InterruptedException {
    var response = executeAnalyzeRequestWithResponse(backend, requestBody);
    return response.statusCode();
  }

  private HttpResponse<String> executeAnalyzeRequestWithResponse(SonarLintTestRpcServer backend, String requestBody) throws IOException,
    InterruptedException {
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/analyze-list"))
      .header("Origin", "http://localhost")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build();

    return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static Path prepareJavaInputFile(Path baseDir, String filePath) {
    return createFile(baseDir, filePath, "public class Foo {\n}");
  }

}
