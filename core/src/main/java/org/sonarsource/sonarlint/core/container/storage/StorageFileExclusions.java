/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.config.internal.ConfigurationBridge;
import org.sonar.api.config.internal.MapSettings;
import org.sonarsource.sonarlint.core.container.analysis.ExclusionFilters;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;

import static java.util.stream.Collectors.toList;

public class StorageFileExclusions {
  private final StorageReader storageReader;

  public StorageFileExclusions(StorageReader storageReader) {
    this.storageReader = storageReader;
  }

  public <G> List<G> getExcludedFiles(String projectKey, Collection<G> files, Function<G, String> filePathExtractor, Predicate<G> testFilePredicate) {
    GlobalProperties globalProps = storageReader.readGlobalProperties();
    ProjectConfiguration projectConfig = storageReader.readProjectConfig(projectKey);
    MapSettings settings = new MapSettings();
    settings.addProperties(globalProps.getProperties());
    settings.addProperties(projectConfig.getProperties());
    ExclusionFilters exclusionFilters = new ExclusionFilters(new ConfigurationBridge(settings));
    exclusionFilters.prepare();

    return files.stream()
      .filter(file -> !exclusionFilters.accept(filePathExtractor.apply(file), testFilePredicate.test(file) ? Type.TEST : Type.MAIN))
      .collect(toList());
  }
}
