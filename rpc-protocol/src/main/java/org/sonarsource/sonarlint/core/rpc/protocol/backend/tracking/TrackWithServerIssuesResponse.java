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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import com.google.gson.annotations.JsonAdapter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherServerOrLocalIssueDtoAdapter;

public class TrackWithServerIssuesResponse {

  private final Map<String, List<ServerOrLocalIssueDto>> issuesByServerRelativePath;

  public TrackWithServerIssuesResponse(Map<String, List<ServerOrLocalIssueDto>> issuesByServerRelativePath) {
    this.issuesByServerRelativePath = issuesByServerRelativePath;
  }

  public Map<String, List<ServerOrLocalIssueDto>> getIssuesByServerRelativePath() {
    return issuesByServerRelativePath;
  }

  @JsonAdapter(EitherServerOrLocalIssueDtoAdapter.Factory.class)
  public static class ServerOrLocalIssueDto {

    private Either<ServerMatchedIssueDto, LocalOnlyIssueDto> wrapped;

    public ServerOrLocalIssueDto(Either<ServerMatchedIssueDto, LocalOnlyIssueDto> wrapped) {
      this.wrapped = wrapped;
    }

    public Either<ServerMatchedIssueDto, LocalOnlyIssueDto> getWrapped() {
      return wrapped;
    }

    public static ServerOrLocalIssueDto forLeft(@NonNull ServerMatchedIssueDto left) {
      return new ServerOrLocalIssueDto(Either.forLeft(left));
    }

    public static ServerOrLocalIssueDto forRight(@NonNull LocalOnlyIssueDto right) {
      return new ServerOrLocalIssueDto(Either.forRight(right));
    }

    public ServerMatchedIssueDto getLeft() {
      return wrapped.getLeft();
    }

    public LocalOnlyIssueDto getRight() {
      return wrapped.getRight();
    }

    public boolean isLeft() {
      return wrapped.isLeft();
    }

    public boolean isRight() {
      return wrapped.isRight();
    }

    public <T> T map(
      @NonNull Function<? super ServerMatchedIssueDto, ? extends T> mapLeft,
      @NonNull Function<? super LocalOnlyIssueDto, ? extends T> mapRight) {
      return wrapped.map(mapLeft, mapRight);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ServerOrLocalIssueDto that = (ServerOrLocalIssueDto) o;
      return Objects.equals(wrapped, that.wrapped);
    }

    @Override
    public int hashCode() {
      return Objects.hash(wrapped);
    }
  }
}
