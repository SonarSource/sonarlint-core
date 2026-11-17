/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) SonarSource Sàrl
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

import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static its.utils.AnalysisUtils.analyzeAndAwaitIssues;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;

/**
 * Split from {@link SonarQubeDeveloperEditionTests} so that both classes run against their own server in parallel forks.
 */
class SonarQubeDeveloperEditionAnalysisTests extends AbstractSonarQubeDeveloperEditionTests {

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = newDeveloperEditionOrchestrator();

  @BeforeAll
  static void start() throws IOException {
    startBackend(ORCHESTRATOR);
  }

  @Nested
  class AnalysisTests {

    @BeforeEach
    void start() {
      Map<String, String> globalProps = new HashMap<>();
      globalProps.put("sonar.global.label", "It works");
    }

    @AfterEach
    void stop() {
      adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
      client.clear();
    }

    // TODO should be moved to a separate class, not related to analysis
    @Test
    void shouldRaiseIssuesOnAJavaScriptProject() {
      var configScopeId = "shouldRaiseIssuesOnAJavaScriptProject";
      var projectKey = "sample-javascript";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/javascript-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "js", "SonarLint IT Javascript");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-javascript"), "src/Person.js");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesFromACustomRule() {
      var configScopeId = "shouldRaiseIssuesFromACustomRule";
      var projectKey = "sample-java-custom";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-custom.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Custom");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java-custom"), "src/main/java/foo/Foo.java");

      assertThat(rawIssues).extracting("ruleKey", "textRange")
        .usingRecursiveFieldByFieldElementComparator()
        .containsOnly(tuple("mycompany-java:AvoidAnnotation", new TextRangeDto(12, 3, 12, 19)));
    }

    @Test
    void shouldRaiseIssuesOnAPhpProject() {
      var configScopeId = "shouldRaiseIssuesOnAPhpProject";
      var projectKey = "sample-php";
      provisionProject(ORCHESTRATOR, projectKey, "Sample PHP");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/php-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "php", "SonarLint IT PHP");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-php"), "src/Math.php");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnATextDeveloperProject() {
      var configScopeId = "shouldRaiseIssuesOnATextDeveloperProject";
      var projectKey = "sample-text-developer";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Text Developer");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/text-developer-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "secrets", "SonarLint IT Text Developer");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-text-developer"), "src/file.yml");

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey)
        .containsOnly("secrets:S7155");
    }

    @Test
    void shouldNotOfferCustomSecretsTemplateOnATextDeveloperServer() {
      WsRequest request = new GetRequest("/api/rules/show").setParam("key", "secrets:S6784");

      try (var response = adminWsClient.wsConnector().call(request)) {
        assertThat(response.code()).isEqualTo(404);
      }
    }

    @Test
    void shouldRaiseIssuesOnAPythonProject() {
      var configScopeId = "shouldRaiseIssuesOnAPythonProject";
      var projectKey = "sample-python";
      var projectName = "Sample Python";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT Python");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-python"), "src/hello.py");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAHtmlProject() {
      var configScopeId = "shouldRaiseIssuesOnAHtmlProject";
      var projectKey = "sample-web";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Web");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/web-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "web", "SonarLint IT Web");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-web"), "src/file.html");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAGoProject() {
      var configScopeId = "shouldRaiseIssuesOnAGoProject";
      var projectKey = "sample-go";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Go");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/go-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "go", "SonarLint IT Go");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-go"), "src/sample.go");

      // S5542 was introduced with Go Enterprise in 2025.2
      var expectedIssues = ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(2025, 2) ? 2 : 1;
      assertThat(rawIssues).hasSize(expectedIssues);
    }

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldRaiseIssuesOnACloudFormationProject() {
      var configScopeId = "shouldRaiseIssuesOnACloudFormationProject";
      var projectKey = "sample-cloudformation";
      provisionProject(ORCHESTRATOR, projectKey, "Sample CloudFormation");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/cloudformation-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cloudformation", "SonarLint IT CloudFormation");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-cloudformation"), "src/sample.yaml");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldRaiseIssuesOnADockerProject() {
      var configScopeId = "shouldRaiseIssuesOnADockerProject";
      var projectKey = "sample-docker";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Docker");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/docker-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "docker", "SonarLint IT Docker");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-docker"), "src/Dockerfile");

      assertThat(rawIssues).hasSize(2);
    }

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldRaiseIssuesOnAKubernetesProject() {
      var configScopeId = "shouldRaiseIssuesOnAKubernetesProject";
      var projectKey = "sample-kubernetes";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kubernetes");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kubernetes-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kubernetes", "SonarLint IT Kubernetes");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var raisedIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-kubernetes"), "src/sample.yaml");

      assertThat(raisedIssues)
        .extracting(RaisedFindingDto::getRuleKey)
        .containsOnly("kubernetes:S1135");
    }

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldRaiseIssuesOnATerraformProject() {
      var configScopeId = "shouldRaiseIssuesOnATerraformProject";
      var projectKey = "sample-terraform";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Terraform");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/terraform-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "terraform", "SonarLint IT Terraform");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-terraform"), "src/sample.tf");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseDataflowIssuesOnAPythonProject() {
      var configScopeId = "shouldRaiseDataflowIssuesOnAPythonProject";
      var projectKey = "sample-dbd";
      provisionProject(ORCHESTRATOR, projectKey, "Sample DBD");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/dbd-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT DBD");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForSync(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-dbd"), "src/hello.py");

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getPrimaryMessage)
        .containsOnly(tuple("pythonbugs:S6466", "Fix this access on a collection that may trigger an 'IndexError'."));
    }

    @Test
    void customSensorsShouldNotBeExecuted() {
      var configScopeId = "customSensorsShouldNotBeExecuted";
      var projectKey = "sample-java-custom-sensor";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/custom-sensor.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Custom Sensor");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      assertThat(analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java")).isEmpty();
    }

    // TODO should be moved to a medium test
    @Test
    void globalExtension() {
      var configScopeId = "globalExtension";
      var projectKey = "sample-global-extension";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Global Extension");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/global-extension.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cobol", "SonarLint IT Global Extension");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-global-extension"), "src/foo.glob", "sonar.cobol.file.suffixes", "glob");
      assertThat(rawIssues).extracting("ruleKey", "primaryMessage").containsOnly(
        tuple("global:inc", "Issue number 0"));

      rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-global-extension"), "src/foo.glob", "sonar.cobol.file.suffixes", "glob");
      assertThat(rawIssues).extracting("ruleKey", "primaryMessage").containsOnly(
        tuple("global:inc", "Issue number 1"));
    }

    @Test
    void shouldRaiseIssuesFromATemplateRule() throws Exception {
      var configScopeId = "shouldRaiseIssuesFromATemplateRule";
      var projectKey = "sample-java-template-rule";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      var qp = getQualityProfile(adminWsClient, "SonarLint IT Java");

      PostRequest request = new PostRequest("/api/rules/create")
        .setParam("params", "methodName=echo;className=foo.Foo;argumentTypes=int")
        .setParam("name", "myrule")
        .setParam("severity", "MAJOR");
      request.setParam("customKey", "myrule")
        .setParam("markdownDescription", "my_rule_description")
        .setParam("templateKey", javaRuleKey("S2253"));

      try (var response = adminWsClient.wsConnector().call(request)) {
        assertTrue(response.isSuccessful());
      }

      request = new PostRequest("/api/qualityprofiles/activate_rule")
        .setParam("key", qp.getKey())
        .setParam("rule", javaRuleKey("myrule"));
      try (var response = adminWsClient.wsConnector().call(request)) {
        assertTrue(response.isSuccessful(), "Unable to activate custom rule");
      }

      try {
        openBoundConfigurationScope(configScopeId, projectKey, true);
        waitForAnalysisToBeReady(configScopeId);

        var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");
        assertThat(rawIssues).hasSize(3);

        var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, javaRuleKey("myrule"), null)).get();
        var details = ruleDetails.details();
        assertThat(details.getDescription().getLeft().getHtmlContent()).contains("my_rule_description");
        assertThat(details.getName()).isEqualTo("myrule");

      } finally {

        request = new PostRequest("/api/rules/delete")
          .setParam("key", javaRuleKey("myrule"));
        try (var response = adminWsClient.wsConnector().call(request)) {
          assertTrue(response.isSuccessful(), "Unable to delete custom rule");
        }
      }
    }

    @Test
    void shouldHonorServerSideSettings() {
      var configScopeId = "shouldHonorServerSideSettings";
      var projectKey = "sample-java-configured";
      var projectName = "Sample Java";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");
      assertThat(rawIssues).hasSize(2);

      rpcClientLogs.clear();
      didSynchronizeConfigurationScopes.clear();
      // Override default file suffixes in global props so that input file is not considered as a Java file
      setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, false)));
      await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      assertThat(analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java")).isEmpty();

      rpcClientLogs.clear();
      // Override default file suffixes in project props so that input file is considered as a Java file again
      setSettingsMultiValue(projectKey, "sonar.java.file.suffixes", ".java");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)));
      await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");
      assertThat(rawIssues).hasSize(2);
    }

    @Test
    void shouldRaiseIssuesOnARubyProject() {
      var configScopeId = "shouldRaiseIssuesOnARubyProject";
      var projectKey = "sample-ruby";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Ruby");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/ruby-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "ruby", "SonarLint IT Ruby");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-ruby"), "src/hello.rb");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAKotlinProject() {
      var configScopeId = "shouldRaiseIssuesOnAKotlinProject";
      var projectKey = "sample-kotlin";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kotlin");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kotlin-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kotlin", "SonarLint IT Kotlin");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-kotlin"), "src/hello.kt");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAScalaProject() {
      var configScopeId = "shouldRaiseIssuesOnAScalaProject";
      var projectKey = "sample-scala";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Scala");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/scala-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "scala", "SonarLint IT Scala");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-scala"), "src/Hello.scala");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAnXmlProject() {
      var configScopeId = "shouldRaiseIssuesOnAnXmlProject";
      var projectKey = "sample-xml";
      provisionProject(ORCHESTRATOR, projectKey, "Sample XML");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xml-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "xml", "SonarLint IT XML");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-xml"), "src/foo.xml");

      assertThat(rawIssues).hasSize(1);
    }
  }
}
