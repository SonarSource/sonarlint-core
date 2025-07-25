/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.http.HttpMethod;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.AffectedPackageDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.RecommendationDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
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
  private static final String PROJECT_KEY_SCA = "sample-sca";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setServerProperty("sonar.sca.enabled", "true")
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

    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 4)) {
      singlePointOfExitRuleKey = "c:S1005";
    } else {
      singlePointOfExitRuleKey = "c:FunctionSinglePointOfExit";
    }
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

    void start(String configScopeId, String projectKey) {
      bindProject(configScopeId, "project-" + projectKey, projectKey);
    }

    @AfterEach
    void stop() {
      ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    }

    @Test
    void analysisC_old_build_wrapper_prop(@TempDir File buildWrapperOutput) throws Exception {
      String configScopeId = "analysisC_old_build_wrapper_prop";
      start(configScopeId, PROJECT_KEY_C);

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

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-output", buildWrapperOutput.getAbsolutePath());

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    // New property was introduced in SonarCFamily 6.18 part of SQ 8.8
    @OnlyOnSonarQube(from = "8.8")
    void analysisC_new_prop() {
      String configScopeId = "analysisC_old_build_wrapper_prop";
      start(configScopeId, PROJECT_KEY_C);

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

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    void analysisCobol() {
      String configScopeId = "analysisCobol";
      start(configScopeId, PROJECT_KEY_COBOL);

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_COBOL, "src/Custmnt2.cbl", "sonar.cobol.file.suffixes", "cbl");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "10.5")
    void analysisJCL() {
      String configScopeId = "analysisJCL";
      start(configScopeId, PROJECT_KEY_JCL);

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_JCL, "GAM0VCDB.jcl");

      assertThat(rawIssues).hasSize(6);
    }

    @Test
    void analysisTsql() {
      String configScopeId = "analysisTsql";
      start(configScopeId, PROJECT_KEY_TSQL);

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_TSQL, "src/file.tsql");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void analysisApex() {
      String configScopeId = "analysisApex";
      start(configScopeId, PROJECT_KEY_APEX);

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_APEX, "src/file.cls");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "10.4")
    void analysisCustomSecrets() {
      var configScopeId = "analysisCustomSecrets";
      start(configScopeId, PROJECT_KEY_CUSTOM_SECRETS);

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_CUSTOM_SECRETS, "src/file.md");

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getPrimaryMessage)
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
    }

    /**
     * SLCORE-365 c:FunctionSinglePointOfExit has been deprecated in SonarCFamily 6.32.0.44918 (SQ 9.4) so older versions of SQ will
     * return a QP with rule c:FunctionSinglePointOfExit,
     * while embedded analyzer contains the new rule key. So SLCORE should do the translation.
     */
    @Test
    void analysisWithDeprecatedRuleKey() {
      var configScopeId = "analysisWithDeprecatedRuleKey";
      bindProject(configScopeId, "project-" + PROJECT_KEY_C, PROJECT_KEY_C);
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

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey)
        .containsOnly("c:S3805", "c:S1005");
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @OnlyOnSonarQube(from = "2025.4")
  class Sca {
    @BeforeAll
    void prepare() throws IOException {
      startBackend(Map.of());
    }

    @Test
    void should_return_risk_dependency_details() {
      String configScopeId = "should_honor_the_web_api_contract";
      provisionProject(ORCHESTRATOR, PROJECT_KEY_SCA, "Sample SCA");
      analyzeMavenProject(ORCHESTRATOR, "sample-sca", Map.of("sonar.projectKey", PROJECT_KEY_SCA));
      bindProject(configScopeId, PROJECT_KEY_SCA, PROJECT_KEY_SCA);
      var firstDependencyRiskKey = getFirstDependencyRiskKey(PROJECT_KEY_SCA);

      var riskDetailsResponse = backend.getDependencyRiskService().getDependencyRiskDetails(new GetDependencyRiskDetailsParams(configScopeId, firstDependencyRiskKey)).join();

      assertThat(riskDetailsResponse)
        .usingRecursiveComparison()
        .isEqualTo(new GetDependencyRiskDetailsResponse(firstDependencyRiskKey, DependencyRiskDto.Severity.MEDIUM, "com.fasterxml.woodstox:woodstox-core", "6.2.7",
          DependencyRiskDto.Type.VULNERABILITY, "CVE-2022-40152",
          "Those using Woodstox to parse XML data may be vulnerable to Denial of Service attacks (DOS) if DTD support is enabled. If the parser is running on user supplied input, an attacker may supply content that causes the parser to crash by stackoverflow. This effect may support a denial of service attack.",
          List.of(new AffectedPackageDto("pkg:maven/com.fasterxml.woodstox/woodstox-core", "upgrade",
            RecommendationDetailsDto.builder().impactDescription("Vulnerability occurs if attacker can provide specifically crafted XML document with DTD reference.")
              .impactScore(5).realIssue(true).visibility("external").build()))));
    }

    private UUID getFirstDependencyRiskKey(String projectKey) {
      var response = ORCHESTRATOR.getServer()
        .newHttpCall("/api/v2/sca/issues-releases")
        .setMethod(HttpMethod.GET)
        .setAdminCredentials()
        .setParam("projectKey", projectKey)
        .setParam("branchName", MAIN_BRANCH_NAME)
        .execute();
      if (!response.isSuccessful()) {
        throw new IllegalStateException("Unexpected response code: " + response);
      }
      var jsonObject = new Gson().fromJson(response.getBodyAsString(), JsonObject.class);
      return UUID.fromString(jsonObject.getAsJsonArray("issuesReleases").get(0).getAsJsonObject().get("key").getAsString());
    }
  }

  private static void bindProject(String configScopeId, String projectName, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, projectName,
        new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
    await().atMost(30, SECONDS).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
  }

  private List<RaisedIssueDto> analyzeFile(String configScopeId, String projectDir, String filePathStr, String... properties) {
    var filePath = Path.of("projects").resolve(projectDir).resolve(filePathStr);
    var fileUri = filePath.toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(fileUri, Path.of(filePathStr), configScopeId, false, null, filePath.toAbsolutePath(), null, null, true)),
      List.of(),
      List.of()));

    var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(fileUri), toMap(properties), true, System.currentTimeMillis())).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedIssues != null ? raisedIssues.values().stream().flatMap(List::stream).toList() : List.of();
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
        System.out.println(params);
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
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, false, false, true, false, true, false);
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, featureFlags,
          sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("work"),
          emptySet(),
          connectedModeEmbeddedPluginPathsByKey, languages, emptySet(), emptySet(),
          List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), emptyList(),
          sonarUserHome.toString(),
          Map.of(), false, null, false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }
}
