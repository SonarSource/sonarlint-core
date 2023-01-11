/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.backend.rules;

public class OthersSectionHtmlContent {

  private OthersSectionHtmlContent() {}

  public static final String HTML_CONTENT =
    "<h2>How can I fix it in another component or framework?</h2>\n" +
    "<p>Although the main framework or component you use in your project is not listed, " +
      "you may find helpful content in the instructions we provide.</p>\n" +
    "<p>Caution: The libraries mentioned in these instructions may not be appropriate for your code.</p>\n" +
    "<p>\n" +
    "<ul>\n" +
    "<li>Do use libraries that are compatible with the frameworks you are using.</li>\n" +
    "<li>Don't blindly copy and paste the fix ups into your code.</li>\n" +
    "</ul>\n" +
    "<h2>Help us improve</h2>\n" +
    "<p>Let us know if the instructions we provide do not work for you. " +
      "Tell us which framework you use and why our solution does not work by submitting an idea on the SonarLint product-board.</p>\n" +
    "<a href=\"https://portal.productboard.com/sonarsource/4-sonarlint/submit-idea\">Submit an idea</a>\n" +
    "<p>We will do our best to provide you with more relevant instructions in the future.</p>";

}
