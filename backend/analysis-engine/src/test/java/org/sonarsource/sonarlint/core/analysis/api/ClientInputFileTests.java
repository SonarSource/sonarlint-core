/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ClientInputFileTests {

  @Test
  void testDefaults(@TempDir Path tempDir) throws IOException {
    var path = tempDir.resolve("Foo.java");
    ClientInputFile underTest = new ClientInputFile() {

      @Override
      public boolean isTest() {
        return false;
      }

      @Override
      public InputStream inputStream() throws IOException {
        return null;
      }

      @Override
      public String getPath() {
        return path.toAbsolutePath().toString();
      }

      @Override
      public String relativePath() {
        return path.getParent().toString();
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }

      @Override
      public Charset getCharset() {
        return null;
      }

      @Override
      public String contents() throws IOException {
        return null;
      }

      @Override
      public URI uri() {
        return path.toUri();
      }
    };

    assertThat(underTest.language()).isNull();
    assertThat(underTest.uri()).hasScheme("file");
    assertThat(underTest.uri().getPath()).endsWith("/Foo.java");
  }

}
