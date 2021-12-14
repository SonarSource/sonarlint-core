/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.mediumtest.fixtures.ProjectStorageFixture;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;
import testutils.OnDiskTestClientInputFile;
import testutils.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.client.api.common.ClientFileSystemFixtures.aClientFileSystemWith;
import static org.sonarsource.sonarlint.core.client.api.common.ClientFileSystemFixtures.anEmptyClientFileSystem;
import static org.sonarsource.sonarlint.core.mediumtest.fixtures.StorageFixture.newStorage;
import static testutils.TestUtils.createNoOpIssueListener;
import static testutils.TestUtils.createNoOpLogOutput;

public class ConnectedIssueMediumTest {

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    var storage = newStorage(SERVER_ID)
      .withJSPlugin()
      .withJavaPlugin()
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          .withActiveRule("java:S106", "MAJOR")
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER")))
      .withProject("stale_module", ProjectStorageFixture.ProjectStorageBuilder::stale)
      .create(slHome);

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  public void testContainerInfo() {
    assertThat(sonarlint.getPluginDetails()).extracting("key").containsOnly("java", "javascript");
    assertThat(sonarlint.allProjectsByKey()).containsKeys("test-project", "test-project-2");
  }

  @Test
  public void testStaleProject() {
    assertThat(sonarlint.getProjectStorageStatus("stale_module").isStale()).isTrue();
    ConnectedAnalysisConfiguration config = ConnectedAnalysisConfiguration.builder()
      .setProjectKey("stale_module")
      .setBaseDir(baseDir.toPath())
      .setModuleKey("key")
      .build();

    try {
      sonarlint.analyze(config, createNoOpIssueListener(), null, null);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(StorageException.class)
        .hasMessage("Stored data for project 'stale_module' is stale because it was created with a different version of SonarLint. Please update the binding.");
    }
  }

  @Test
  public void unknownRuleKey() {
    assertThrows(SonarLintException.class, () -> sonarlint.getRuleDetails("not_found"), "Invalid rule key: not_found");
    assertThrows(SonarLintException.class, () -> sonarlint.getActiveRuleDetails("not_found", null), "Invalid active rule key: not_found");
    assertThrows(SonarLintException.class, () -> sonarlint.getActiveRuleDetails("not_found", JAVA_MODULE_KEY), "Invalid active rule key: not_found");
  }

  @Test
  @Ignore("We don't support this use case anymore")
  public void simpleJavaScriptUnbinded() throws Exception {
    // TODO remove test ?
    String ruleKey = "javascript:S1135";
    ConnectedRuleDetails ruleDetails = sonarlint.getRuleDetails(ruleKey);
    assertThat(ruleDetails.getKey()).isEqualTo(ruleKey);
    assertThat(ruleDetails.getName()).isEqualTo("\"TODO\" tags should be handled");
    assertThat(ruleDetails.getLanguage()).isEqualTo(Language.JS);
    assertThat(ruleDetails.getSeverity()).isEqualTo("INFO");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "<code>TODO</code> tags are commonly used");
    assertThat(ruleDetails.getExtendedDescription()).isEmpty();

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x; //TODO\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), (m, l) -> System.out.println(m), null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple(ruleKey, 2, inputFile.getPath()));
  }

  @Test
  @Ignore("We don't support this use case anymore")
  public void simpleJavaUnbinded() throws Exception {
    // TODO remove ?
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("java:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("java:S1481", 3, inputFile.getPath(), "BLOCKER"));
  }

  @Test
  @Ignore("We don't support this use case anymore")
  public void simpleJavaTestUnbinded() throws Exception {
    // TODO remove ?
    ClientInputFile inputFile = prepareJavaTestInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S2187", 1, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaBinded() throws Exception {
    ClientInputFile inputFile = prepareJavaInputFile();

    // Severity of java:S1481 changed to BLOCKER in the quality profile
    assertThat(sonarlint.getRuleDetails("java:S1481").getSeverity()).isEqualTo("MINOR");
    assertThat(sonarlint.getActiveRuleDetails("java:S1481", JAVA_MODULE_KEY).getSeverity()).isEqualTo("BLOCKER");
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey(JAVA_MODULE_KEY)
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("java:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("java:S1481", 3, inputFile.getPath(), "BLOCKER"));
  }

  @Test
  public void rule_description_come_from_plugin() {
    assertThat(sonarlint.getRuleDetails("java:S106").getHtmlDescription())
      .isEqualTo("<p>When logging a message there are several important requirements which must be fulfilled:</p>\n"
        + "<ul>\n"
        + "  <li> The user must be able to easily retrieve the logs </li>\n"
        + "  <li> The format of all logged message must be uniform to allow the user to easily read the log </li>\n"
        + "  <li> Logged data must actually be recorded </li>\n"
        + "  <li> Sensitive data must only be logged securely </li>\n"
        + "</ul>\n"
        + "<p>If a program directly writes to the standard outputs, there is absolutely no way to comply with those requirements. That's why defining and using a\n"
        + "dedicated logger is highly recommended.</p>\n"
        + "<h2>Noncompliant Code Example</h2>\n"
        + "<pre>\n"
        + "System.out.println(\"My Message\");  // Noncompliant\n"
        + "</pre>\n"
        + "<h2>Compliant Solution</h2>\n"
        + "<pre>\n"
        + "logger.log(\"My Message\");\n"
        + "</pre>\n"
        + "<h2>See</h2>\n"
        + "<ul>\n"
        + "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/RoElAQ\">CERT, ERR02-J.</a> - Prevent exceptions while logging data </li>\n"
        + "</ul>");
  }

  @Test
  public void emptyQPJava() throws IOException {
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey("test-project")
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  public void declare_module_should_create_a_module_container_with_loaded_extensions() {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))));

    ComponentContainer moduleContainer = sonarlint.getGlobalContainer().getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintModuleFileSystem.class)).isNotEmpty();
  }

  @Test
  public void stop_module_should_stop_the_module_container() {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))));
    ComponentContainer moduleContainer = sonarlint.getGlobalContainer().getModuleRegistry().getContainerFor("key");

    sonarlint.stopModule("key");

    assertThat(moduleContainer.getPicoContainer().getLifecycleState().isStarted()).isFalse();
  }

  @Test
  public void should_forward_module_file_event_to_listener() {
    // should not be located in global container in real life but easier for testing
    FakeModuleFileListener moduleFileListener = new FakeModuleFileListener();
    sonarlint.getGlobalContainer().add(moduleFileListener);
    OnDiskTestClientInputFile clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);
    sonarlint.declareModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem()));

    sonarlint.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED));

    assertThat(moduleFileListener.events).hasSize(1);
  }

  @SonarLintSide(lifespan = "MODULE")
  static class FakeModuleFileListener implements ModuleFileListener {
    private final List<ModuleFileEvent> events = new ArrayList<>();

    @Override
    public void process(ModuleFileEvent event) {
      events.add(event);
    }
  }

  private ClientInputFile prepareJavaInputFile() throws IOException {
    return prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareJavaTestInputFile() throws IOException {
    return prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void foo() {\n"
        + "  }\n"
        + "}",
      true);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = TestUtils.createInputFile(file.toPath(), relativePath, isTest);
    return inputFile;
  }

  static class StoreIssueListener implements IssueListener {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

}
