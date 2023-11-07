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
package org.sonarsource.sonarlint.core.file;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class FilePathTranslationRepository {
  private final Map<String, FilePathTranslation> pathTranslationsByConfigScopeId = new ConcurrentHashMap<>();

  public void setPathTranslation(String configurationScopeId, FilePathTranslation translation) {
    pathTranslationsByConfigScopeId.put(configurationScopeId, translation);
  }

  public Optional<FilePathTranslation> getPathTranslation(String configurationScopeId) {
    return Optional.ofNullable(pathTranslationsByConfigScopeId.get(configurationScopeId));
  }
}
