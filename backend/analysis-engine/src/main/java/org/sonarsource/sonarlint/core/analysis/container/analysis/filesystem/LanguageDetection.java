/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.util.stream.Collectors.joining;

/**
 * Detect language of a source file based on its suffix and configured patterns.
 */
public class LanguageDetection {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  /**
   * Lower-case extension -> languages
   */
  private final Map<SonarLanguage, String[]> extensionsByLanguage = new LinkedHashMap<>();

  public LanguageDetection(Configuration config) {
    for (SonarLanguage language : SonarLanguage.values()) {
      var extensions = config.get(language.getFileSuffixesPropKey()).isPresent() ? config.getStringArray(language.getFileSuffixesPropKey())
        : language.getDefaultFileSuffixes();
      for (var i = 0; i < extensions.length; i++) {
        var suffix = extensions[i];
        extensions[i] = sanitizeExtension(suffix);
      }
      extensionsByLanguage.put(language, extensions);
    }
  }

  @CheckForNull
  public SonarLanguage language(InputFile inputFile) {
    SonarLanguage detectedLanguage = null;
    for (Entry<SonarLanguage, String[]> languagePatterns : extensionsByLanguage.entrySet()) {
      if (isCandidateForLanguage(inputFile, languagePatterns.getValue())) {
        if (detectedLanguage == null) {
          detectedLanguage = languagePatterns.getKey();
        } else {
          // Language was already forced by another pattern
          throw MessageException.of(MessageFormat.format("Language of file \"{0}\" can not be decided as the file extension matches both {1} and {2}",
            inputFile.uri(), getDetails(detectedLanguage), getDetails(languagePatterns.getKey())));
        }
      }
    }
    if (detectedLanguage != null) {
      LOG.debug("Language of file \"{}\" is detected to be \"{}\"", inputFile.uri(), detectedLanguage);
      return detectedLanguage;
    }
    return null;
  }

  private static boolean isCandidateForLanguage(InputFile inputFile, String[] extensions) {
    for (String extension : extensions) {
      if (inputFile.filename().toLowerCase(Locale.ENGLISH).endsWith("." + extension)) {
        return true;
      }
    }
    return false;
  }

  private String getDetails(SonarLanguage detectedLanguage) {
    return detectedLanguage + ": "
      + Arrays.stream(extensionsByLanguage.get(detectedLanguage))
        .collect(joining(","));
  }

  public static String sanitizeExtension(String suffix) {
    return StringUtils.lowerCase(StringUtils.removeStart(suffix, "."));
  }
}
