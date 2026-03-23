/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class OnDemandPluginSignatureVerifierTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private final OnDemandPluginSignatureVerifier underTest = new OnDemandPluginSignatureVerifier();

  @Test
  void should_return_true_when_jar_and_signature_are_valid() throws URISyntaxException {
    var jarPath = testResource();

    assertThat(underTest.verify(jarPath, "cpp")).isTrue();
  }

  @Test
  void should_return_false_when_jar_is_tampered() throws IOException {
    var tamperedJar = tempDir.resolve("sonar-cpp-plugin-tampered.jar");
    Files.write(tamperedJar, "tampered content".getBytes());

    assertThat(underTest.verify(tamperedJar, "cpp")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonexistentplugin", "cpp-unknownkey", "cpp-corrupt", "cpp-nosig"})
  void should_return_false_for_invalid_signatures(String pluginKey) throws URISyntaxException {
    var jarPath = testResource();

    assertThat(underTest.verify(jarPath, pluginKey)).isFalse();
  }

  private static Path testResource() throws URISyntaxException {
    var resource = OnDemandPluginSignatureVerifierTest.class.getClassLoader().getResource("ondemand/sonar-cpp-plugin-test.jar");
    if (resource == null) {
      throw new IllegalArgumentException("Test resource not found: ondemand/sonar-cpp-plugin-test.jar");
    }
    return Path.of(resource.toURI());
  }
}
