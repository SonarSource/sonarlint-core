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

import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationResponse;

import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

@Named
@Singleton
public class FileService {
  private final FilePathTranslationRepository pathTranslationRepository;

  public FileService(FilePathTranslationRepository pathTranslationRepository) {
    this.pathTranslationRepository = pathTranslationRepository;
  }

  public GetPathTranslationResponse getPathTranslation(GetPathTranslationParams params) {
    return pathTranslationRepository.getPathTranslation(params.getConfigurationScopeId())
      .map(
        translation -> new GetPathTranslationResponse(separatorsToUnix(translation.getIdePathPrefix().toString()), separatorsToUnix(translation.getServerPathPrefix().toString())))
      .orElseGet(() -> new GetPathTranslationResponse(null, null));
  }
}
