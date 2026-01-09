/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public class RpcErrorHandler {

  private RpcErrorHandler() {
  }

  public static ResponseError handleError(Throwable throwable) {
    if (throwable instanceof ResponseErrorException) {
      return ((ResponseErrorException) throwable).getResponseError();
    } else if (isWrappedResponseErrorException(throwable)) {
      return ((ResponseErrorException) throwable.getCause()).getResponseError();
    } else {
      return createInternalErrorResponse("Internal error", throwable);
    }
  }

  private static boolean isWrappedResponseErrorException(Throwable throwable) {
    return (throwable instanceof CompletionException || throwable instanceof InvocationTargetException)
      && throwable.getCause() instanceof ResponseErrorException;
  }

  public static ResponseError createInternalErrorResponse(String header, Throwable throwable) {
    var error = new ResponseError();
    error.setMessage(header + ".");
    error.setCode(ResponseErrorCode.InternalError);
    var stackTraceString = toStringStacktrace(throwable);

    // Send to Sentry with hint being the full stacktrace
    var stackTraceAttachment = new Attachment(stackTraceString.getBytes(StandardCharsets.UTF_8), "stacktrace.txt");
    Sentry.captureException(throwable, Hint.withAttachment(stackTraceAttachment));

    error.setData(stackTraceString);
    return error;
  }

  private static String toStringStacktrace(Throwable throwable) {
    var stackTrace = new ByteArrayOutputStream();
    var stackTraceWriter = new PrintWriter(stackTrace);
    throwable.printStackTrace(stackTraceWriter);
    stackTraceWriter.flush();
    return stackTrace.toString(StandardCharsets.UTF_8);
  }
}
