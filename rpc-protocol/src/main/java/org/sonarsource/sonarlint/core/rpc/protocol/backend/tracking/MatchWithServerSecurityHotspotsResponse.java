/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import com.google.gson.annotations.JsonAdapter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherServerOrLocalSecurityHotspotDtoAdapter;

public class MatchWithServerSecurityHotspotsResponse {

  private final Map<String, List<ServerOrLocalSecurityHotspotDto>> securityHotspotsByServerRelativePath;

  public MatchWithServerSecurityHotspotsResponse(Map<String, List<ServerOrLocalSecurityHotspotDto>> hotspotsByServerRelativePath) {
    this.securityHotspotsByServerRelativePath = hotspotsByServerRelativePath;
  }

  public Map<String, List<ServerOrLocalSecurityHotspotDto>> getSecurityHotspotsByServerRelativePath() {
    return securityHotspotsByServerRelativePath;
  }

  @JsonAdapter(EitherServerOrLocalSecurityHotspotDtoAdapter.Factory.class)
  public static class ServerOrLocalSecurityHotspotDto {

    private Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto> wrapped;

    public ServerOrLocalSecurityHotspotDto(Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto> wrapped) {
      this.wrapped = wrapped;
    }

    public Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto> getWrapped() {
      return wrapped;
    }

    public static ServerOrLocalSecurityHotspotDto forLeft(ServerMatchedSecurityHotspotDto left) {
      return new ServerOrLocalSecurityHotspotDto(Either.forLeft(left));
    }

    public static ServerOrLocalSecurityHotspotDto forRight(LocalOnlySecurityHotspotDto right) {
      return new ServerOrLocalSecurityHotspotDto(Either.forRight(right));
    }

    public ServerMatchedSecurityHotspotDto getLeft() {
      return wrapped.getLeft();
    }

    public LocalOnlySecurityHotspotDto getRight() {
      return wrapped.getRight();
    }

    public boolean isLeft() {
      return wrapped.isLeft();
    }

    public boolean isRight() {
      return wrapped.isRight();
    }

    public <T> T map(
      Function<? super ServerMatchedSecurityHotspotDto, ? extends T> mapLeft,
      Function<? super LocalOnlySecurityHotspotDto, ? extends T> mapRight) {
      return wrapped.map(mapLeft, mapRight);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ServerOrLocalSecurityHotspotDto that = (ServerOrLocalSecurityHotspotDto) o;
      return Objects.equals(wrapped, that.wrapped);
    }

    @Override
    public int hashCode() {
      return Objects.hash(wrapped);
    }
  }
}
