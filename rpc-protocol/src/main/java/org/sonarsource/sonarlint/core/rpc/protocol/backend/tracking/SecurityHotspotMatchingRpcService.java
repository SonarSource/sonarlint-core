/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;

/**
 * @deprecated Use {@link AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)} instead.
 */
@Deprecated(since = "10.2")
@JsonSegment("hotspotMatching")
public interface SecurityHotspotMatchingRpcService {
  /**
   * Warning: this method will eventually become internal to the backend. It is exposed as an intermediate step during migration.
   *
   * <p>This method accepts a list of raw security hotspots grouped by the server relative file path in which they were detected.
   * This method returns a list of matched security hotspots grouped by the server relative file path in which they were detected.
   * It is guaranteed that the size and order of the matched security hotspots list in the response will be the same as the locally
   * tracked security hotspots list in the parameters.
   * If the provided configuration scope is not bound, the security hotspots are considered local-only and assigned a unique identifier if they don't have one.
   * </p>
   */
  @JsonRequest
  CompletableFuture<MatchWithServerSecurityHotspotsResponse> matchWithServerSecurityHotspots(MatchWithServerSecurityHotspotsParams params);
}
