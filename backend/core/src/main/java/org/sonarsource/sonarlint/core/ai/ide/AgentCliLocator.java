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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;

final class AgentCliLocator {

  private static final long AGENT_PROBE_TIMEOUT_MILLIS = 5_000L;
  private static final int AGENT_PROBE_MAX_LINES = 20;
  private static final int AGENT_PROBE_MAX_LINE_LENGTH = 1_024;

  private final OsExecutableSearch search;
  private final Path userHome;
  @Nullable
  private List<AiAgent> cachedDiscovery;

  AgentCliLocator(OsExecutableSearch search, Path userHome) {
    this.search = search;
    this.userHome = userHome;
  }

  List<AiAgent> discover() {
    return discover(search.resolvePath());
  }

  synchronized List<AiAgent> discover(@Nullable String resolvedPath) {
    if (cachedDiscovery == null) {
      cachedDiscovery = List.copyOf(doDiscover(resolvedPath));
    }
    return cachedDiscovery;
  }

  private List<AiAgent> doDiscover(@Nullable String resolvedPath) {
    var directories = executableSearchDirectories(resolvedPath);
    return AgentProfiles.cliDiscoverable().stream()
      .filter(profile -> isAgentCliInstalled(profile, directories, resolvedPath))
      .map(AgentProfile::agent)
      .toList();
  }

  private boolean isAgentCliInstalled(AgentProfile profile, Set<Path> directories, @Nullable String resolvedPath) {
    return profile.executableNames().stream()
      .map(baseName -> firstExistingExecutable(baseName, directories))
      .filter(Objects::nonNull)
      .anyMatch(candidate -> probeMatches(candidate, profile, resolvedPath));
  }

  @Nullable
  private Path firstExistingExecutable(String baseName, Set<Path> directories) {
    for (var directory : directories) {
      for (var executableName : search.executableNames(baseName)) {
        var candidate = directory.resolve(executableName);
        if (search.isExecutable(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }

  private boolean probeMatches(Path executable, AgentProfile profile, @Nullable String resolvedPath) {
    var stdout = new ArrayList<String>();
    var stderr = new ArrayList<String>();
    var pathOverride = search.isMac() ? resolvedPath : null;
    var result = search.execute(executable, profile.probeArguments(), pathOverride, AGENT_PROBE_TIMEOUT_MILLIS,
      line -> addProbeLine(stdout, line), line -> addProbeLine(stderr, line));
    if (result.exitCode() != 0) {
      return false;
    }
    var output = (String.join("\n", stdout) + "\n" + String.join("\n", stderr)).toLowerCase(Locale.ROOT);
    return profile.outputMarkers().stream().anyMatch(output::contains);
  }

  private Set<Path> executableSearchDirectories(@Nullable String path) {
    var directories = new LinkedHashSet<>(search.pathDirectories(path, true));
    if (!search.isWindows()) {
      directories.add(userHome.resolve(".local/bin"));
    }
    return directories;
  }

  private static void addProbeLine(List<String> output, String line) {
    if (output.size() < AGENT_PROBE_MAX_LINES) {
      output.add(line.substring(0, Math.min(line.length(), AGENT_PROBE_MAX_LINE_LENGTH)));
    }
  }
}
