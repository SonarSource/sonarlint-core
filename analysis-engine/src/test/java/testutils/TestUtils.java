/*
 * SonarLint Core - Analysis Engine
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
package testutils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.LogOutput;

public class TestUtils {

  public static Consumer<Issue> noOpIssueListener() {
    return i -> {
    };
  }

  public static class NoOpLogOutput implements LogOutput {
    @Override
    public void log(String formattedMessage, Level level) {
      // Don't pollute logs
    }
  }

  public static NoOpLogOutput createNoOpLogOutput() {
    return new NoOpLogOutput();
  }

  public static ClientInputFile createInputFile(final Path path, String relativePath, final boolean isTest) {
    return createInputFile(path, relativePath, isTest, StandardCharsets.UTF_8);
  }

  public static ClientInputFile createInputFile(final Path path, String relativePath, final boolean isTest, final Charset encoding) {
    return new OnDiskTestClientInputFile(path, relativePath, isTest, encoding);
  }

}
