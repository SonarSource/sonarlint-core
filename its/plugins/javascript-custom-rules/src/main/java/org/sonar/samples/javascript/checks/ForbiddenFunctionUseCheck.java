/*
 * JavaScript Custom Rules Plugin
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonar.samples.javascript.checks;

import java.util.HashSet;
import java.util.Set;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.expression.CallExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.IdentifierTree;
import org.sonar.plugins.javascript.api.visitors.DoubleDispatchVisitorCheck;

import static java.util.Arrays.asList;

/**
 * Example of implementation of a check by extending {@link DoubleDispatchVisitorCheck}.
 * DoubleDispatchVisitorCheck provides methods to visit nodes of the Abstract Syntax Tree
 * that represents the source code.
 * <p>
 * Those methods can be overridden to process information
 * related to node and issues can be created via {@link DoubleDispatchVisitorCheck#addIssue} methods}.
 *
 * <p>
 * Description can either be given via {@link Rule} annotation or through HTML name <ruleKey>.html loaded in
 * {@link org.sonar.samples.javascript.JavaScriptCustomRulesDefinition}
 * </p>
 */
@Rule(
  key = "S1",
  priority = Priority.MAJOR,
  name = "Forbidden function should not be used.",
  tags = {"convention"}
// description = "<p>The following functions should not be used:</p> <ul><li>foo</li> <li>bar</li></ul>",
)
public class ForbiddenFunctionUseCheck extends DoubleDispatchVisitorCheck {

  private static final Set<String> FORBIDDEN_FUNCTIONS = new HashSet<>(asList("foo", "bar"));

  /**
   * Overriding method visiting the call expression to create an issue
   * each time a call to "foo()" or "bar()" is done.
   */
  @Override
  public void visitCallExpression(CallExpressionTree tree) {
    ExpressionTree callee = tree.callee();

    if (callee.is(Kind.IDENTIFIER_REFERENCE) && FORBIDDEN_FUNCTIONS.contains(((IdentifierTree) callee).name())) {
      addIssue(tree, "Remove the usage of this forbidden function.");
    }

    // super method must be called in order to visit what is under the function node in the syntax tree
    super.visitCallExpression(tree);
  }

}
