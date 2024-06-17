/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.grip;

import java.net.URI;
import java.util.UUID;

public class ProvideFeedbackParams {
  private final URI serviceURI;
  private final String authenticationToken;
  private final UUID correlationId;
  private final boolean accepted;
  private final FeedbackRating rating;
  private final String comment;

  public ProvideFeedbackParams(URI serviceURI, String authenticationToken, UUID correlationId, boolean accepted, FeedbackRating rating, String comment) {
    this.serviceURI = serviceURI;
    this.authenticationToken = authenticationToken;
    this.correlationId = correlationId;
    this.accepted = accepted;
    this.rating = rating;
    this.comment = comment;
  }

  public URI getServiceURI() {
    return serviceURI;
  }

  public String getAuthenticationToken() {
    return authenticationToken;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public FeedbackRating getRating() {
    return rating;
  }

  public String getComment() {
    return comment;
  }
}
