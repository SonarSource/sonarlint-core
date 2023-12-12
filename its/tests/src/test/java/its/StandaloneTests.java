/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2023 SonarSource SA
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import its.utils.TestClientInputFile;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamType;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.COBOL;

class StandaloneTests {
  public static final TelemetryClientConstantAttributesDto IT_TELEMETRY_ATTRIBUTES = new TelemetryClientConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
    "1.2.3", "4.5.6", Collections.emptyMap());
  public static final ClientConstantInfoDto IT_CLIENT_INFO = new ClientConstantInfoDto("clientName", "integrationTests");
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

  private static SonarLintAnalysisEngine engine;
  private static List<String> logs;
  @TempDir
  private File baseDir;
  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;

  @BeforeAll
  static void prepare(@TempDir Path sonarlintUserHome) throws Exception {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());
    backend = clientLauncher.getServerProxy();
    try {
      // The global-extension-plugin reuses the cobol plugin key to be whitelisted
      var languages = Set.of(COBOL);
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, true, false, true);
      System.out.println("Before backend initialize");
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, featureFlags,
          sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("work"),
          Set.of(Paths.get("../plugins/global-extension-plugin/target/global-extension-plugin.jar")), Collections.emptyMap(),
          languages, Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), sonarUserHome.toString(), Map.of(), false, null))
        .get();
      System.out.println("After backend initialize");
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
    logs = new ArrayList<>();
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> logs.add(msg))
      .setExtraProperties(globalProps).build();
    System.out.println("Before engine initialize");
    engine = new SonarLintAnalysisEngine(config, backend, null);
    System.out.println("After engine initialize");

    assertThat(logs).containsOnlyOnce("Start Global Extension It works");
  }

  @AfterAll
  static void stop() {
    engine.stop();
    assertThat(logs).containsOnlyOnce("Stop Global Extension");
  }

  @Test
  void checkRuleParameterDeclarations() throws ExecutionException, InterruptedException {
    var ruleDetails = backend.getRulesService().listAllStandaloneRulesDefinitions().get().getRulesByKey();
    assertThat(ruleDetails).hasSize(1);
    var incRule = ruleDetails.entrySet().iterator().next().getValue();
    assertThat(incRule.getParamsByKey()).hasSize(8);
    assertRuleHasParam(incRule, "stringParam", RuleParamType.STRING);
    assertRuleHasParam(incRule, "textParam", RuleParamType.TEXT);
    assertRuleHasParam(incRule, "boolParam", RuleParamType.BOOLEAN);
    assertRuleHasParam(incRule, "intParam", RuleParamType.INTEGER);
    assertRuleHasParam(incRule, "floatParam", RuleParamType.FLOAT);
    assertRuleHasParam(incRule, "enumParam", RuleParamType.STRING, "enum1", "enum2", "enum3");
    assertRuleHasParam(incRule, "enumListParam", RuleParamType.STRING, "list1", "list2", "list3");
    assertRuleHasParam(incRule, "multipleIntegersParam", RuleParamType.INTEGER, "80", "120", "160");
  }

  private static void assertRuleHasParam(RuleDefinitionDto rule, String paramKey, RuleParamType expectedType, String... possibleValues) {
    var param = rule.getParamsByKey().entrySet().stream().filter(p -> p.getKey().equals(paramKey)).findFirst();
    assertThat(param).isNotEmpty();
    assertThat(param.get().getValue())
      .extracting(RuleParamDefinitionDto::getType, RuleParamDefinitionDto::getPossibleValues)
      .containsExactly(expectedType, Arrays.asList(possibleValues));
  }

  @Test
  void globalExtension() throws Exception {
    var inputFile = prepareInputFile("foo.glob", "foo", false);

    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cobol.file.suffixes", "glob")
        .build(),
      issues::add, null, null, CONFIG_SCOPE_ID);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 0"));

    // Default parameter values
    assertThat(logs).containsSubsequence(
      "Param stringParam has value null",
      "Param textParam has value text\nparameter",
      "Param intParam has value 42",
      "Param boolParam has value true",
      "Param floatParam has value 3.14159265358",
      "Param enumParam has value enum1",
      "Param enumListParam has value list1,list2",
      "Param multipleIntegersParam has value null");

    issues.clear();
    backend.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(
      Map.of("global:inc", new StandaloneRuleConfigDto(true, Map.of("stringParam", "polop", "textParam", "", "multipleIntegersParam", "80,160", "unknown", "parameter")))));
    engine.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cobol.file.suffixes", "glob")
        .build(),
      issues::add, null, null, CONFIG_SCOPE_ID);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 1"));

    // Overridden parameter values
    assertThat(logs).contains(
      "Param stringParam has value polop",
      "Param textParam has value ",
      "Param intParam has value 42",
      "Param boolParam has value true",
      "Param floatParam has value 3.14159265358",
      "Param enumParam has value enum1",
      "Param enumListParam has value list1,list2",
      "Param multipleIntegersParam has value 80,160");
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new TestClientInputFile(baseDir.toPath(), file.toPath(), isTest, encoding);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8);
  }

  private List<RawIssue> analyzeFile(String projectDir, String filePath, String... properties) {
    var issueListener = new AbstractConnectedTests.SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectDir, filePath, properties),
      issueListener, null, null, CONFIG_SCOPE_ID);
    return issueListener.issues;
  }

  private static AnalysisConfiguration createAnalysisConfiguration(String projectDir, String filePath, String... properties) {
    final var baseDir = Paths.get("projects/" + projectDir).toAbsolutePath();
    final var path = baseDir.resolve(filePath);
    return AnalysisConfiguration.builder()
      .setBaseDir(new File("projects/" + projectDir).toPath().toAbsolutePath())
      .addInputFile(new TestClientInputFile(baseDir, path, false, StandardCharsets.UTF_8))
      .putAllExtraProperties(toMap(properties))
      .build();
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    var index = 0;
    while (index < keyValues.length) {
      var key = keyValues[index++];
      var value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {
      @Override
      public void log(LogParams params) {
        System.out.println(params.getMessage());
      }
    };
  }

}
