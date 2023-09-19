/*
 * SonarLint Core - Implementation
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
package testutils;

import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.google.protobuf.Message;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

public class TestUtils {

  public static class NoOpIssueListener implements IssueListener {
    @Override
    public void handle(Issue issue) {
    }
  };

  public static NoOpIssueListener createNoOpIssueListener() {
    return new NoOpIssueListener();
  }

  public static class NoOpLogOutput implements ClientLogOutput {
    @Override
    public void log(String formattedMessage, Level level) {
      // Don't pollute logs
    }
  }

  public static NoOpLogOutput createNoOpLogOutput() {
    return new NoOpLogOutput();
  }

  public static ClientInputFile createInputFile(final Path path, String relativePath, final boolean isTest) {
    return createInputFile(path, relativePath, isTest, StandardCharsets.UTF_8);
  }

  public static ClientInputFile createInputFile(final Path path, String relativePath, final boolean isTest, final Charset encoding) {
    return new OnDiskTestClientInputFile(path, relativePath, isTest, encoding);
  }

  public static Body protobufBody(Message message) {
    var baos = new ByteArrayOutputStream();
    try {
      message.writeTo(baos);
      return Body.ofBinaryOrText(baos.toByteArray(), ContentTypeHeader.absent());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Body protobufBodyDelimited(Message... messages) {
    var baos = new ByteArrayOutputStream();
    try {
      for (var message : messages) {
        message.writeDelimitedTo(baos);
      }
      return Body.ofBinaryOrText(baos.toByteArray(), ContentTypeHeader.absent());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String generateThreadDump() {
    final var dump = new StringBuilder();
    final var threadMXBean = ManagementFactory.getThreadMXBean();
    final var threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
    for (var threadInfo : threadInfos) {
      dump.append('"');
      dump.append(threadInfo.getThreadName());
      dump.append("\" ");
      final var state = threadInfo.getThreadState();
      dump.append("\n   java.lang.Thread.State: ");
      dump.append(state);
      final var stackTraceElements = threadInfo.getStackTrace();
      for (final var stackTraceElement : stackTraceElements) {
        dump.append("\n        at ");
        dump.append(stackTraceElement);
      }
      dump.append("\n\n");
    }
    return dump.toString();
  }

  public static void printThreadDump() {
    System.out.println(generateThreadDump());
  }

}
