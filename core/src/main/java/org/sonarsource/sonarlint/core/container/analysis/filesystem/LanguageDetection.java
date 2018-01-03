/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.google.common.base.Joiner;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintPathPattern;

/**
 * Detect language of a source file based on its suffix and configured patterns.
 */
public class LanguageDetection {

  private static final Logger LOG = Loggers.get(LanguageDetection.class);

  /**
   * Lower-case extension -> languages
   */
  private final Map<String, SonarLintPathPattern[]> patternsByLanguage = new LinkedHashMap<>();
  private final List<String> languagesToConsider = new ArrayList<>();

  public LanguageDetection(LanguagesRepository languages) {
    for (Language language : languages.all()) {
      String[] patterns = language.fileSuffixes().toArray(new String[language.fileSuffixes().size()]);
      for (int i = 0; i < patterns.length; i++) {
        String suffix = patterns[i];
        String extension = sanitizeExtension(suffix);
        patterns[i] = new StringBuilder().append("**/*.").append(extension).toString();
      }
      SonarLintPathPattern[] defaultLanguagePatterns = SonarLintPathPattern.create(patterns);
      patternsByLanguage.put(language.key(), defaultLanguagePatterns);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Declared extensions of language {} were converted to {}", language, getDetails(language.key()));
      }
    }

    languagesToConsider.addAll(patternsByLanguage.keySet());
  }

  Map<String, SonarLintPathPattern[]> patternsByLanguage() {
    return patternsByLanguage;
  }

  @CheckForNull
  String language(InputFile inputFile) {
    String detectedLanguage = null;
    for (String languageKey : languagesToConsider) {
      if (isCandidateForLanguage(inputFile, languageKey)) {
        if (detectedLanguage == null) {
          detectedLanguage = languageKey;
        } else {
          // Language was already forced by another pattern
          throw MessageException.of(MessageFormat.format("Language of file ''{0}'' can not be decided as the file matches patterns of both {1} and {2}",
            inputFile.uri(), getDetails(detectedLanguage), getDetails(languageKey)));
        }
      }
    }
    if (detectedLanguage != null) {
      LOG.debug("Language of file '{}' is detected to be '{}'", inputFile.uri(), detectedLanguage);
      return detectedLanguage;
    }
    return null;
  }

  private boolean isCandidateForLanguage(InputFile inputFile, String languageKey) {
    SonarLintPathPattern[] patterns = patternsByLanguage.get(languageKey);
    if (patterns != null) {
      for (SonarLintPathPattern pathPattern : patterns) {
        if (pathPattern.match(inputFile, false)) {
          return true;
        }
      }
    }
    return false;
  }

  private String getDetails(String detectedLanguage) {
    return detectedLanguage + ": " + Joiner.on(",").join(patternsByLanguage.get(detectedLanguage));
  }

  static String sanitizeExtension(String suffix) {
    return StringUtils.lowerCase(StringUtils.removeStart(suffix, "."));
  }
}
