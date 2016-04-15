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
package org.sonarsource.sonarlint.core.container.connected.validate;

import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class PluginVersionCheckerTest {
  private static final String RESPONSE_FILE_LTS = "/validate/plugins_index.txt";
  private static final String RESPONSE_FILE = "/validate/installed_plugins.json";
  private static final String RESPONSE_FILE_FAIL = "/validate/installed_plugins_fail.json";
  private static final String RESPONSE_FILE_LTS_FAIL = "/validate/plugins_index_fail.txt";
  private PluginVersionChecker checker;
  private SonarLintWsClient client;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    client = mock(SonarLintWsClient.class);
    checker = new PluginVersionChecker(client);
  }

  @Test
  public void testSuccess() {
    WsClientTestUtils.addReaderResponse(client, PluginVersionChecker.WS_PATH, RESPONSE_FILE);

    checker.checkPlugins("5.2");
  }

  @Test
  public void testSuccessLTS() throws IOException {
    String content = Resources.toString(this.getClass().getResource(RESPONSE_FILE_LTS), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);

    checker.checkPlugins("5.1");
  }

  @Test
  public void testFailJava() {
    WsClientTestUtils.addReaderResponse(client, PluginVersionChecker.WS_PATH, RESPONSE_FILE_FAIL);

    ValidationResult result = checker.validatePlugins("5.2");
    assertThat(result.success()).isFalse();
    assertThat(result.message()).contains("The following plugins do not meet the required minimum version");
    assertThat(result.message()).contains("Java");
  }

  @Test
  public void testFailJavaLTS() throws IOException {
    String content = Resources.toString(this.getClass().getResource(RESPONSE_FILE_LTS_FAIL), StandardCharsets.UTF_8);
    WsClientTestUtils.addResponse(client, PluginVersionChecker.WS_PATH_LTS, content);

    ValidationResult result = checker.validatePlugins("5.1");
    assertThat(result.success()).isFalse();
    assertThat(result.message()).contains("The following plugins do not meet the required minimum version");
    assertThat(result.message()).contains("java");
  }

  @Test
  public void testFailJavaException() {
    WsClientTestUtils.addReaderResponse(client, PluginVersionChecker.WS_PATH, RESPONSE_FILE_FAIL);

    exception.expect(UnsupportedServerException.class);
    exception.expectMessage("The following plugins do not meet the required minimum version");
    checker.checkPlugins("5.2");
  }
}
