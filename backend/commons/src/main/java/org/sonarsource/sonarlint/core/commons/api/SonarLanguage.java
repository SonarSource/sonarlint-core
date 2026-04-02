/*
 * SonarLint Core - Commons
 * Copyright (C) SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;

public enum SonarLanguage {

  ABAP("abap", SonarPlugin.ABAP, "Abap", new String[]{".abap", ".ab4", ".flow", ".asprog"}, "sonar.abap.file.suffixes"),
  APEX("apex", SonarPlugin.APEX, "Apex", new String[]{".cls", ".trigger"}, "sonar.apex.file.suffixes"),
  C("c", SonarPlugin.C_FAMILY, "C", new String[]{".c", ".h"}, "sonar.c.file.suffixes"),
  CPP("cpp", SonarPlugin.C_FAMILY, "C++", new String[]{".cc", ".cpp", ".cxx", ".c++", ".hh", ".hpp", ".hxx", ".h++", ".ipp"}, "sonar.cpp.file.suffixes"),
  CS("cs", SonarPlugin.CS_OSS, "C#", new String[]{".cs", ".razor"}, "sonar.cs.file.suffixes"),
  CSS("css", SonarPlugin.JS, "CSS", new String[]{".css", ".less", ".scss"}, "sonar.css.file.suffixes"),
  OBJC("objc", SonarPlugin.C_FAMILY, "Objective-C", new String[]{".m"}, "sonar.objc.file.suffixes"),
  COBOL("cobol", SonarPlugin.COBOL, "COBOL", new String[0], "sonar.cobol.file.suffixes"),
  HTML("web", SonarPlugin.WEB, "HTML", new String[]{".html", ".xhtml", ".cshtml", ".vbhtml", ".aspx", ".ascx", ".rhtml", ".erb", ".shtm", ".shtml"}, "sonar.html.file.suffixes"),
  IPYTHON("ipynb", SonarPlugin.PYTHON, "IPython Notebook", new String[]{".ipynb"}, "sonar.ipython.file.suffixes"),
  JAVA("java", SonarPlugin.JAVA, "Java", new String[]{".java", ".jav"}, "sonar.java.file.suffixes"),
  JCL("jcl", SonarPlugin.JCL, "JCL", new String[]{".jcl"}, "sonar.jcl.file.suffixes"),
  JS("js", SonarPlugin.JS, "JavaScript", new String[]{".js", ".jsx", ".vue"}, "sonar.javascript.file.suffixes"),
  KOTLIN("kotlin", SonarPlugin.KOTLIN, "Kotlin", new String[]{".kt", ".kts"}, "sonar.kotlin.file.suffixes"),
  PHP("php", SonarPlugin.PHP, "PHP", new String[]{"php", "php3", "php4", "php5", "phtml", "inc"}, "sonar.php.file.suffixes"),
  PLI("pli", SonarPlugin.PLI, "PL/I", new String[]{".pli"}, "sonar.pli.file.suffixes"),
  PLSQL("plsql", SonarPlugin.PLSQL, "PL/SQL", new String[]{".sql", ".pks", ".pkb"}, "sonar.plsql.file.suffixes"),
  PYTHON("py", SonarPlugin.PYTHON, "Python", new String[]{".py"}, "sonar.python.file.suffixes"),
  RPG("rpg", SonarPlugin.RPG, "RPG", new String[]{".rpg", ".rpgle"}, "sonar.rpg.file.suffixes"),
  RUBY("ruby", SonarPlugin.RUBY, "Ruby", new String[]{".rb"}, "sonar.ruby.file.suffixes"),
  SCALA("scala", SonarPlugin.SCALA, "Scala", new String[]{".scala"}, "sonar.scala.file.suffixes"),
  SECRETS("secrets", SonarPlugin.TEXT, "Secrets", new String[0], "sonar.secrets.file.suffixes"),
  TEXT("text", SonarPlugin.TEXT, "Text", new String[0], "sonar.text.file.suffixes"),
  SWIFT("swift", SonarPlugin.SWIFT, "Swift", new String[]{".swift"}, "sonar.swift.file.suffixes"),
  TSQL("tsql", SonarPlugin.TSQL, "T-SQL", new String[]{".tsql"}, "sonar.tsql.file.suffixes"),
  TS("ts", SonarPlugin.JS, "TypeScript", new String[]{".ts", ".tsx"},
    "sonar.typescript.file.suffixes"),
  JSP("jsp", SonarPlugin.WEB, "JSP", new String[]{".jsp", ".jspf", ".jspx"}, "sonar.jsp.file.suffixes"),
  VBNET("vbnet", SonarPlugin.VBNET_OSS, "VB.NET", new String[]{".vb"}, "sonar.vbnet.file.suffixes"),
  XML("xml", SonarPlugin.XML, "XML", new String[]{".xml", ".xsd", ".xsl"}, "sonar.xml.file.suffixes"),
  YAML("yaml", SonarPlugin.JS, "YAML", new String[]{".yml", "yaml"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  JSON("json", SonarPlugin.JS, "JSON", new String[]{".json"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  GO("go", SonarPlugin.GO, "Go", new String[]{".go"}, "sonar.go.file.suffixes"),
  CLOUDFORMATION("cloudformation", SonarPlugin.IAC, "CloudFormation", new String[0], Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  DOCKER("docker", SonarPlugin.IAC, "Docker", new String[0], Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  KUBERNETES("kubernetes", SonarPlugin.IAC, "Kubernetes", new String[0], Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  TERRAFORM("terraform", SonarPlugin.IAC, "Terraform", new String[]{".tf"}, "sonar.terraform.file.suffixes"),
  AZURERESOURCEMANAGER("azureresourcemanager", SonarPlugin.IAC, "Azure Resource Manager", new String[]{".bicep"}, Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  ANSIBLE("ansible", SonarPlugin.IAC_ENTERPRISE, "Ansible", new String[0], Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE),
  GITHUBACTIONS("githubactions", SonarPlugin.IAC_ENTERPRISE, "GitHub Actions", new String[0], Constants.NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE);

  private final String sonarLanguageKey;

  /**
   * The Sonar Plugin declaring this language
   */
  private final SonarPlugin plugin;
  private final String name;
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

  SonarLanguage(String sonarLanguageKey, SonarPlugin plugin, String name, String[] defaultFileSuffixes, String fileSuffixesPropKey) {
    this.sonarLanguageKey = sonarLanguageKey;
    this.plugin = plugin;
    this.name = name;
    this.defaultFileSuffixes = defaultFileSuffixes;
    this.fileSuffixesPropKey = fileSuffixesPropKey;
  }

  public String getSonarLanguageKey() {
    return sonarLanguageKey;
  }

  public SonarPlugin getPlugin() {
    return plugin;
  }

  public String getName() {
    return name;
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
    return Stream.of(values()).filter(l -> l.getPlugin().getKey().equals(pluginKey)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static Optional<SonarLanguage> getLanguageByLanguageKey(String languageKey) {
    var languages = Stream.of(values()).filter(l -> l.getSonarLanguageKey().equals(languageKey)).collect(Collectors.toCollection(ArrayList::new));
    return languages.isEmpty() ? Optional.empty() : Optional.of(languages.get(0));
  }

  public static Optional<SonarLanguage> forKey(String languageKey) {
    return Optional.ofNullable(mMap.get(languageKey));
  }

  public static class Constants {
    private static final String NO_PUBLIC_PROPERTY_PROVIDED_FOR_THIS_LANGUAGE = "<no public property provided for this language>";
  }

}
