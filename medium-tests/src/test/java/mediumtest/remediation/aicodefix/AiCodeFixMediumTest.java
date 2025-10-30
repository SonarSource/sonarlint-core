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
package mediumtest.remediation.aicodefix;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;
import org.sonarsource.sonarlint.core.commons.storage.repository.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.telemetry.TelemetryFixSuggestionReceivedCounter;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams;
import static org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.FILE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.ISSUE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.TOO_MANY_REQUESTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.serverapi.ServerApiHelper.HTTP_TOO_MANY_REQUESTS;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerTaintIssueFixtures.aServerTaintIssue;
import static utils.AnalysisUtils.analyzeFileAndGetIssue;
import static utils.AnalysisUtils.createFile;

public class AiCodeFixMediumTest {
  public static final String XML_SOURCE_CODE_WITH_ISSUE = """
    <?xml version="1.0" encoding="UTF-8"?>
    <project>
      <modelVersion>4.0.0</modelVersion>
      <groupId>com.foo</groupId>
      <artifactId>bar</artifactId>
      <version>${pom.version}</version>
    </project>""";

  @SonarLintTest
  void it_should_fail_if_the_configuration_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("configScope")
      .start();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", UUID.randomUUID()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(CONFIG_SCOPE_NOT_BOUND, "The provided configuration scope is not bound");
  }

  @SonarLintTest
  void it_should_fail_if_the_configuration_scope_is_bound_to_an_unknown_connection(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", UUID.randomUUID()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(CONNECTION_NOT_FOUND, "The provided configuration scope is bound to an unknown connection");
  }

  @SonarLintTest
  void it_should_fail_if_the_issue_is_unknown(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer().start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey"))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start();
    var issueId = UUID.randomUUID();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issueId));

    assertThat(future).failsWithin(Duration.of(2, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(ISSUE_NOT_FOUND, "The provided issue or taint does not exist");
  }

  @SonarLintTest
  void it_should_fail_if_the_file_is_unknown(SonarLintTestHarness harness, @TempDir Path baseDir) throws InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey", project -> project
          .withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code"))))
      .start();

    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true).enabledForAllProjects()))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(), List.of(fileUri)));
    // leave time for the notification to be received by the backend
    Thread.sleep(300);

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(FILE_NOT_FOUND, "The provided issue ID corresponds to an unknown file");
  }

  @SonarLintTest
  void it_should_fail_if_the_issue_is_not_fixable_because_at_file_level(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var sourceCode = "public interface Fubar\n" +
      "{}";
    var filePath = createFile(baseDir, "Fubar.java", sourceCode);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey", project -> project
          .withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
        .withAiCodeFixSettings(settings -> settings.organizationEligible(true).enabledForAllProjects()))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");
    assertThat(issue.isAiCodeFixable()).isFalse();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(InvalidParams.getValue(), "The provided issue cannot be fixed");
  }

  @SonarLintTest
  void it_should_fail_if_the_issue_is_not_fixable_because_the_organization_is_not_eligible(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var sourceCode = "public interface Fubar\n" +
      "{}";
    var filePath = createFile(baseDir, "Fubar.java", sourceCode);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey", project -> project
          .withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
        .withAiCodeFixSettings(settings -> settings.organizationEligible(false)))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");
    assertThat(issue.isAiCodeFixable()).isFalse();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(InvalidParams.getValue(), "The provided issue cannot be fixed");
  }

  @SonarLintTest
  void it_should_fail_if_the_issue_is_not_fixable_because_the_feature_is_globally_disabled(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var sourceCode = "public interface Fubar\n" +
      "{}";
    var filePath = createFile(baseDir, "Fubar.java", sourceCode);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
        .withAiCodeFixSettings(settings -> settings.organizationEligible(true).disabled()))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");
    assertThat(issue.isAiCodeFixable()).isFalse();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(InvalidParams.getValue(), "The provided issue cannot be fixed");
  }

  @SonarLintTest
  void it_should_fail_if_the_issue_is_not_fixable_because_the_feature_is_disabled_for_the_project(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var sourceCode = "public interface Fubar\n" +
      "{}";
    var filePath = createFile(baseDir, "Fubar.java", sourceCode);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
        .withAiCodeFixSettings(settings -> settings.organizationEligible(true).enabledForProjects("otherProjectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");
    assertThat(issue.isAiCodeFixable()).isFalse();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(InvalidParams.getValue(), "The provided issue cannot be fixed");
  }

  @SonarLintTest
  void it_should_mark_the_issue_as_not_fixable_if_not_bound(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withUnboundConfigScope("configScope")
      .start(fakeClient);

    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    assertThat(issue.isAiCodeFixable()).isFalse();
  }

  @SonarLintTest
  void it_should_mark_the_issue_as_not_fixable_if_bound_to_sonarqube_server(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("branchName"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);

    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    assertThat(issue.isAiCodeFixable()).isFalse();
  }

  @SonarLintTest
  void it_should_mark_the_issue_as_not_fixable_if_rule_not_supported(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S0000"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);

    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    assertThat(issue.isAiCodeFixable()).isFalse();
  }

  @SonarLintTest
  void it_should_mark_the_issue_as_fixable_if_supported(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);

    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    assertThat(issue.isAiCodeFixable()).isTrue();
  }

  @SonarLintTest
  void it_should_throw_too_many_requests_when_fixing_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .withResponseCodes(codes -> codes.withStatusCode(HTTP_TOO_MANY_REQUESTS))
      .start();

    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId()));

    assertThat(future).failsWithin(Duration.ofSeconds(3))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(TOO_MANY_REQUESTS,
        "AI CodeFix usage has been capped. Too many requests have been made.");
  }

  @SonarLintTest
  void it_should_throw_too_many_requests_when_fixing_taint_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "File.java", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .withResponseCodes(codes -> codes.withStatusCode(HTTP_TOO_MANY_REQUESTS))
      .start();

    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project
          .withMainBranch("branchName", branch -> branch.withTaintIssue(aServerTaintIssue("key")
            .withRuleKey("javasecurity:S2076")
            .withFilePath("File.java")
            .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")))))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("javasecurity:S2076"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var listAllResponse = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams("configScope")).join();
    var taintVulnerabilityDto = listAllResponse.getTaintVulnerabilities().get(0);

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", taintVulnerabilityDto.getId()));

    assertThat(future).failsWithin(Duration.ofSeconds(3))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(TOO_MANY_REQUESTS,
        "AI CodeFix usage has been capped. Too many requests have been made.");
  }

  @SonarLintTest
  void it_should_return_the_suggestion_from_sonarqube_cloud_for_an_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    var fixSuggestion = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId())).join();

    assertThat(fixSuggestion.getId()).isEqualTo(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"));
    assertThat(fixSuggestion.getExplanation()).isEqualTo("This is the explanation");
    assertThat(fixSuggestion.getChanges())
      .extracting(SuggestFixChangeDto::getStartLine, SuggestFixChangeDto::getEndLine, SuggestFixChangeDto::getNewCode)
      .containsExactly(tuple(0, 0, "This is the new code"));
    assertThat(server.getMockServer().getAllServeEvents().get(0).getRequest().getBodyAsString())
      .isEqualTo(
        """
          {"organizationKey":"organizationKey","projectKey":"projectKey","issue":{"message":"Replace \\"pom.version\\" with \\"project.version\\".","startLine":6,"endLine":6,"ruleKey":"xml:S3421","sourceCode":"%s"}}"""
          .formatted(XML_SOURCE_CODE_WITH_ISSUE.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")));
  }

  @SonarLintTest
  void it_should_return_the_suggestion_from_sonarqube_cloud_for_a_taint_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "File.java", "source");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project
          .withMainBranch("main", branch -> branch.withTaintIssue(aServerTaintIssue("key")
            .withRuleKey("javasecurity:S2076")
            .withFilePath("File.java")
            .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")))))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("javasecurity:S2076"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var listAllResponse = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams("configScope")).join();
    var taintVulnerabilityDto = listAllResponse.getTaintVulnerabilities().get(0);

    var fixSuggestion = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", taintVulnerabilityDto.getId())).join();

    assertThat(fixSuggestion.getId()).isEqualTo(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"));
    assertThat(fixSuggestion.getExplanation()).isEqualTo("This is the explanation");
    assertThat(fixSuggestion.getChanges())
      .extracting(SuggestFixChangeDto::getStartLine, SuggestFixChangeDto::getEndLine, SuggestFixChangeDto::getNewCode)
      .containsExactly(tuple(0, 0, "This is the new code"));
    assertThat(server.getMockServer().getAllServeEvents().get(0).getRequest().getBodyAsString())
      .isEqualTo(
        """
          {"organizationKey":"organizationKey","projectKey":"projectKey","issue":{"message":"message","startLine":1,"endLine":3,"ruleKey":"javasecurity:S2076","sourceCode":"source"}}""");
  }

  @SonarLintTest
  void it_should_fail_if_the_taint_issue_is_not_fixable_because_rule_is_not_supported(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "File.java", "source");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project
          .withMainBranch("main", branch -> branch.withTaintIssue(aServerTaintIssue("key")
            .withRuleKey("javasecurity:S2076")
            .withFilePath("File.java")
            .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")))))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("javasecurity:SXXXX"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var listAllResponse = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams("configScope")).join();
    var taintVulnerabilityDto = listAllResponse.getTaintVulnerabilities().get(0);

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", taintVulnerabilityDto.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(InvalidParams.getValue(), "The provided taint cannot be fixed");
  }

  @SonarLintTest
  void it_should_fail_if_the_taint_issue_is_not_fixable_because_file_was_removed(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "File.java", "source");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project
          .withMainBranch("main", branch -> branch.withTaintIssue(aServerTaintIssue("key")
            .withRuleKey("javasecurity:S2076")
            .withFilePath("OtherFile.java")
            .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")))))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("javasecurity:S2076"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var listAllResponse = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams("configScope")).join();
    var taintVulnerabilityDto = listAllResponse.getTaintVulnerabilities().get(0);

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", taintVulnerabilityDto.getId()));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(FILE_NOT_FOUND, "The provided taint ID corresponds to an unknown file");
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_from_sq_cloud_when_disabled(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withAiCodeFixSupportedRules(Set.of("xml:S3421"))
      .withOrganization("organizationKey", organization -> organization
        .withAiCodeFixFeature(feature -> feature
          .organizationEligible(true)
          .disabled())
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .contains(new AiCodeFix("connectionId", Set.of("xml:S3421"),
        true, AiCodeFix.Enablement.DISABLED, Set.of())));
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_from_sq_cloud_when_enabled_for_some_projects(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withAiCodeFixSupportedRules(Set.of("xml:S3421"))
      .withOrganization("organizationKey", organization -> organization
        .withAiCodeFixFeature(feature -> feature
          .organizationEligible(true)
          .enabledForProjects("projectKey"))
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .contains(new AiCodeFix("connectionId", Set.of("xml:S3421"),
        true, AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS, Set.of("projectKey"))));
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_from_sq_cloud_when_enabled_for_all_projects(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withAiCodeFixSupportedRules(Set.of("xml:S3421"))
      .withOrganization("organizationKey", organization -> organization
        .withAiCodeFixFeature(feature -> feature
          .organizationEligible(true)
          .enabledForAllProjects())
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .contains(new AiCodeFix("connectionId", Set.of("xml:S3421"),
        true, AiCodeFix.Enablement.ENABLED_FOR_ALL_PROJECTS, Set.of())));
  }

  @SonarLintTest
  void it_should_not_synchronize_the_ai_codefix_settings_for_sq_server_older_then_2025_3(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer("2025.2")
      .withFeature("fix-suggestions")
      .withProject("projectKey",
        project -> project
          .withBranch("branchName"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();

    await().untilAsserted(() -> assertThat(getAiCodeFixStorageFilePath(backend, "connectionId"))
      .doesNotExist());
  }

  @SonarLintTest
  void it_should_not_synchronize_the_ai_codefix_settings_for_sq_server_without_feature(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer("2025.3")
      .withProject("projectKey",
        project -> project
          .withBranch("branchName"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();

    await().untilAsserted(() -> assertThat(getAiCodeFixStorageFilePath(backend, "connectionId"))
      .doesNotExist());
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_for_sq_server(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer("2025.3")
      .withAiCodeFixSupportedRules(Set.of("xml:S3421"))
      .withFeature("fix-suggestions")
      .withProject("projectKey", project -> project
        .withAiCodeFixEnabled(true))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project
          .withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .contains(new AiCodeFix("connectionId", Set.of("xml:S3421"), true,
        ENABLED_FOR_SOME_PROJECTS, Set.of("projectKey"))));
  }

  @SonarLintTest
  void it_should_return_the_suggestion_from_sonarqube_server_for_an_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project
          .withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    var fixSuggestion = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId())).join();

    assertThat(fixSuggestion.getId()).isEqualTo(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"));
    assertThat(fixSuggestion.getExplanation()).isEqualTo("This is the explanation");
    assertThat(fixSuggestion.getChanges())
      .extracting(SuggestFixChangeDto::getStartLine, SuggestFixChangeDto::getEndLine, SuggestFixChangeDto::getNewCode)
      .containsExactly(tuple(0, 0, "This is the new code"));
    assertThat(server.getMockServer().getAllServeEvents().get(0).getRequest().getBodyAsString())
      .isEqualTo(
        """
          {"projectKey":"projectKey","issue":{"message":"Replace \\"pom.version\\" with \\"project.version\\".","startLine":6,"endLine":6,"ruleKey":"xml:S3421","sourceCode":"%s"}}"""
          .formatted(XML_SOURCE_CODE_WITH_ISSUE.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")));
  }

  @SonarLintTest
  void it_should_register_telemetry_for_sonarqube_cloud(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey",
          project -> project
            .withBranch("branchName")
            .withAiCodeFixSuggestion(suggestion -> suggestion
              .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
              .withExplanation("This is the explanation")
              .withChange(0, 0, "This is the new code"))))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId())).join();

    assertThat(backend.telemetryFileContent().getFixSuggestionReceivedCounter())
      .isEqualTo(Map.of("e51b7bbd-72bc-4008-a4f1-d75583f3dc98", new TelemetryFixSuggestionReceivedCounter(AiSuggestionSource.SONARCLOUD, 1, true)));
  }

  @SonarLintTest
  void it_should_register_telemetry_for_sonarqube_server(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project
          .withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId())).join();

    assertThat(backend.telemetryFileContent().getFixSuggestionReceivedCounter())
      .isEqualTo(Map.of("e51b7bbd-72bc-4008-a4f1-d75583f3dc98", new TelemetryFixSuggestionReceivedCounter(AiSuggestionSource.SONARQUBE, 1, true)));
  }

  @SonarLintTest
  void it_should_skip_synchronization_if_user_not_a_member_of_the_organization(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer()
      .withAiCodeFixSupportedRules(Set.of("xml:S3421"))
      .withOrganization("organizationKey", organization -> organization
        .withCurrentUserMember(false)
        .withAiCodeFixFeature(feature -> feature
          .organizationEligible(true)
          .enabledForAllProjects())
        .withProject("projectKey"))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();

    await().untilAsserted(() -> assertThat(getAiCodeFixStorageFilePath(backend, "connectionId"))
      .doesNotExist());
  }

  private Optional<AiCodeFix> readAiCodeFixSettings(SonarLintTestRpcServer backend, String connectionId) {
    var sonarLintDatabase = backend.getSonarLintDatabase();
    var aiCodeFixRepository = new AiCodeFixRepository(sonarLintDatabase);
    return aiCodeFixRepository.get(connectionId);
  }

  private static Path getAiCodeFixStorageFilePath(SonarLintTestRpcServer backend, String connectionId) {
    return backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("ai_codefix.pb");
  }
}
