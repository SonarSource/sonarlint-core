/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.hotspot;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface HotspotService {

  @JsonNotification
  void openHotspotInBrowser(OpenHotspotInBrowserParams params);

  /**
   * <p>Request if the local detection of hotspots is supported. It is the case when the configuration scope provided as a parameter:
   * <ul>
   *     <li>is bound to SonarCloud</li>
   *     <li>is bound to SonarQube 9.7</li>
   * </ul>
   * For the latter, the check is made on the storage data, no query will be made to the server. If there is no storage, local detection is considered unsupported.
   * </p>
   * <p>
   * In all other cases, local detection is not supported:
   * <ul>
   *     <li>the configuration scope is bound to SonarQube < 9.7</li>
   *     <li>the configuration scope has no effective binding</li>
   * </ul>
   * The corresponding reason will be returned in the response.
   * </p>
   * <p>
   * This method will fail if:
   * <ul>
   *   <li>the provided configuration scope is unknown</li>
   *   <li>the configuration scope is bound to an unknown connection</li>
   * </ul>
   * In those cases, a failed future will be returned.
   * </p>
   */
  @JsonRequest
  CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(CheckLocalDetectionSupportedParams params);

  /**
   * Checks if the user has permission to change the hotspot review status.
   * Also returns the list of allowed statuses.The list differs between SonarQube and SonarCloud, so different values will be returned based on the connectionId:
   * <ul>
   *   <li>For SonarCloud, the allowed statuses are {@link HotspotStatus#TO_REVIEW}, {@link HotspotStatus#SAFE} and {@link HotspotStatus#FIXED}</li>
   *   <li>For SonarQube, on top of the previous ones, the {@link HotspotStatus#ACKNOWLEDGED} status is also allowed</li>
   * </ul>
   * <p>
   * This method will fail if:
   * <ul>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   *   <li>the connectionId provided as a parameter is unknown</li>
   * </ul>
   * In those cases, a failed future will be returned.
   * </p>
   *
   */
  @JsonRequest
  CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params);

  /**
   * <p>This method achieves several things:
   * <ul>
   *   <li>Changes the hotspot status on the SonarQube bound to the provided configuration scope</li>
   *   <li>Updates the hotspot status in the local storage</li>
   *   <li>Increment the 'hotspot.status_changed_count' counter for telemetry</li>
   * </ul>
   *</p>
   * <p>
   * This method will fail if:
   * <ul>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   * </ul>
   * In those cases, a failed future will be returned.
   * </p>
   *<p>
   * This method will silently deal with the following conditions:
   * <ul>
   *   <li>the provided configuration scope ID is unknown</li>
   *   <li>the connection bound to the configuration scope is unknown</li>
   *   <li>the hotspotKey is not found in the local storage</li>
   * </ul>
   * In those cases a completed future will be returned.
   * </p>
   */
  @JsonRequest
  CompletableFuture<Void> changeStatus(ChangeHotspotStatusParams params);
}
