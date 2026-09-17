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
package org.sonarsource.sonarlint.core.ai.ide;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

final class OsPathHelpers {

  private static final Pattern PATH_HELPER_OUTPUT_PATTERN = Pattern.compile("^\\s*PATH=\"([^\"]+)\"; export PATH;?\\s*$");

  private OsPathHelpers() {
  }

  static Optional<String> pathFromPathHelperOutput(String line) {
    var matcher = PATH_HELPER_OUTPUT_PATTERN.matcher(line);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  static String[] splitPathEntries(String path, boolean windows) {
    var separator = windows ? ";" : ":";
    return path.split(Pattern.quote(separator));
  }

  @Nullable
  static String environmentVariable(Map<String, String> environment, String name, boolean windows) {
    var value = environment.get(name);
    if (value != null || !windows) {
      return value;
    }
    return environment.entrySet().stream()
      .filter(entry -> entry.getKey().equalsIgnoreCase(name))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);
  }
}
