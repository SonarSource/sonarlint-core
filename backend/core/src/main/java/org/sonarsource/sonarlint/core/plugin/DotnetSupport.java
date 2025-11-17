/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class DotnetSupport {
  @Nullable
  private final Path actualCsharpAnalyzerPath;
  private final boolean supportsCsharp;
  private final boolean supportsVbNet;
  private final boolean shouldUseCsharpEnterprise;
  private final boolean shouldUseVbNetEnterprise;

  DotnetSupport(InitializeParams initializeParams, @Nullable Path actualCsharpAnalyzerPath, boolean shouldUseCsharpEnterprise, boolean shouldUseVbNetEnterprise) {
    supportsCsharp = initializeParams.getEnabledLanguagesInStandaloneMode().contains(Language.CS);
    supportsVbNet = initializeParams.getEnabledLanguagesInStandaloneMode().contains(Language.VBNET);
    this.actualCsharpAnalyzerPath = actualCsharpAnalyzerPath;
    this.shouldUseCsharpEnterprise = shouldUseCsharpEnterprise;
    this.shouldUseVbNetEnterprise = shouldUseVbNetEnterprise;
  }

  @Nullable
  public Path getActualCsharpAnalyzerPath() {
    return actualCsharpAnalyzerPath;
  }

  public boolean isSupportsCsharp() {
    return supportsCsharp;
  }

  public boolean isSupportsVbNet() {
    return supportsVbNet;
  }

  public boolean isShouldUseCsharpEnterprise() {
    return shouldUseCsharpEnterprise;
  }

  public boolean isShouldUseVbNetEnterprise() {
    return shouldUseVbNetEnterprise;
  }
}
