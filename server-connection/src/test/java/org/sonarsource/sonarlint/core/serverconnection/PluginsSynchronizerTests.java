/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;
import testutils.MockWebServerExtensionWithProtobuf;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PluginsSynchronizerTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private PluginsSynchronizer underTest;

  @Test
  void should_synchronize_plugins(@TempDir Path tmp) throws Exception {
    var dest = tmp.resolve("destDir");
    Files.createDirectory(dest);
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"javascript\", \"hash\": \"79dba9cab72d8d31767f47c03d169598\", \"filename\": \"sonar-javascript-plugin-5.2.1.7778.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=java", "content-java");
    mockServer.addStringResponse("/api/plugins/download?plugin=javascript", "content-js");

    underTest = new PluginsSynchronizer(Set.of(Language.JAVA, Language.JS), new PluginsStorage(dest), emptySet());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    var references = ProtobufUtil.readFile(dest.resolve("plugin_references.pb"), PluginReferences.parser());
    assertThat(references.getPluginsByKeyMap().values()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-5.13.1.18282.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-5.2.1.7778.jar"));
    assertThat(dest.resolve("sonar-java-plugin-5.13.1.18282.jar")).hasContent("content-java");
    assertThat(dest.resolve("sonar-javascript-plugin-5.2.1.7778.jar")).hasContent("content-js");
    assertThat(anyPluginUpdated).isTrue();
  }

  @Test
  void should_not_synchronize_an_up_to_date_plugin(@TempDir Path tmp) throws Exception {
    var dest = tmp.resolve("destDir");
    Files.createDirectory(dest);
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=java", "content-java");
    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), emptySet());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));
    mockServer.removeResponse("/api/plugins/download?plugin=java");

    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(anyPluginUpdated).isFalse();
  }

  @Test
  void should_synchronize_a_plugin_when_hash_is_different(@TempDir Path tmp) throws Exception {
    var dest = tmp.resolve("destDir");
    Files.createDirectory(dest);
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=java", "content-java");
    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), emptySet());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"79dba9cab72d8d31767f47c03d169598\", \"filename\": \"sonar-java-plugin-5.14.0.18485.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=java", "content-java2");

    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    var references = ProtobufUtil.readFile(dest.resolve("plugin_references.pb"), PluginReferences.parser());
    assertThat(references.getPluginsByKeyMap().values()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("java", "79dba9cab72d8d31767f47c03d169598", "sonar-java-plugin-5.14.0.18485.jar"));
    assertThat(dest.resolve("sonar-java-plugin-5.14.0.18485.jar")).hasContent("content-java2");
    assertThat(anyPluginUpdated).isTrue();
  }

  @Test
  void should_not_synchronize_plugins_that_do_not_support_sonarlint(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": false}" +
      "]}");

    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), Set.of());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(dest.resolve("plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("sonar-java-plugin-5.13.1.18282.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }

  @Test
  void should_not_synchronize_plugins_with_unsupported_version(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.12.0.jar\", \"sonarLintSupported\": true}" +
      "]}");

    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), Set.of());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(dest.resolve("plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("sonar-java-plugin-5.12.0.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }

  @Test
  void should_not_synchronize_embedded_plugins(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": true}" +
      "]}");

    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), Set.of("java"));
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(dest.resolve("plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("sonar-java-plugin-5.13.1.18282.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }

  @Test
  void should_not_synchronize_plugins_for_not_enabled_languages(@TempDir Path tmp) {
    var dest = tmp.resolve("destDir");
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-java-plugin-5.13.1.18282.jar\", \"sonarLintSupported\": true}" +
      "]}");

    underTest = new PluginsSynchronizer(Set.of(Language.JS), new PluginsStorage(dest), emptySet());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(dest.resolve("plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("sonar-java-plugin-5.13.1.18282.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }

  @Test
  void should_synchronize_unknown_plugins_for_custom_rules(@TempDir Path tmp) {
    var dest = tmp.resolve("destDir");
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"java-custom\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"java-custom-plugin-4.3.0.1456.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=java-custom", "content-java-custom");

    underTest = new PluginsSynchronizer(Set.of(Language.JS), new PluginsStorage(dest), emptySet());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    var references = ProtobufUtil.readFile(dest.resolve("plugin_references.pb"), PluginReferences.parser());
    assertThat(references.getPluginsByKeyMap().values()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("java-custom", "de5308f43260d357acc97712ce4c5475", "java-custom-plugin-4.3.0.1456.jar"));
    assertThat(dest.resolve("java-custom-plugin-4.3.0.1456.jar")).hasContent("content-java-custom");
    assertThat(anyPluginUpdated).isTrue();
  }

  @Test
  void should_synchronize_the_old_typescript_plugin_if_language_enabled(@TempDir Path tmp) throws Exception {
    var dest = tmp.resolve("destDir");
    Files.createDirectory(dest);
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"typescript\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-typescript-plugin-1.9.0.3766.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=typescript", "content-ts");

    underTest = new PluginsSynchronizer(Set.of(Language.TS), new PluginsStorage(dest), emptySet());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    var references = ProtobufUtil.readFile(dest.resolve("plugin_references.pb"), PluginReferences.parser());
    assertThat(references.getPluginsByKeyMap().values()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("typescript", "de5308f43260d357acc97712ce4c5475", "sonar-typescript-plugin-1.9.0.3766.jar"));
    assertThat(dest.resolve("sonar-typescript-plugin-1.9.0.3766.jar")).hasContent("content-ts");
    assertThat(anyPluginUpdated).isTrue();
  }

  @Test
  void should_not_synchronize_the_old_typescript_plugin_if_language_not_enabled(@TempDir Path tmp) throws Exception {
    var dest = tmp.resolve("destDir");
    Files.createDirectory(dest);
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"typescript\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-typescript-plugin-1.9.0.3766.jar\", \"sonarLintSupported\": true}" +
      "]}");

    underTest = new PluginsSynchronizer(Set.of(Language.JAVA), new PluginsStorage(dest), emptySet());
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), new ProgressMonitor(null));

    assertThat(dest.resolve("plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("sonar-typescript-plugin-1.9.0.3766.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }
}
