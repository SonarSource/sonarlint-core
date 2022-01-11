/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.PathUtils;
import org.sonar.api.utils.WildcardPattern;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * Path relative to module basedir
 */
public class SonarLintPathPattern {

  private static final Logger LOG = Loggers.get(SonarLintPathPattern.class);

  final WildcardPattern pattern;

  public SonarLintPathPattern(String pattern) {
    if (pattern.startsWith("file:")) {
      LOG.warn("Unsupported path pattern: " + pattern);
      pattern = pattern.replaceAll("^file:/*", "");
    }
    if (!pattern.startsWith("**/")) {
      pattern = "**/" + pattern;
    }
    this.pattern = WildcardPattern.create(pattern);
  }

  public static SonarLintPathPattern[] create(String[] s) {
    SonarLintPathPattern[] result = new SonarLintPathPattern[s.length];
    for (int i = 0; i < s.length; i++) {
      result[i] = new SonarLintPathPattern(s[i]);
    }
    return result;
  }

  public boolean match(InputFile inputFile) {
    return match(inputFile.relativePath(), true);
  }

  public boolean match(String filePath) {
    return match(filePath, true);
  }

  public boolean match(InputFile inputFile, boolean caseSensitiveFileExtension) {
    return match(inputFile.relativePath(), caseSensitiveFileExtension);
  }

  public boolean match(String filePath, boolean caseSensitiveFileExtension) {
    String path = PathUtils.sanitize(filePath);
    if (!caseSensitiveFileExtension) {
      String extension = sanitizeExtension(FilenameUtils.getExtension(path));
      if (StringUtils.isNotBlank(extension)) {
        path = StringUtils.removeEndIgnoreCase(path, extension);
        path = path + extension;
      }
    }
    return path != null && pattern.match(path);
  }

  @Override
  public String toString() {
    return pattern.toString();
  }

  static String sanitizeExtension(String suffix) {
    return StringUtils.lowerCase(StringUtils.removeStart(suffix, "."));
  }
}
