/*
 * SonarLint Core - Implementation
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

package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class TextRangeUtils {

  private static final Pattern MATCH_ALL_WHITESPACES = Pattern.compile("\\s");

  private TextRangeUtils() {
    // utils
  }

  @CheckForNull
  public static TextRangeWithHash getTextRangeWithHash(@Nullable TextRange textRange, @Nullable ClientInputFile file) {
    if (textRange == null) return null;
    String hash = computeTextRangeHash(textRange, file);
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(),
      textRange.getEndLineOffset(), hash);
  }

  @CheckForNull
  public static LineWithHash getLineWithHash(@Nullable TextRange textRange, @Nullable ClientInputFile file) {
    if (textRange == null) return null;
    String hash = computeLineHash(textRange, file);
    return new LineWithHash(textRange.getStartLine(), hash);
  }

  @CheckForNull
  public static TextRangeDto toTextRangeDto(@Nullable TextRangeWithHash textRange) {
    if (textRange == null) return null;
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  @CheckForNull
  public static TextRangeDto toTextRangeDto(@Nullable TextRange textRange) {
    if (textRange == null) return null;
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(),
      textRange.getEndLineOffset());
  }

  static String computeTextRangeHash(TextRange textRange, @Nullable ClientInputFile file) {
    if (file == null) return "";
    var textRangeContent = getTextRangeContent(file, textRange);
    return hash(textRangeContent);
  }

  static String computeLineHash(TextRange textRange, @Nullable ClientInputFile file) {
    if (file == null) return "";
    var textRangeContent = getLineContent(file, textRange);
    return hash(textRangeContent);
  }

  private static String getLineContent(ClientInputFile file, TextRange textRange) {
    var fileContent = getFileContentOrEmptyString(file);
    if (StringUtils.isEmpty(fileContent)) return "";
    var lines = fileContent.lines().collect(Collectors.toList());
    if (lines.size() < textRange.getStartLine()) return "";
    var line = lines.get(textRange.getStartLine() - 1);
    return hash(line);
  }

  static String getFileContentOrEmptyString(ClientInputFile file) {
    try {
      return file.contents();
    } catch (IOException e) {
      return "";
    }
  }

  public static String getTextRangeContent(@Nullable ClientInputFile file, @Nullable TextRange textRange) {
    if (file == null || textRange == null) return "";
    var contentLines = getFileContentOrEmptyString(file).lines().collect(Collectors.toList());
    var startLine = textRange.getStartLine() - 1;
    var endLine = textRange.getEndLine() - 1;
    if (startLine == endLine) {
      var startLineContent = contentLines.get(startLine);
      var endLineOffset = Math.min(textRange.getEndLineOffset(), startLineContent.length());
      return startLineContent.substring(textRange.getStartLineOffset(), endLineOffset);
    }

    var contentBuilder = new StringBuilder();
    contentBuilder.append(contentLines.get(startLine).substring(textRange.getStartLineOffset()))
      .append(System.lineSeparator());
    for (int i = startLine + 1; i < endLine; i++) {
      contentBuilder.append(contentLines.get(i)).append(System.lineSeparator());
    }
    var endLineContent = contentLines.get(endLine);
    var endLineOffset = Math.min(textRange.getEndLineOffset(), endLineContent.length());
    contentBuilder.append(endLineContent, 0, endLineOffset);
    return contentBuilder.toString();
  }

  @CheckForNull
  public static TextRangeWithHashDto adapt(@Nullable TextRangeWithHash textRange) {
    return textRange == null ? null
      : new TextRangeWithHashDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
  }

  @CheckForNull
  public static TextRangeWithHashDto toTextRangeWithHashDto(@Nullable TextRange textRange, @Nullable ClientInputFile clientInputFile) {
    return adapt(getTextRangeWithHash(textRange, clientInputFile));
  }

  static String hash(String codeSnippet) {
    String codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("");
    return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces);
  }

  @CheckForNull
  public static LineWithHashDto getLineWithHashDto(@Nullable TextRange textRange, @Nullable ClientInputFile clientInputFile) {
    var lineWithHash = getLineWithHash(textRange, clientInputFile);
    return lineWithHash != null ? toLineWithHashDto(lineWithHash) : null;
  }

  private static LineWithHashDto toLineWithHashDto(LineWithHash lineWithHash) {
    return new LineWithHashDto(lineWithHash.getNumber(), lineWithHash.getHash());
  }
}
