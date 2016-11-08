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
package org.sonarsource.sonarlint.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.TestClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class LoggedErrorHandlerTest {
  private LoggedErrorHandler handler;
  private ClientInputFile file1;
  private ClientInputFile file2;

  @Before
  public void setUp() {
    file1 = new TestClientInputFile(Paths.get("/my/file1"), false, StandardCharsets.UTF_8);
    file2 = new TestClientInputFile(Paths.get("/my/file2"), false, StandardCharsets.UTF_8);

    handler = new LoggedErrorHandler(Arrays.asList(new ClientInputFile[] {file1, file2}));
  }

  @Test
  public void testException() {
    handler.handleException("java.lang.IllegalStateException");
    assertThat(handler.getErrorFiles()).containsOnly(file1, file2);
  }

  @Test
  public void testOtherException() {
    handler.handleException("java.lang.IllegalArgumentException");
    assertThat(handler.getErrorFiles()).isEmpty();
  }

  @Test
  public void testParsingError() {
    handler.handleError("Unable to parse source file : /my/file1");
    assertThat(handler.getErrorFiles()).containsOnly(file1);
  }
  
  @Test
  public void testParsingErrorJs() {
    handler.handleError("Unable to parse file: /my/file1");
    assertThat(handler.getErrorFiles()).containsOnly(file1);
  }

  @Test
  public void testOtherError() {
    handler.handleError("Unablerewe to parse source file : /my/file1");
    assertThat(handler.getErrorFiles()).isEmpty();
  }
}
