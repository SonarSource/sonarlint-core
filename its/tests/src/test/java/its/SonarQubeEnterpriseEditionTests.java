/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permissions.RemoveGroupRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.APEX;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.C;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.COBOL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JCL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SECRETS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.TSQL;

class SonarQubeEnterpriseEditionTests extends AbstractConnectedTests {
  public static final String CONNECTION_ID = "orchestrator";
  private static final String PROJECT_KEY_COBOL = "sample-cobol";
  private static final String PROJECT_KEY_JCL = "sample-jcl";
  private static final String PROJECT_KEY_C = "sample-c";
  private static final String PROJECT_KEY_TSQL = "sample-tsql";
  private static final String PROJECT_KEY_APEX = "sample-apex";
  private static final String PROJECT_KEY_CUSTOM_SECRETS = "sample-custom-secrets";
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.ENTERPRISE)
    .activateLicense()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/c-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/cobol-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/jcl-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/tsql-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/apex-sonarlint.xml"))
    .build();

  private static WsClient adminWsClient;

  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;
  private static SonarLintRpcClientDelegate client;

  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private static String singlePointOfExitRuleKey;

  @BeforeAll
  static void prepare() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));

    removeGroupPermission("anyone", "scan");

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_C, "Sample C");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_COBOL, "Sample Cobol");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_TSQL, "Sample TSQL");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_APEX, "Sample APEX");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_C, "c", "SonarLint IT C");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_COBOL, "cobol", "SonarLint IT Cobol");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TSQL, "tsql", "SonarLint IT TSQL");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_APEX, "apex", "SonarLint IT APEX");
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 4)) {
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/custom-secrets-sonarlint.xml"));
      provisionProject(ORCHESTRATOR, PROJECT_KEY_CUSTOM_SECRETS, "Sample Custom Secrets");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_CUSTOM_SECRETS, "secrets", "SonarLint IT Custom Secrets");
    }
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 5)) {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JCL, "Sample JCL");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JCL, "jcl", "SonarLint IT JCL");
    }

    singlePointOfExitRuleKey = "c:S1005";
  }

  @AfterEach
  void stop() {
    analysisReadinessByConfigScopeId.forEach((scopeId, readiness) -> backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(scopeId)));
    analysisReadinessByConfigScopeId.clear();
    rpcClientLogs.clear();
    ((MockSonarLintRpcClientDelegate) client).clear();
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CommercialAnalyzers {

    @BeforeAll
    void prepare() throws IOException {
      startBackend(Map.of());
    }

    void start(String projectKey) {
      bindProject("project-" + projectKey, projectKey);
    }

    @AfterEach
    void stop() {
      ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    }

    @Test
    void analysisC_old_build_wrapper_prop(@TempDir File buildWrapperOutput) throws Exception {
      start(PROJECT_KEY_C);

      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __cplusplus 201703L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{\"compiler\":\"clang\",\"cwd\":\"" +
        Paths.get("projects/" + PROJECT_KEY_C).toAbsolutePath().toString().replace("\\", "\\\\") +
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      FileUtils.write(new File(buildWrapperOutput, "build-wrapper-dump.json"), buildWrapperContent, StandardCharsets.UTF_8);

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-output", buildWrapperOutput.getAbsolutePath());

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    // New property was introduced in SonarCFamily 6.18 part of SQ 8.8
    @OnlyOnSonarQube(from = "9.9")
    void analysisC_new_prop() {
      start(PROJECT_KEY_C);

      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __cplusplus 201703L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{\"compiler\":\"clang\",\"cwd\":\"" +
        Paths.get("projects/" + PROJECT_KEY_C).toAbsolutePath().toString().replace("\\", "\\\\") +
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    void analysisCobol() {
      start(PROJECT_KEY_COBOL);

      var rawIssues = analyzeFile(PROJECT_KEY_COBOL, "src/Custmnt2.cbl", "sonar.cobol.file.suffixes", "cbl");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "10.5")
    void analysisJCL() {
      start(PROJECT_KEY_JCL);

      var rawIssues = analyzeFile(PROJECT_KEY_JCL, "GAM0VCDB.jcl");

      assertThat(rawIssues).hasSize(6);
    }

    @Test
    void analysisTsql() {
      start(PROJECT_KEY_TSQL);

      var rawIssues = analyzeFile(PROJECT_KEY_TSQL, "src/file.tsql");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void analysisApex() {
      start(PROJECT_KEY_APEX);

      var rawIssues = analyzeFile(PROJECT_KEY_APEX, "src/file.cls");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "10.4")
    void analysisCustomSecrets() {
      start(PROJECT_KEY_CUSTOM_SECRETS);

      var rawIssues = analyzeFile(PROJECT_KEY_CUSTOM_SECRETS, "src/file.md");

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey, RawIssueDto::getPrimaryMessage)
        .containsOnly(tuple("secrets:custom_secret_rule", "User-specified secrets should not be disclosed."));
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithEmbeddedAnalyzer {

    @BeforeAll
    void setup() throws IOException {
      startBackend(Map.of("cpp", PluginLocator.getCppPluginPath()));
      bindProject("project-" + PROJECT_KEY_C, PROJECT_KEY_C);
    }

    /**
     * SLCORE-365 c:FunctionSinglePointOfExit has been deprecated in SonarCFamily 6.32.0.44918 (SQ 9.4) so older versions of SQ will
     * return a QP with rule c:FunctionSinglePointOfExit,
     * while embedded analyzer contains the new rule key. So SLCORE should do the translation.
     */
    @Test
    void analysisWithDeprecatedRuleKey() {
      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __cplusplus 201703L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{\"compiler\":\"clang\",\"cwd\":\"" +
        Paths.get("projects/" + PROJECT_KEY_C).toAbsolutePath().toString().replace("\\", "\\\\") +
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey)
        .containsOnly("c:S3805", "c:S1005");
    }
  }

  private static void bindProject(String projectName, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName,
        new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
    await().atMost(30, SECONDS).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(CONFIG_SCOPE_ID, true));
  }

  private List<RawIssueDto> analyzeFile(String projectDir, String filePathStr, String... properties) {
    var filePath = Path.of("projects").resolve(projectDir).resolve(filePathStr);
    var fileUri = filePath.toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(fileUri, Path.of(filePathStr), CONFIG_SCOPE_ID, false, null, filePath.toAbsolutePath(), null, null))));

    var analyzeResponse = backend.getAnalysisService().analyzeFiles(
      new AnalyzeFilesParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), toMap(properties), System.currentTimeMillis())
    ).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(CONFIG_SCOPE_ID);
    return raisedIssues != null ? raisedIssues : List.of();
  }

  private static void removeGroupPermission(String groupName, String permission) {
    adminWsClient.permissions().removeGroup(new RemoveGroupRequest()
      .setGroupName(groupName)
      .setPermission(permission));
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        }
        return super.getCredentials(connectionId);
      }

      @Override
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params.toString());
        rpcClientLogs.add(params);
      }
    };
  }

  static void startBackend(Map<String, Path> connectedModeEmbeddedPluginPathsByKey) throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    client = newDummySonarLintClient();
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(JAVA, COBOL, C, TSQL, APEX, SECRETS, JCL);
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, false, false, true, false, true);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, featureFlags,
            sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            emptySet(),
            connectedModeEmbeddedPluginPathsByKey, languages, emptySet(), emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }
}
