/*
 * SonarLint Daemon
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarlint.daemon.interceptors;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Status;

public class ExceptionInterceptor implements ServerInterceptor {
  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(MethodDescriptor<ReqT, RespT> method, ServerCall<RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    ServerCall<RespT> serverCall = new TransformStatusServerCall<>(call);
    return next.startCall(method, serverCall, headers);
  }

  static class TransformStatusServerCall<RespT> extends SimpleForwardingServerCall<RespT> {
    protected TransformStatusServerCall(ServerCall<RespT> delegate) {
      super(delegate);
    }

    @Override
    public void close(Status status, Metadata trailers) {
      Status transformed = status;
      if (transformed.getDescription() == null && transformed.getCause() != null) {
        transformed = status.withDescription(status.getCause().getMessage());
      }
      super.close(transformed, trailers);
    }
  }
}
