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
import org.sonarsource.sonarlint.core.SonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Downloader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ConnectedIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static SonarLintEngine sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    Path storage = Paths.get(ConnectedIssueMediumTest.class.getResource("/sample-storage").toURI());
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename("sonar-javascript-plugin-2.8.jar")
      .setHash("0f87170f4cec0f7fc51b6572530153f9")
      .setKey("javascript")
      .build());
    cache.get("sonar-javascript-plugin-2.8.jar", "0f87170f4cec0f7fc51b6572530153f9", new Downloader() {

      @Override
      public void download(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(ConnectedIssueMediumTest.class.getResource("/sonar-javascript-plugin-2.8.jar"), toFile.toFile());
      }
    });

    builder.addReference(PluginReference.newBuilder()
      .setFilename("sonar-java-plugin-3.9.jar")
      .setHash("db224331b6753d63cb31f2b58c93914c")
      .setKey("java")
      .build());
    cache.get("sonar-java-plugin-3.9.jar", "db224331b6753d63cb31f2b58c93914c", new Downloader() {

      @Override
      public void download(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(ConnectedIssueMediumTest.class.getResource("/sonar-java-plugin-3.9.jar"), toFile.toFile());
      }
    });

    ProtobufUtil.writeToFile(builder.build(), storage.resolve("localhost").resolve("global").resolve(StorageManager.PLUGIN_REFERENCES_PB));

    GlobalConfiguration config = GlobalConfiguration.builder()
      .setServerId("localhost")
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage)
      .setVerbose(true)
      .build();
    sonarlint = new SonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Test
  public void simpleJavaScript() throws Exception {

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
    sonarlint.analyze(new AnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
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
  public void simpleJava() throws Exception {
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
    sonarlint.analyze(new AnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
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
