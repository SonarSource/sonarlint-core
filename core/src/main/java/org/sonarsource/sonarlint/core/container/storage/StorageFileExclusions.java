/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;

public class StorageFileExclusions {
  private final IssueStorePaths issueStorePaths;

  public StorageFileExclusions(IssueStorePaths issueStorePaths) {
    this.issueStorePaths = issueStorePaths;
  }

  public <G> List<G> getExcludedFiles(ProjectStorage projectStorage, ProjectBinding projectBinding, Collection<G> files, Function<G, String> fileIdePathExtractor,
    Predicate<G> testFilePredicate) {
    var settings = new MapSettings(projectStorage.getAnalyzerConfiguration(projectBinding.projectKey()).getSettings().getAll());
    var exclusionFilters = new ExclusionFilters(settings.asConfig());
    exclusionFilters.prepare();

    List<G> excluded = new ArrayList<>();

    for (G file : files) {
      var idePath = fileIdePathExtractor.apply(file);
      if (idePath == null) {
        continue;
      }
      var sqPath = issueStorePaths.idePathToSqPath(projectBinding, idePath);
      if (sqPath == null) {
        // we can't map it to a SonarQube path, so just apply exclusions to the original ide path
        sqPath = idePath;
      }
      var type = testFilePredicate.test(file) ? Type.TEST : Type.MAIN;
      if (!exclusionFilters.accept(sqPath, type)) {
        excluded.add(file);
      }
    }
    return excluded;
  }
}
