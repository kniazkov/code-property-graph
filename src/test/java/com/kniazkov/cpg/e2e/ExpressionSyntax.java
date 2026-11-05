/* Code Property Graph: inspect written syntax through semantic-only conversions. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.model.*;

final class ExpressionSyntax {
    private ExpressionSyntax() { }
    static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()) expression = conversion.operand();
        return expression;
    }
}
