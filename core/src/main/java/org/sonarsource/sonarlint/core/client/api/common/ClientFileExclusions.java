/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Exclusions configured on client side
 */
public class ClientFileExclusions implements Predicate<String> {
  private static final String SYNTAX = "glob";

  private final List<PathMatcher> matchers;
  private final Set<String> directoryExclusions;
  private final Set<String> fileExclusions;

  public ClientFileExclusions(Set<String> fileExclusions, Set<String> directoryExclusions, Set<String> globPatterns) {
    this.fileExclusions = fileExclusions;
    this.directoryExclusions = directoryExclusions;
    this.matchers = parseGlobPatterns(globPatterns);
  }

  private static List<PathMatcher> parseGlobPatterns(Set<String> globPatterns) {
    var fs = FileSystems.getDefault();

    List<PathMatcher> parsedMatchers = new ArrayList<>(globPatterns.size());
    for (String pattern : globPatterns) {
      try {
        parsedMatchers.add(fs.getPathMatcher(SYNTAX + ":" + pattern));
      } catch (Exception e) {
        // ignore invalid patterns, simply skip them
      }
    }
    return parsedMatchers;
  }

  public boolean test(Path path) {
    return testFileExclusions(path) || testDirectoryExclusions(path) || testGlob(path);
  }

  private boolean testGlob(Path path) {
    return matchers.stream().anyMatch(matcher -> matcher.matches(path));
  }

  private boolean testFileExclusions(Path path) {
    return fileExclusions.contains(path.toString());
  }

  private boolean testDirectoryExclusions(Path path) {
    var p = path;
    while (p != null) {
      if (directoryExclusions.contains(p.toString())) {
        return true;
      }
      p = p.getParent();
    }
    return false;
  }

  @Override
  public boolean test(String string) {
    return test(Paths.get(string));
  }
}
