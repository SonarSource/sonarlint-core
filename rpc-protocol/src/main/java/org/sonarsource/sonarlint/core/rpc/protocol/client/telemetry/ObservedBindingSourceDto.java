/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

import java.nio.file.Path;
import javax.annotation.Nullable;

public class ObservedBindingSourceDto {
  @Nullable
  private final Path declarationRoot;
  private final BindingSource bindingSource;
  private final int declaredBindingCount;

  /**
   * @param declarationRoot the directory the declaration applies to: the root (workspace folder, project or solution)
   *                        for IDE settings, the directory of the file for a .sonar-config.json; {@code null} when it has
   *                        no local path. Only used to resolve the git repository; never sent.
   */
  public ObservedBindingSourceDto(@Nullable Path declarationRoot, BindingSource bindingSource, int declaredBindingCount) {
    this.declarationRoot = declarationRoot;
    this.bindingSource = bindingSource;
    this.declaredBindingCount = declaredBindingCount;
  }

  @Nullable
  public Path getDeclarationRoot() {
    return declarationRoot;
  }

  public BindingSource getBindingSource() {
    return bindingSource;
  }

  public int getDeclaredBindingCount() {
    return declaredBindingCount;
  }
}
