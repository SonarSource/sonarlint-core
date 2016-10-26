/*
 * Copyright (C) 2009-2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.sonar.samples.javascript.checks;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.expression.CallExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.IdentifierTree;
import org.sonar.plugins.javascript.api.visitors.DoubleDispatchVisitorCheck;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

/**
 * Example of implementation of a check by extending {@link DoubleDispatchVisitorCheck}.
 * DoubleDispatchVisitorCheck provides methods to visit nodes of the Abstract Syntax Tree
 * that represents the source code.
 * <p>
 * Those methods can be overridden to process information
 * related to node and issues can be created via {@link DoubleDispatchVisitorCheck#addIssue} methods}.
 */
@Rule(
  key = "S1",
  priority = Priority.MAJOR,
  name = "Forbidden function should not be used.",
  tags = {"convention"}
// Description can either be given in this annotation or through HTML name <ruleKey>.html located in package src/resources/org/sonar/l10n/javascript/rules/<repositoryKey>
// description = "<p>The following functions should not be used:</p> <ul><li>foo</li> <li>bar</li></ul>",
  )
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.DATA_RELIABILITY)
@SqaleConstantRemediation("5min")
public class ForbiddenFunctionUseCheck extends DoubleDispatchVisitorCheck {

  private static final Set<String> FORBIDDEN_FUNCTIONS = ImmutableSet.of("foo", "bar");

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
