/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static java.util.stream.Collectors.joining;

/**
 * Detect language of a source file based on its suffix and configured patterns.
 */
public class LanguageDetection {

  private static final Logger LOG = Loggers.get(LanguageDetection.class);

  /**
   * Lower-case extension -> languages
   */
  private final Map<Language, String[]> extensionsByLanguage = new LinkedHashMap<>();

  public LanguageDetection(Configuration config) {
    for (Language language : Language.values()) {
      String[] extensions = config.get(language.getFileSuffixesPropKey()).isPresent() ? config.getStringArray(language.getFileSuffixesPropKey())
        : language.getDefaultFileSuffixes();
      for (int i = 0; i < extensions.length; i++) {
        String suffix = extensions[i];
        extensions[i] = sanitizeExtension(suffix);
      }
      extensionsByLanguage.put(language, extensions);
    }
  }

  @CheckForNull
  public Language language(InputFile inputFile) {
    Language detectedLanguage = null;
    for (Entry<Language, String[]> languagePatterns : extensionsByLanguage.entrySet()) {
      if (isCandidateForLanguage(inputFile, languagePatterns.getValue())) {
        if (detectedLanguage == null) {
          detectedLanguage = languagePatterns.getKey();
        } else {
          // Language was already forced by another pattern
          throw MessageException.of(MessageFormat.format("Language of file ''{0}'' can not be decided as the file extension matches both {1} and {2}",
            inputFile.uri(), getDetails(detectedLanguage), getDetails(languagePatterns.getKey())));
        }
      }
    }
    if (detectedLanguage != null) {
      LOG.debug("Language of file '{}' is detected to be '{}'", inputFile.uri(), detectedLanguage);
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

  private String getDetails(Language detectedLanguage) {
    return detectedLanguage + ": "
      + Arrays.stream(extensionsByLanguage.get(detectedLanguage))
        .collect(joining(","));
  }

  static String sanitizeExtension(String suffix) {
    return StringUtils.lowerCase(StringUtils.removeStart(suffix, "."));
  }
}
