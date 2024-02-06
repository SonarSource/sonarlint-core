/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum SonarLanguage {

  ABAP("abap", "abap", new String[]{".abap", ".ab4", ".flow", ".asprog"}, "sonar.abap.file.suffixes"),
  APEX("apex", "sonarapex", new String[]{".cls", ".trigger"}, "sonar.apex.file.suffixes"),
  C("c", "cpp", new String[]{".c", ".h"}, "sonar.c.file.suffixes"),
  CPP("cpp", "cpp", new String[]{".cc", ".cpp", ".cxx", ".c++", ".hh", ".hpp", ".hxx", ".h++", ".ipp"}, "sonar.cpp.file.suffixes"),
  CS("cs", "csharp", new String[]{".cs"}, "sonar.cs.file.suffixes"),
  CSS("css", Constants.JAVASCRIPT_PLUGIN_KEY, new String[]{".css", ".less", ".scss"}, "sonar.css.file.suffixes"),
  OBJC("objc", "cpp", new String[]{".m"}, "sonar.objc.file.suffixes"),
  COBOL("cobol", "cobol", new String[0], "sonar.cobol.file.suffixes"),
  HTML("web", "web", new String[]{".html", ".xhtml", ".cshtml", ".vbhtml", ".aspx", ".ascx", ".rhtml", ".erb", ".shtm", ".shtml"}, "sonar.html.file.suffixes"),
  IPYTHON("ipynb", "python", new String[]{".ipynb"}, "sonar.ipython.file.suffixes"),
  JAVA("java", "java", new String[]{".java", ".jav"}, "sonar.java.file.suffixes"),
  JS("js", Constants.JAVASCRIPT_PLUGIN_KEY, new String[]{".js", ".jsx", ".vue"}, "sonar.javascript.file.suffixes"),
  KOTLIN("kotlin", "kotlin", new String[]{".kt", ".kts"}, "sonar.kotlin.file.suffixes"),
  PHP("php", "php", new String[]{"php", "php3", "php4", "php5", "phtml", "inc"}, "sonar.php.file.suffixes"),
  PLI("pli", "pli", new String[]{".pli"}, "sonar.pli.file.suffixes"),
  PLSQL("plsql", "plsql", new String[]{".sql", ".pks", ".pkb"}, "sonar.plsql.file.suffixes"),
  PYTHON("py", "python", new String[]{".py"}, "sonar.python.file.suffixes"),
  RPG("rpg", "rpg", new String[]{".rpg", ".rpgle"}, "sonar.rpg.file.suffixes"),
  RUBY("ruby", "ruby", new String[]{".rb"}, "sonar.ruby.file.suffixes"),
  SCALA("scala", "sonarscala", new String[]{".scala"}, "sonar.scala.file.suffixes"),
  SECRETS("secrets", "text", new String[0], "sonar.secrets.file.suffixes"),
  SWIFT("swift", "swift", new String[]{".swift"}, "sonar.swift.file.suffixes"),
  TSQL("tsql", "tsql", new String[]{".tsql"}, "sonar.tsql.file.suffixes"),
  TS("ts", Constants.JAVASCRIPT_PLUGIN_KEY, new String[]{".ts", ".tsx"},
    "sonar.typescript.file.suffixes"),
  JSP("jsp", "web", new String[]{".jsp", ".jspf", ".jspx"}, "sonar.jsp.file.suffixes"),
  VBNET("vbnet", "vbnet", new String[]{".vb"}, "sonar.vbnet.file.suffixes"),
  XML("xml", "xml", new String[]{".xml", ".xsd", ".xsl"}, "sonar.xml.file.suffixes"),
  YAML("yaml", Constants.JAVASCRIPT_PLUGIN_KEY, new String[]{".yml", "yaml"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  JSON("json", Constants.JAVASCRIPT_PLUGIN_KEY, new String[]{".json"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  GO("go", "go", new String[]{".go"}, "sonar.go.file.suffixes"),
  CLOUDFORMATION("cloudformation", "iac", new String[0], "sonar.cloudformation.file.suffixes"),
  DOCKER("docker", "iac", new String[0], "sonar.docker.file.suffixes"),
  KUBERNETES("kubernetes", "iac", new String[0], "sonar.kubernetes.file.suffixes"),
  TERRAFORM("terraform", "iac", new String[]{".tf"}, "sonar.terraform.file.suffixes"),
  AZURERESOURCEMANAGER("azureresourcemanager", "iac", new String[]{".bicep"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE);
  private final String sonarLanguageKey;

  /**
   * The Sonar Plugin declaring this language
   */
  private final String pluginKey;
  private final String[] defaultFileSuffixes;
  private final String fileSuffixesPropKey;

  private static final Map<String, SonarLanguage> mMap = Collections.unmodifiableMap(initializeMapping());

  private static Map<String, SonarLanguage> initializeMapping() {
    Map<String, SonarLanguage> mMap = new HashMap<>();
    for (SonarLanguage l : SonarLanguage.values()) {
      mMap.put(l.sonarLanguageKey, l);
    }
    return mMap;
  }

  SonarLanguage(String sonarLanguageKey, String pluginKey, String[] defaultFileSuffixes, String fileSuffixesPropKey) {
    this.sonarLanguageKey = sonarLanguageKey;
    this.pluginKey = pluginKey;
    this.defaultFileSuffixes = defaultFileSuffixes;
    this.fileSuffixesPropKey = fileSuffixesPropKey;
  }

  public String getSonarLanguageKey() {
    return sonarLanguageKey;
  }

  public String getPluginKey() {
    return pluginKey;
  }

  public String[] getDefaultFileSuffixes() {
    return defaultFileSuffixes;
  }

  public String getFileSuffixesPropKey() {
    return fileSuffixesPropKey;
  }

  public boolean shouldSyncInConnectedMode() {
    return !equals(SonarLanguage.IPYTHON);
  }

  public static Set<SonarLanguage> getLanguagesByPluginKey(String pluginKey) {
    return Stream.of(values()).filter(l -> l.getPluginKey().equals(pluginKey)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static Optional<SonarLanguage> getLanguageByLanguageKey(String languageKey) {
    var languages = Stream.of(values()).filter(l -> l.getSonarLanguageKey().equals(languageKey)).collect(Collectors.toCollection(ArrayList::new));
    return languages.isEmpty() ? Optional.empty() : Optional.of(languages.get(0));
  }

  public static boolean containsPlugin(String pluginKey) {
    return Stream.of(values()).anyMatch(l -> l.getPluginKey().equals(pluginKey));
  }

  public static Optional<SonarLanguage> forKey(String languageKey) {
    return Optional.ofNullable(mMap.get(languageKey));
  }

  private static class Constants {
    public static final String JAVASCRIPT_PLUGIN_KEY = "javascript";
    private static final String NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE = "<no public property provided for this language>";
  }

}
