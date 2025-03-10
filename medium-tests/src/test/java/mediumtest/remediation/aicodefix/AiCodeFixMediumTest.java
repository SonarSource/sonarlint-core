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
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_KIND_NOT_SUPPORTED;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.CONNECTION_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.FILE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.ISSUE_NOT_FOUND;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
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
  void it_should_fail_if_the_configuration_scope_is_bound_to_sonarqube(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId")
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
      .containsExactly(CONNECTION_KIND_NOT_SUPPORTED, "The provided configuration scope is not bound to SonarQube Cloud");
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
    var backend = harness.newBackend()
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey"))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start();
    var issueId = UUID.randomUUID();

    var future = backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issueId));

    assertThat(future).failsWithin(Duration.of(1, ChronoUnit.SECONDS))
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode, ResponseError::getMessage)
      .containsExactly(ISSUE_NOT_FOUND, "The provided issue does not exist");
  }

  @SonarLintTest
  void it_should_fail_if_the_file_is_unknown(SonarLintTestHarness harness, @TempDir Path baseDir) throws InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion.withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
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
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
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
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
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
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
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
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
          .withAiCodeFixSuggestion(suggestion -> suggestion
            .withId(UUID.fromString("e51b7bbd-72bc-4008-a4f1-d75583f3dc98"))
            .withExplanation("This is the explanation")
            .withChange(0, 0, "This is the new code")))
      .start();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S1220", "MAJOR")))
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
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .start(fakeClient);

    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    assertThat(issue.isAiCodeFixable()).isFalse();
  }

  @SonarLintTest
  void it_should_mark_the_issue_as_not_fixable_if_rule_not_supported(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
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
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
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
  void it_should_return_the_suggestion_from_sonarqube_cloud(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
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
  void it_should_synchronize_the_ai_codefix_settings_from_the_server_when_disabled(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withAiCodeFixFeature(feature -> feature.withSupportedRules(Set.of("xml:S3421"))
        .organizationEligible(true)
        .disabled())
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withFullSynchronization()
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .isEqualTo(Sonarlint.AiCodeFixSettings.newBuilder()
        .addAllSupportedRules(Set.of("xml:S3421"))
        .setOrganizationEligible(true)
        .setEnablement(Sonarlint.AiCodeFixEnablement.DISABLED)
        .build()));
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_from_the_server_when_enabled_for_some_projects(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withAiCodeFixFeature(feature -> feature.withSupportedRules(Set.of("xml:S3421"))
        .organizationEligible(true)
        .enabledForProjects("projectKey"))
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withFullSynchronization()
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .isEqualTo(Sonarlint.AiCodeFixSettings.newBuilder()
        .addAllSupportedRules(Set.of("xml:S3421"))
        .setOrganizationEligible(true)
        .setEnablement(Sonarlint.AiCodeFixEnablement.ENABLED_FOR_SOME_PROJECTS)
        .addAllEnabledProjectKeys(Set.of("projectKey"))
        .build()));
  }

  @SonarLintTest
  void it_should_synchronize_the_ai_codefix_settings_from_the_server_when_enabled_for_all_projects(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withAiCodeFixFeature(feature -> feature.withSupportedRules(Set.of("xml:S3421"))
        .organizationEligible(true)
        .enabledForAllProjects())
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withFullSynchronization()
      .start(fakeClient);

    await().untilAsserted(() -> assertThat(readAiCodeFixSettings(backend, "connectionId"))
      .isEqualTo(Sonarlint.AiCodeFixSettings.newBuilder()
        .addAllSupportedRules(Set.of("xml:S3421"))
        .setOrganizationEligible(true)
        .setEnablement(Sonarlint.AiCodeFixEnablement.ENABLED_FOR_ALL_PROJECTS)
        .build()));
  }

  @SonarLintTest
  void it_should_register_telemetry(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", XML_SOURCE_CODE_WITH_ISSUE);
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarCloudServer("organizationKey")
      .withProject("projectKey",
        project -> project.withBranch("branchName")
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
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);
    var issue = analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    backend.getAiCodeFixRpcService().suggestFix(new SuggestFixParams("configScope", issue.getId())).join();

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"fixSuggestionReceivedCounter\":{\"e51b7bbd-72bc-4008-a4f1-d75583f3dc98\":{\"aiSuggestionsSource\":\"SONARCLOUD\",\"snippetsCount\":1,\"wasGeneratedFromIde\":true}}");
  }

  private Sonarlint.AiCodeFixSettings readAiCodeFixSettings(SonarLintTestRpcServer backend, String connectionId) {
    var path = backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("ai_codefix.pb");
    if (path.toFile().exists()) {
      return ProtobufFileUtil.readFile(path, Sonarlint.AiCodeFixSettings.parser());
    }
    return null;
  }
}
