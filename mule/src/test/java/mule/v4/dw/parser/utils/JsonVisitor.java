/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package mule.v4.dw.parser.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mule.v4.dataweave.parser.DataWeaveBaseVisitor;
import mule.v4.dataweave.parser.DataWeaveParser;

import java.util.List;

public class JsonVisitor extends DataWeaveBaseVisitor<JsonNode> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JsonNode visitScript(DataWeaveParser.ScriptContext ctx) {
        ObjectNode scriptNode = objectMapper.createObjectNode();
        scriptNode.put("type", "Script");

        if (ctx.header() != null) {
            scriptNode.set("header", visit(ctx.header()));
        }

        if (ctx.body() != null) {
            scriptNode.set("body", visit(ctx.body()));
        }

        return scriptNode;
    }

    @Override
    public JsonNode visitHeader(DataWeaveParser.HeaderContext ctx) {
        ObjectNode headerNode = objectMapper.createObjectNode();
        headerNode.put("type", "Header");
        ArrayNode directives = objectMapper.createArrayNode();

        for (DataWeaveParser.DirectiveContext directive : ctx.directive()) {
            directives.add(visit(directive));
        }

        headerNode.set("directives", directives);
        return headerNode;
    }

    @Override
    public JsonNode visitDirective(DataWeaveParser.DirectiveContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public JsonNode visitDwVersion(DataWeaveParser.DwVersionContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "Version");
        directiveNode.put("version", ctx.NUMBER().getText());
        return directiveNode;
    }

    @Override
    public JsonNode visitOutputDirective(DataWeaveParser.OutputDirectiveContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "Output");
        directiveNode.put("output", ctx.MEDIA_TYPE().getText());
        return directiveNode;
    }

    @Override
    public JsonNode visitImportDirective(DataWeaveParser.ImportDirectiveContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "Import");
        directiveNode.put("identifier", ctx.IDENTIFIER().getText());
        if (ctx.STRING() != null) {
            directiveNode.put("from", ctx.STRING().getText());
        }
        return directiveNode;
    }

    @Override
    public JsonNode visitNamespaceDirective(DataWeaveParser.NamespaceDirectiveContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "NameSpace");
        directiveNode.put("identifier", ctx.IDENTIFIER().getText());
        directiveNode.put("value", ctx.URL().getText());
        return directiveNode;
    }

    @Override
    public JsonNode visitVariableDeclaration(DataWeaveParser.VariableDeclarationContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "Variable");
        directiveNode.put("identifier", ctx.IDENTIFIER().getText());
        directiveNode.set("expression", visit(ctx.expression()));
        return directiveNode;
    }

    @Override
    public JsonNode visitFunctionDeclaration(DataWeaveParser.FunctionDeclarationContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "Function");
        directiveNode.put("identifier", ctx.IDENTIFIER().getText());
        if (ctx.functionParameters() != null) {
            directiveNode.set("args", visit(ctx.functionParameters()));
        }
        directiveNode.set("expression", visit(ctx.expression()));
        return directiveNode;
    }

    @Override
    public JsonNode visitTypeDeclaration(DataWeaveParser.TypeDeclarationContext ctx) {
        ObjectNode directiveNode = objectMapper.createObjectNode();
        directiveNode.put("type", "TypeDeclaration");
        directiveNode.put("identifier", ctx.IDENTIFIER().getText());
        directiveNode.set("typeExpression", visit(ctx.typeExpression()));
        return directiveNode;
    }

    @Override
    public JsonNode visitFunctionParameters(DataWeaveParser.FunctionParametersContext ctx) {
        ArrayNode argsArray = objectMapper.createArrayNode();
        for (var identifier : ctx.IDENTIFIER()) {
            argsArray.add(identifier.getText());
        }
        return argsArray;
    }

    @Override
    public JsonNode visitBody(DataWeaveParser.BodyContext ctx) {
        ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("type", "Body");
        bodyNode.set("expression", visit(ctx.expression()));
        return bodyNode;
    }

    // Expression visitors
    @Override
    public JsonNode visitConditionalExpressionWrapper(DataWeaveParser.ConditionalExpressionWrapperContext ctx) {
        return visit(ctx.operationExpression());
    }

    @Override
    public JsonNode visitWhenCondition(DataWeaveParser.WhenConditionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "WhenCondition");

        // Handle nested when-otherwise conditions with new grammar structure
        // Structure: operationExpression (WHEN conditionalExpression OTHERWISE)+ conditionalExpression
        List<DataWeaveParser.ConditionalExpressionContext> conditions = ctx.conditionalExpression();
        ArrayNode whenClauses = objectMapper.createArrayNode();

        // First result (before first when)
        ObjectNode firstClause = objectMapper.createObjectNode();
        firstClause.set("result", visit(ctx.operationExpression()));
        if (!conditions.isEmpty()) {
            firstClause.set("condition", visit(conditions.getFirst()));
        }
        whenClauses.add(firstClause);

        // Process remaining when-otherwise pairs
        for (int i = 1; i < conditions.size() - 1; i += 2) {
            ObjectNode whenClause = objectMapper.createObjectNode();
            whenClause.set("result", visit(conditions.get(i)));
            if (i + 1 < conditions.size()) {
                whenClause.set("condition", visit(conditions.get(i + 1)));
            }
            whenClauses.add(whenClause);
        }

        objectNode.set("whenClauses", whenClauses);

        // Final else clause (last expression)
        if (!conditions.isEmpty()) {
            objectNode.set("otherwise", visit(conditions.getLast()));
        }

        return objectNode;
    }

    @Override
    public JsonNode visitUnlessCondition(DataWeaveParser.UnlessConditionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "UnlessCondition");
        objectNode.set("result", visit(ctx.operationExpression()));
        objectNode.set("condition", visit(ctx.conditionalExpression(0)));
        objectNode.set("otherwise", visit(ctx.conditionalExpression(1)));
        return objectNode;
    }

    // Operation expression visitors
    @Override
    public JsonNode visitOperationExpressionWrapper(DataWeaveParser.OperationExpressionWrapperContext ctx) {
        return visit(ctx.logicalOrExpression());
    }

    @Override
    public JsonNode visitFilterExpression(DataWeaveParser.FilterExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "FilterExpression");
        objectNode.set("expression", visit(ctx.operationExpression()));
        objectNode.set("lambda", visit(ctx.implicitLambdaExpression()));
        return objectNode;
    }

    @Override
    public JsonNode visitMapExpression(DataWeaveParser.MapExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "MapExpression");
        objectNode.set("expression", visit(ctx.operationExpression()));
        objectNode.set("lambda", visit(ctx.implicitLambdaExpression()));
        return objectNode;
    }

    @Override
    public JsonNode visitGroupByExpression(DataWeaveParser.GroupByExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "GroupByExpression");
        objectNode.set("expression", visit(ctx.operationExpression()));
        objectNode.set("lambda", visit(ctx.implicitLambdaExpression()));
        return objectNode;
    }

    @Override
    public JsonNode visitReplaceExpression(DataWeaveParser.ReplaceExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ReplaceExpression");
        objectNode.set("expression", visit(ctx.operationExpression()));
        objectNode.put("regex", ctx.REGEX().getText());
        objectNode.set("replacement", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitConcatExpression(DataWeaveParser.ConcatExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ConcatExpression");
        objectNode.set("left", visit(ctx.operationExpression()));
        objectNode.set("right", visit(ctx.logicalOrExpression()));
        return objectNode;
    }

    // Logical expression visitors
    @Override
    public JsonNode visitLogicalOrExpression(DataWeaveParser.LogicalOrExpressionContext ctx) {
        if (ctx.logicalAndExpression().size() == 1) {
            return visit(ctx.logicalAndExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "LogicalOrExpression");
        ArrayNode expressions = objectMapper.createArrayNode();

        for (DataWeaveParser.LogicalAndExpressionContext expr : ctx.logicalAndExpression()) {
            expressions.add(visit(expr));
        }

        objectNode.set("expressions", expressions);
        return objectNode;
    }

    @Override
    public JsonNode visitLogicalAndExpression(DataWeaveParser.LogicalAndExpressionContext ctx) {
        if (ctx.equalityExpression().size() == 1) {
            return visit(ctx.equalityExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "LogicalAndExpression");
        ArrayNode expressions = objectMapper.createArrayNode();

        for (DataWeaveParser.EqualityExpressionContext expr : ctx.equalityExpression()) {
            expressions.add(visit(expr));
        }

        objectNode.set("expressions", expressions);
        return objectNode;
    }

    @Override
    public JsonNode visitEqualityExpression(DataWeaveParser.EqualityExpressionContext ctx) {
        if (ctx.relationalExpression().size() == 1) {
            return visit(ctx.relationalExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "EqualityExpression");
        objectNode.set("left", visit(ctx.relationalExpression(0)));
        objectNode.put("operator", ctx.OPERATOR_EQUALITY(0).getText());
        objectNode.set("right", visit(ctx.relationalExpression(1)));
        return objectNode;
    }

    @Override
    public JsonNode visitRelationalComparison(DataWeaveParser.RelationalComparisonContext ctx) {
        if (ctx.additiveExpression().size() == 1) {
            return visit(ctx.additiveExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "RelationalExpression");
        objectNode.set("left", visit(ctx.additiveExpression(0)));
        objectNode.put("operator", ctx.OPERATOR_RELATIONAL(0).getText());
        objectNode.set("right", visit(ctx.additiveExpression(1)));
        return objectNode;
    }

    @Override
    public JsonNode visitIsExpression(DataWeaveParser.IsExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "IsExpression");
        objectNode.set("expression", visit(ctx.additiveExpression()));
        objectNode.set("typeExpression", visit(ctx.typeExpression()));
        return objectNode;
    }

    @Override
    public JsonNode visitAdditiveExpression(DataWeaveParser.AdditiveExpressionContext ctx) {
        if (ctx.multiplicativeExpression().size() == 1) {
            return visit(ctx.multiplicativeExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "AdditiveExpression");
        objectNode.set("left", visit(ctx.multiplicativeExpression(0)));
        objectNode.put("operator", ctx.OPERATOR_ADDITIVE(0).getText());
        objectNode.set("right", visit(ctx.multiplicativeExpression(1)));
        return objectNode;
    }

    @Override
    public JsonNode visitMultiplicativeExpression(DataWeaveParser.MultiplicativeExpressionContext ctx) {
        if (ctx.typeCoercionExpression().size() == 1) {
            return visit(ctx.typeCoercionExpression(0));
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "MultiplicativeExpression");
        objectNode.set("left", visit(ctx.typeCoercionExpression(0)));
        objectNode.put("operator", ctx.OPERATOR_MULTIPLICATIVE(0).getText());
        objectNode.set("right", visit(ctx.typeCoercionExpression(1)));
        return objectNode;
    }

    @Override
    public JsonNode visitTypeCoercionExpression(DataWeaveParser.TypeCoercionExpressionContext ctx) {
        if (ctx.typeExpression() == null) {
            return visit(ctx.unaryExpression());
        }

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "TypeCoercionExpression");
        objectNode.set("expression", visit(ctx.unaryExpression()));
        objectNode.set("typeExpression", visit(ctx.typeExpression()));

        if (ctx.formatOption() != null) {
            objectNode.set("formatOption", visit(ctx.formatOption()));
        }

        return objectNode;
    }

    // Unary expression visitors
    @Override
    public JsonNode visitPrimaryExpressionWrapper(DataWeaveParser.PrimaryExpressionWrapperContext ctx) {
        return visit(ctx.primaryExpression());
    }

    @Override
    public JsonNode visitSizeOfExpression(DataWeaveParser.SizeOfExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "SizeOf");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitSizeOfExpressionWithParentheses(DataWeaveParser.SizeOfExpressionWithParenthesesContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "SizeOf");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitUpperExpression(DataWeaveParser.UpperExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "UpperExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitUpperExpressionWithParentheses(DataWeaveParser.UpperExpressionWithParenthesesContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "UpperExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitLowerExpression(DataWeaveParser.LowerExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "LowerExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitLowerExpressionWithParentheses(DataWeaveParser.LowerExpressionWithParenthesesContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "LowerExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitNotExpression(DataWeaveParser.NotExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "NotExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitNegativeExpression(DataWeaveParser.NegativeExpressionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "NegativeExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    // Primary expression visitors
    @Override
    public JsonNode visitLambdaExpression(DataWeaveParser.LambdaExpressionContext ctx) {
        return visit(ctx.inlineLambda());
    }

    @Override
    public JsonNode visitGroupedExpression(DataWeaveParser.GroupedExpressionContext ctx) {
        return visit(ctx.grouped());
    }

    @Override
    public JsonNode visitLiteralExpression(DataWeaveParser.LiteralExpressionContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public JsonNode visitFunctionCallExpression(DataWeaveParser.FunctionCallExpressionContext ctx) {
        return visit(ctx.functionCall());
    }

    @Override
    public JsonNode visitArrayExpression(DataWeaveParser.ArrayExpressionContext ctx) {
        return visit(ctx.array());
    }

    @Override
    public JsonNode visitObjectExpression(DataWeaveParser.ObjectExpressionContext ctx) {
        return visit(ctx.object());
    }

    @Override
    public JsonNode visitBuiltInFunctionExpression(DataWeaveParser.BuiltInFunctionExpressionContext ctx) {
        return visit(ctx.builtInFunction());
    }

    @Override
    public JsonNode visitIdentifierExpression(DataWeaveParser.IdentifierExpressionContext ctx) {
        ObjectNode identifierNode = objectMapper.createObjectNode();
        identifierNode.put("type", "Identifier");
        identifierNode.put("name", ctx.IDENTIFIER().getText());
        return identifierNode;
    }

    @Override
    public JsonNode visitValueIdentifierExpression(DataWeaveParser.ValueIdentifierExpressionContext ctx) {
        ObjectNode identifierNode = objectMapper.createObjectNode();
        identifierNode.put("type", "ValueIdentifier");
        identifierNode.put("name", ctx.VALUE_IDENTIFIER().getText());
        return identifierNode;
    }

    @Override
    public JsonNode visitIndexIdentifierExpression(DataWeaveParser.IndexIdentifierExpressionContext ctx) {
        ObjectNode identifierNode = objectMapper.createObjectNode();
        identifierNode.put("type", "IndexIdentifier");
        identifierNode.put("name", ctx.INDEX_IDENTIFIER().getText());
        return identifierNode;
    }

    @Override
    public JsonNode visitPayloadExpression(DataWeaveParser.PayloadExpressionContext ctx) {
        ObjectNode identifierNode = objectMapper.createObjectNode();
        identifierNode.put("type", "Payload");
        identifierNode.put("name", ctx.PAYLOAD().getText());
        return identifierNode;
    }

    @Override
    public JsonNode visitSelectorExpressionWrapper(DataWeaveParser.SelectorExpressionWrapperContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "SelectorExpression");
        objectNode.set("primary", visit(ctx.primaryExpression()));
        objectNode.set("selector", visit(ctx.selectorExpression()));
        return objectNode;
    }

    // Built-in functions
    @Override
    public JsonNode visitNowFunction(DataWeaveParser.NowFunctionContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "NowFunction");
        objectNode.put("name", "now");
        return objectNode;
    }

    // Grouped expressions
    @Override
    public JsonNode visitGrouped(DataWeaveParser.GroupedContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "GroupedExpression");
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    // Selector expressions
    @Override
    public JsonNode visitSingleValueSelector(DataWeaveParser.SingleValueSelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "SingleValueSelector");
        objectNode.put("identifier", ctx.IDENTIFIER().getText());
        return objectNode;
    }

    @Override
    public JsonNode visitMultiValueSelector(DataWeaveParser.MultiValueSelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "MultiValueSelector");
        objectNode.put("identifier", ctx.IDENTIFIER().getText());
        return objectNode;
    }

    @Override
    public JsonNode visitDescendantsSelector(DataWeaveParser.DescendantsSelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "DescendantsSelector");
        objectNode.put("identifier", ctx.IDENTIFIER().getText());
        return objectNode;
    }

    @Override
    public JsonNode visitIndexedSelector(DataWeaveParser.IndexedSelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "IndexedSelector");
        objectNode.set("index", visit(ctx.expression()));
        return objectNode;
    }

    @Override
    public JsonNode visitAttributeSelector(DataWeaveParser.AttributeSelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "AttributeSelector");
        objectNode.put("identifier", ctx.IDENTIFIER().getText());
        return objectNode;
    }

    @Override
    public JsonNode visitExistenceQuerySelector(DataWeaveParser.ExistenceQuerySelectorContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ExistenceQuerySelector");
        return objectNode;
    }

    // Literals
    @Override
    public JsonNode visitLiteral(DataWeaveParser.LiteralContext ctx) {
        ObjectNode literalNode = objectMapper.createObjectNode();
        literalNode.put("type", "Literal");
        literalNode.put("value", ctx.getText());
        return literalNode;
    }

    // Arrays
    @Override
    public JsonNode visitArray(DataWeaveParser.ArrayContext ctx) {
        ObjectNode arrayNode = objectMapper.createObjectNode();
        arrayNode.put("type", "Array");
        ArrayNode elements = objectMapper.createArrayNode();

        for (DataWeaveParser.ExpressionContext expr : ctx.expression()) {
            elements.add(visit(expr));
        }

        arrayNode.set("elements", elements);
        return arrayNode;
    }

    // Objects
    @Override
    public JsonNode visitMultiFieldObject(DataWeaveParser.MultiFieldObjectContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Object");
        ArrayNode fields = objectMapper.createArrayNode();

        for (DataWeaveParser.ObjectFieldContext field : ctx.objectField()) {
            fields.add(visit(field));
        }

        objectNode.set("fields", fields);
        return objectNode;
    }

    @Override
    public JsonNode visitSingleFieldObject(DataWeaveParser.SingleFieldObjectContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Object");
        ArrayNode fields = objectMapper.createArrayNode();
        fields.add(visit(ctx.objectField()));
        objectNode.set("fields", fields);
        return objectNode;
    }

    @Override
    public JsonNode visitUnquotedKeyField(DataWeaveParser.UnquotedKeyFieldContext ctx) {
        ObjectNode fieldNode = objectMapper.createObjectNode();
        fieldNode.put("type", "UnquotedKeyField");
        fieldNode.put("key", ctx.IDENTIFIER().getText());
        fieldNode.set("value", visit(ctx.expression()));
        return fieldNode;
    }

    @Override
    public JsonNode visitQuotedKeyField(DataWeaveParser.QuotedKeyFieldContext ctx) {
        ObjectNode fieldNode = objectMapper.createObjectNode();
        fieldNode.put("type", "QuotedKeyField");
        fieldNode.put("key", ctx.STRING().getText());
        fieldNode.set("value", visit(ctx.expression()));
        return fieldNode;
    }

    @Override
    public JsonNode visitDynamicKeyField(DataWeaveParser.DynamicKeyFieldContext ctx) {
        ObjectNode fieldNode = objectMapper.createObjectNode();
        fieldNode.put("type", "DynamicKeyField");
        fieldNode.set("key", visit(ctx.expression(0)));
        fieldNode.set("value", visit(ctx.expression(1)));
        return fieldNode;
    }

    // Function calls
    @Override
    public JsonNode visitFunctionCall(DataWeaveParser.FunctionCallContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "FunctionCall");
        objectNode.put("name", ctx.IDENTIFIER().getText());
        ArrayNode args = objectMapper.createArrayNode();

        for (DataWeaveParser.ExpressionContext expr : ctx.expression()) {
            args.add(visit(expr));
        }

        objectNode.set("args", args);
        return objectNode;
    }

    // Lambda expressions
    @Override
    public JsonNode visitImplicitLambdaExpression(DataWeaveParser.ImplicitLambdaExpressionContext ctx) {
        if (ctx.inlineLambda() != null) {
            return visit(ctx.inlineLambda());
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        // Create a default node for implicit lambda when no specific content is found
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ImplicitLambda");
        return objectNode;
    }

    @Override
    public JsonNode visitInlineLambda(DataWeaveParser.InlineLambdaContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "InlineLambda");
        objectNode.set("parameters", visit(ctx.functionParameters()));
        objectNode.set("expression", visit(ctx.expression()));
        return objectNode;
    }

    // Type expressions
    @Override
    public JsonNode visitNamedType(DataWeaveParser.NamedTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "NamedType");
        objectNode.put("name", ctx.IDENTIFIER().getText());
        return objectNode;
    }

    @Override
    public JsonNode visitStringType(DataWeaveParser.StringTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "StringType");
        return objectNode;
    }

    @Override
    public JsonNode visitNumberType(DataWeaveParser.NumberTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "NumberType");
        return objectNode;
    }

    @Override
    public JsonNode visitBooleanType(DataWeaveParser.BooleanTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "BooleanType");
        return objectNode;
    }

    @Override
    public JsonNode visitDateTimeType(DataWeaveParser.DateTimeTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "DateTimeType");
        return objectNode;
    }

    @Override
    public JsonNode visitLocalDateTimeType(DataWeaveParser.LocalDateTimeTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "LocalDateTimeType");
        return objectNode;
    }

    @Override
    public JsonNode visitDateType(DataWeaveParser.DateTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "DateType");
        return objectNode;
    }

    @Override
    public JsonNode visitTimeType(DataWeaveParser.TimeTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "TimeType");
        return objectNode;
    }

    @Override
    public JsonNode visitArrayType(DataWeaveParser.ArrayTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ArrayType");
        objectNode.set("elementType", visit(ctx.typeExpression()));
        return objectNode;
    }

    @Override
    public JsonNode visitObjectType(DataWeaveParser.ObjectTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "ObjectType");
        return objectNode;
    }

    @Override
    public JsonNode visitAnyType(DataWeaveParser.AnyTypeContext ctx) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "AnyType");
        return objectNode;
    }

}
