/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class OnDemandPluginSignatureVerifierTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private final OnDemandPluginSignatureVerifier underTest = new OnDemandPluginSignatureVerifier();

  @Test
  void should_return_false_when_jar_signature_not_found() throws IOException {
    var jarPath = createMinimalPluginJar("nonexistent", "1.0.0");

    assertThat(underTest.verify(jarPath, "nonexistent")).isFalse();
  }

  @Test
  void should_return_false_when_jar_is_tampered() throws IOException {
    var tamperedJar = tempDir.resolve("sonar-cpp-plugin-tampered.jar");
    Files.write(tamperedJar, "tampered content".getBytes());

    assertThat(underTest.verify(tamperedJar, DownloadableArtifact.CFAMILY_PLUGIN)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cpp-unknownkey", "cpp-corrupt", "cpp-nosig"})
  void should_return_false_for_invalid_signatures(String pluginKey) throws IOException {
    var jarPath = createMinimalPluginJar(pluginKey, "1.0.0");

    // Verify it fails with a nonexistent signature path
    assertThat(underTest.verify(jarPath, "ondemand/sonar-cpp-plugin-nonexistent.jar.asc")).isFalse();
  }

  private Path createMinimalPluginJar(String pluginKey, String pluginVersion) throws IOException {
    var target = tempDir.resolve("sonar-" + pluginKey + "-plugin-test.jar");
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
    manifest.getMainAttributes().putValue("Plugin-Version", pluginVersion);
    try (var jos = new JarOutputStream(Files.newOutputStream(target), manifest)) {
      // minimal JAR with only the manifest
    }
    return target;
  }
}
