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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.nio.file.Path;
import javax.annotation.Nullable;

public class OmnisharpRequirementsDto {
  /**
   * Path to the Mono OmniSharp runtime distribution. May be null when SLCORE downloads OmniSharp automatically.
   */
  @Nullable
  private final Path monoDistributionPath;
  /**
   * Path to the .NET 6 OmniSharp runtime distribution. May be null when SLCORE downloads OmniSharp automatically.
   */
  @Nullable
  private final Path dotNet6DistributionPath;
  /**
   * Path to the .NET Framework 4.7.2 OmniSharp runtime distribution. May be null when SLCORE downloads OmniSharp automatically.
   */
  @Nullable
  private final Path dotNet472DistributionPath;
  private final Path ossAnalyzerPath;
  private final Path enterpriseAnalyzerPath;

  public OmnisharpRequirementsDto(@Nullable Path monoDistributionPath, @Nullable Path dotNet6DistributionPath, @Nullable Path dotNet472DistributionPath,
    Path ossAnalyzerPath, Path enterpriseAnalyzerPath) {
    this.monoDistributionPath = monoDistributionPath;
    this.dotNet6DistributionPath = dotNet6DistributionPath;
    this.dotNet472DistributionPath = dotNet472DistributionPath;
    this.ossAnalyzerPath = ossAnalyzerPath;
    this.enterpriseAnalyzerPath = enterpriseAnalyzerPath;
  }

  @Nullable
  public Path getMonoDistributionPath() {
    return monoDistributionPath;
  }

  @Nullable
  public Path getDotNet6DistributionPath() {
    return dotNet6DistributionPath;
  }

  @Nullable
  public Path getDotNet472DistributionPath() {
    return dotNet472DistributionPath;
  }

  public Path getOssAnalyzerPath() {
    return ossAnalyzerPath;
  }

  public Path getEnterpriseAnalyzerPath() {
    return enterpriseAnalyzerPath;
  }
}
