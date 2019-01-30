/*
 * SonarLint Daemon
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarlint.daemon.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultClientInputFileTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testInputFile() throws IOException {
    Charset charset = StandardCharsets.UTF_16;
    Path path = temp.newFile().toPath();
    Files.write(path, "test".getBytes(charset));

    boolean isTest = true;
    String userObject = new String();
    DefaultClientInputFile file = new DefaultClientInputFile(path.getParent(), path, isTest, charset, userObject, "cpp");

    assertThat(file.getCharset()).isEqualTo(charset);
    assertThat(file.isTest()).isEqualTo(isTest);
    assertThat(file.language()).isEqualTo("cpp");
    assertThat(file.getPath()).isEqualTo(path.toString());
    assertThat(file.contents()).isEqualTo("test");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.inputStream(), charset))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test");
    }

    assertThat((String) file.getClientObject()).isEqualTo(userObject);
  }
}
