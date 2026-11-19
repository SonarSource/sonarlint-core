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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.util.git.GitService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSourcesObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.BindingSourceObservedPayload;

/**
 * Turns the binding sources reported by the client into events: one event per valid declaration, all sharing a
 * snapshot id generated for the report. Sources of the same kind declared at the same root (several folders resolving
 * to one .sonar-config.json) are one declaration. The git repository of a declaration is resolved from its root, and
 * reported as a random id shared by the declarations of that repository for the lifetime of the backend; the
 * repository itself is never sent.
 */
public class BindingSourceTelemetryService {

  private final Map<Path, String> repositoryIds = new ConcurrentHashMap<>();

  public List<BindingSourceObservedPayload> toEvents(@Nullable BindingSourcesObservedParams params) {
    if (params == null || params.getSources() == null) {
      return List.of();
    }
    var snapshotId = UUID.randomUUID().toString();
    var declarations = new HashSet<Declaration>();
    return params.getSources().stream()
      .filter(BindingSourceTelemetryService::isValid)
      .filter(source -> source.getDeclarationRoot() == null
        || declarations.add(new Declaration(source.getBindingSource(), source.getDeclarationRoot().toAbsolutePath().normalize())))
      .map(source -> new BindingSourceObservedPayload(snapshotId, repositoryIdOf(source.getDeclarationRoot()), toPayload(source.getBindingSource()),
        source.getDeclaredBindingCount()))
      .toList();
  }

  private static boolean isValid(@Nullable ObservedBindingSourceDto source) {
    return source != null && source.getBindingSource() != null && source.getDeclaredBindingCount() >= 1;
  }

  @CheckForNull
  private String repositoryIdOf(@Nullable Path declarationRoot) {
    var repositoryRoot = declarationRoot == null ? null : GitService.findWorkTreeRoot(declarationRoot);
    return repositoryRoot == null ? null : repositoryIds.computeIfAbsent(repositoryRoot, root -> UUID.randomUUID().toString());
  }

  private record Declaration(BindingSource source, Path root) {
  }

  private static BindingSourceObservedPayload.BindingSource toPayload(BindingSource source) {
    return switch (source) {
      case IDE_SETTINGS -> BindingSourceObservedPayload.BindingSource.IDE_SETTINGS;
      case SONAR_CONFIG -> BindingSourceObservedPayload.BindingSource.SONAR_CONFIG;
    };
  }
}
