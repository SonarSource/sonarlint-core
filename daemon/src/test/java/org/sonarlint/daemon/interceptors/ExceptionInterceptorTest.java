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
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.daemon.interceptors.ExceptionInterceptor.TransformStatusServerCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ExceptionInterceptorTest {
  @Test
  public void testTransformStatusWithCause() {
    Status status = transformStatus(new IllegalStateException("my message"));
    assertThat(status.getDescription()).isEqualTo("my message");
  }

  @Test
  public void testTransformStatusWithoutCause() {
    Status status = transformStatus(null);
    assertThat(status.getDescription()).isNull();
  }

  private Status transformStatus(Exception cause) {
    ArgumentCaptor<Status> argument = ArgumentCaptor.forClass(Status.class);
    ServerCall<Void> delegate = mock(ServerCall.class);
    Status status = Status.UNKNOWN.withCause(cause);
    assertThat(status.getDescription()).isNull();
    Metadata trailers = new Metadata();
    TransformStatusServerCall<Void> serverCall = new TransformStatusServerCall<>(delegate);
    serverCall.close(status, trailers);
    verify(delegate).close(argument.capture(), any(Metadata.class));
    return argument.getValue();
  }

  @Test
  public void testInterceptor() {
    ExceptionInterceptor interceptor = new ExceptionInterceptor();
    MethodDescriptor<Void, Void> method = mock(MethodDescriptor.class);
    ServerCall<Void> call = mock(ServerCall.class);
    Metadata headers = new Metadata();
    ServerCallHandler<Void, Void> next = mock(ServerCallHandler.class);

    interceptor.interceptCall(method, call, headers, next);

    verify(next).startCall(eq(method), isA(TransformStatusServerCall.class), eq(headers));
  }
}
