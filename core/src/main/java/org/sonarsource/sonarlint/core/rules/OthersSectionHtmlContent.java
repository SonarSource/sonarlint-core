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
package org.sonarsource.sonarlint.core.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class OthersSectionHtmlContent {

  private static final String FOLDER_NAME = "/context-rule-description/";
  private static final String FILE_EXTENSION = ".html";
  private static final String UNSUPPORTED_RULE_DESCRIPTION_FOR_CONTEXT_KEY = "Unsupported rule description for context key: ";
  private static final String ERROR_READING_FILE_CONTENT = "Could not read the content for rule description for context key: ";

  private static final String OTHERS_SECTION_HTML_CONTENT_KEY = "others_section_html_content";
  private OthersSectionHtmlContent() {}

  public static String getHtmlContent() {
    try (var htmlContentFile = OthersSectionHtmlContent.class.getResourceAsStream(FOLDER_NAME +
      OTHERS_SECTION_HTML_CONTENT_KEY + FILE_EXTENSION)) {
      if (htmlContentFile == null) {
        SonarLintLogger.get().info(UNSUPPORTED_RULE_DESCRIPTION_FOR_CONTEXT_KEY + OTHERS_SECTION_HTML_CONTENT_KEY);
        return "";
      }

      return IOUtils.toString(htmlContentFile, StandardCharsets.UTF_8).trim().replaceAll("\\r\\n?", "\n");
    } catch (IOException ioException) {
      SonarLintLogger.get().error(ERROR_READING_FILE_CONTENT + OTHERS_SECTION_HTML_CONTENT_KEY, ioException);
      return "";
    }
  }

}
