/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Downloader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ConnectedIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngine sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    Path storage = Paths.get(ConnectedIssueMediumTest.class.getResource("/sample-storage").toURI());
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH)
      .setKey("javascript")
      .build());
    cache.get(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH, new Downloader() {

      @Override
      public void download(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(PluginLocator.getJavaScriptPluginUrl(), toFile.toFile());
      }
    });

    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVA_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH)
      .setKey("java")
      .build());
    cache.get(PluginLocator.SONAR_JAVA_PLUGIN_JAR, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH, new Downloader() {

      @Override
      public void download(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(PluginLocator.getJavaPluginUrl(), toFile.toFile());
      }
    });

    ProtobufUtil.writeToFile(builder.build(), storage.resolve("localhost").resolve("global").resolve(StorageManager.PLUGIN_REFERENCES_PB));

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("localhost")
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage)
      .setLogOutput(new LogOutput() {

        @Override
        public void log(String formattedMessage, Level level) {
          // Don't pollute logs
        }
      })
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop(true);
  }

  @Test
  public void simpleJavaScriptUnbinded() throws Exception {

    RuleDetails ruleDetails = sonarlint.getRuleDetails("javascript:UnusedVariable");
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable is declared but not used");

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {
        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.getPath()));

  }

  @Test
  public void simpleJavaUnbinded() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaBinded() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration("sample", baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "BLOCKER"));
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = createInputFile(file.toPath(), isTest);
    return inputFile;
  }

  private ClientInputFile createInputFile(final Path path, final boolean isTest) {
    ClientInputFile inputFile = new ClientInputFile() {

      @Override
      public Path getPath() {
        return path;
      }

      @Override
      public boolean isTest() {
        return isTest;
      }

      @Override
      public Charset getCharset() {
        return StandardCharsets.UTF_8;
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }
    };
    return inputFile;
  }

}
