/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Language {

  ABAP("abap", "abap", "ABAP", new String[] {".abap", ".ab4", ".flow", ".asprog"}, "sonar.abap.file.suffixes"),
  APEX("apex", "sonarapex", "Apex", new String[] {".cls", ".trigger"}, "sonar.apex.file.suffixes"),
  C("c", "cpp", "C", new String[] {".c", ".h"}, "sonar.c.file.suffixes"),
  CPP("cpp", "cpp", "C++", new String[] {".cc", ".cpp", ".cxx", ".c++", ".hh", ".hpp", ".hxx", ".h++", ".ipp"}, "sonar.cpp.file.suffixes"),
  CS("cs", "csharp", "C#", new String[] {".cs"}, "sonar.cs.file.suffixes"),
  CSS("css", Constants.JAVASCRIPT_PLUGIN_KEY, "CSS", new String[] {".css", ".less", ".scss"}, "sonar.css.file.suffixes"),
  OBJC("objc", "cpp", "Objective-C", new String[] {".m"}, "sonar.objc.file.suffixes"),
  COBOL("cobol", "cobol", "COBOL", new String[0], "sonar.cobol.file.suffixes"),
  HTML("web", "web", "HTML", new String[] {".html", ".xhtml", ".cshtml", ".vbhtml", ".aspx", ".ascx", ".rhtml", ".erb", ".shtm", ".shtml"}, "sonar.html.file.suffixes"),
  IPYTHON("ipynb", "python", "IPython Notebooks", new String[] {".ipynb"}, "sonar.ipython.file.suffixes"),
  JAVA("java", "java", "Java", new String[] {".java", ".jav"}, "sonar.java.file.suffixes"),
  JS("js", Constants.JAVASCRIPT_PLUGIN_KEY, "JavaScript", new String[] {".js", ".jsx", ".vue"}, "sonar.javascript.file.suffixes"),
  KOTLIN("kotlin", "kotlin", "Kotlin", new String[] {".kt"}, "sonar.kotlin.file.suffixes"),
  PHP("php", "php", "PHP", new String[] {"php", "php3", "php4", "php5", "phtml", "inc"}, "sonar.php.file.suffixes"),
  PLI("pli", "pli", "PL/I", new String[] {".pli"}, "sonar.pli.file.suffixes"),
  PLSQL("plsql", "plsql", "PL/SQL", new String[] {".sql", ".pks", ".pkb"}, "sonar.plsql.file.suffixes"),
  PYTHON("py", "python", "Python", new String[] {".py"}, "sonar.python.file.suffixes"),
  RPG("rpg", "rpg", "RPG", new String[] {".rpg", ".rpgle"}, "sonar.rpg.suffixes"),
  RUBY("ruby", "ruby", "Ruby", new String[] {".rb"}, "sonar.ruby.file.suffixes"),
  SCALA("scala", "sonarscala", "Scala", new String[] {".scala"}, "sonar.scala.file.suffixes"),
  SECRETS("secrets", "text", "Secrets", new String[0], "sonar.secrets.file.suffixes"),
  SWIFT("swift", "swift", "Swift", new String[] {".swift"}, "sonar.swift.file.suffixes"),
  TSQL("tsql", "tsql", "T-SQL", new String[] {".tsql"}, "sonar.tsql.file.suffixes"),
  TS("ts", Constants.JAVASCRIPT_PLUGIN_KEY, "TypeScript", new String[] {".ts", ".tsx"},
    "sonar.typescript.file.suffixes"),
  JSP("jsp", "web", "JSP", new String[] {".jsp", ".jspf", ".jspx"}, "sonar.jsp.file.suffixes"),
  VBNET("vbnet", "vbnet", "VB.NET", new String[] {".vb"}, "sonar.vbnet.file.suffixes"),
  XML("xml", "xml", "XML", new String[] {".xml", ".xsd", ".xsl"}, "sonar.xml.file.suffixes"),
  YAML("yaml", Constants.JAVASCRIPT_PLUGIN_KEY, "YAML", new String[] {".yml", "yaml"}, "sonar.yaml.file.suffixes");
  private String languageKey;
  private String pluginKey;
  private String[] defaultFileSuffixes;
  private String fileSuffixesPropKey;
  private String label;

  private static final Map<String, Language> mMap = Collections.unmodifiableMap(initializeMapping());

  private static Map<String, Language> initializeMapping() {
    Map<String, Language> mMap = new HashMap<>();
    for (Language l : Language.values()) {
      mMap.put(l.languageKey, l);
    }
    return mMap;
  }

  Language(String languageKey, String pluginKey, String label, String[] defaultFileSuffixes, String fileSuffixesPropKey) {
    this.languageKey = languageKey;
    this.pluginKey = pluginKey;
    this.label = label;
    this.defaultFileSuffixes = defaultFileSuffixes;
    this.fileSuffixesPropKey = fileSuffixesPropKey;
  }

  public String getLanguageKey() {
    return languageKey;
  }

  public String getPluginKey() {
    return pluginKey;
  }

  public String getLabel() {
    return label;
  }

  public String[] getDefaultFileSuffixes() {
    return defaultFileSuffixes;
  }

  public String getFileSuffixesPropKey() {
    return fileSuffixesPropKey;
  }

  public boolean shouldSyncInConnectedMode() {
    return !equals(Language.IPYTHON);
  }

  public static Set<Language> getLanguagesByPluginKey(String pluginKey) {
    return Stream.of(values()).filter(l -> l.getPluginKey().equals(pluginKey)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static boolean containsPlugin(String pluginKey) {
    return Stream.of(values()).anyMatch(l -> l.getPluginKey().equals(pluginKey));
  }

  public static Optional<Language> forKey(String languageKey) {
    return Optional.ofNullable(mMap.get(languageKey));
  }

  @Override
  public String toString() {
    return getLabel();
  }

  private static class Constants {
    public static final String JAVASCRIPT_PLUGIN_KEY = "javascript";
  }

}
