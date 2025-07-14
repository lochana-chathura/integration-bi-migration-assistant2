grammar DataWeave;

// Lexer rules for DataWeave 2.0
VAR: 'var';
FUNCTION: 'fun';
IMPORT: 'import';
NAMESPACE: 'ns';
OUTPUT: 'output';
DW: '%dw';
TYPE: 'type';
ASSIGN: '=';
ARROW: '->';
BOOLEAN: 'true' | 'false';

// Keywords
WHEN: 'when';
OTHERWISE: 'otherwise';
UNLESS: 'unless';
AND: 'and';
OR: 'or';
NOT: 'not';
IF: 'if';
ELSE: 'else';
MAP: 'map';
FILTER: 'filter';
GROUP_BY: 'groupBy';
SIZE_OF: 'sizeOf';
UPPER: 'upper';
LOWER: 'lower';
REPLACE: 'replace';
WITH: 'with';
FROM: 'from';
AS: 'as';
IS: 'is';

// Built-in identifiers
NOW: 'now';
PAYLOAD: 'payload';

// Operators with names
OPERATOR_EQUALITY: '==' | '!=' | '~=';
OPERATOR_RELATIONAL:'>' | '<' | '>=' | '<=';
OPERATOR_MULTIPLICATIVE: '*' | '/';
OPERATOR_ADDITIVE: '+' | '>>' | '-' ;
OPERATOR_RANGE: '..';
CONCAT: '++';

IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;
INDEX_IDENTIFIER: '$$';
VALUE_IDENTIFIER: '$';
URL: [a-zA-Z]+ '://' [a-zA-Z0-9./_-]+;
MEDIA_TYPE: [a-z]+ '/' [a-z0-9.+-]+;
NUMBER: [0-9]+('.'[0-9]+)?;
STRING: '"' .*? '"' | '\'' .*? '\'';
DATE: '|' .*? '|';
REGEX: '/' .*? '/';
DOT: '.';
COLON: ':';
COMMA: ',';
LCURLY: '{';
RCURLY: '}';
LSQUARE: '[';
RSQUARE: ']';
LPAREN: '(';
RPAREN: ')';
SEPARATOR: '---';
WS: [ \t]+ -> skip;
NEWLINE: [\r\n]+ -> skip;
COMMENT: '//' ~[\r\n]* -> skip;

// Selectors
STAR: '*';
AT: '@';
QUESTION: '?';

// Parser rules
script: header? SEPARATOR body? NEWLINE* EOF;

header: (directive (NEWLINE | WS)*)+;

directive
    : dwVersion
    | outputDirective
    | importDirective
    | namespaceDirective
    | variableDeclaration
    | functionDeclaration
    | typeDeclaration;

dwVersion: DW NUMBER;

outputDirective: OUTPUT MEDIA_TYPE;

importDirective: IMPORT IDENTIFIER (FROM STRING)?;

namespaceDirective: NAMESPACE IDENTIFIER URL;

variableDeclaration: VAR IDENTIFIER ASSIGN expression;

functionDeclaration: FUNCTION IDENTIFIER LPAREN functionParameters? RPAREN expression;

typeDeclaration: TYPE IDENTIFIER ASSIGN typeExpression;

// Body of the parser
body: expression NEWLINE*;

// Expression Rules - Fixed hierarchy for proper precedence
expression
    : conditionalExpression
    ;

// Level 10: Conditional Expressions (WHEN OTHERWISE, UNLESS OTHERWISE) - Top level
conditionalExpression
    : operationExpression (WHEN conditionalExpression OTHERWISE)+ conditionalExpression    # whenCondition
    | operationExpression UNLESS conditionalExpression OTHERWISE conditionalExpression     # unlessCondition
    | operationExpression                                                                  # conditionalExpressionWrapper
    ;

// Level 9: Operations (Map, Filter, GroupBy, Replace, Concat)
operationExpression
    : operationExpression FILTER implicitLambdaExpression   # filterExpression
    | operationExpression MAP implicitLambdaExpression      # mapExpression
    | operationExpression GROUP_BY implicitLambdaExpression # groupByExpression
    | operationExpression REPLACE REGEX WITH expression     # replaceExpression
    | operationExpression CONCAT logicalOrExpression        # concatExpression
    | logicalOrExpression                                   # operationExpressionWrapper
    ;

// Implicit Lambda Expressions
implicitLambdaExpression
    : inlineLambda
    | expression
    ;

// Lambda functions
inlineLambda: '(' functionParameters ')' ARROW expression;

functionParameters: IDENTIFIER (COMMA IDENTIFIER)*;


// Level 8: Logical OR
logicalOrExpression
    : logicalAndExpression (OR logicalAndExpression)*
    ;

// Level 7: Logical AND
logicalAndExpression
    : equalityExpression (AND equalityExpression)*
    ;

// Level 6: Equality Operators (==, !=, ~=)
equalityExpression
    : relationalExpression (OPERATOR_EQUALITY relationalExpression)*
    ;

// Level 5: Relational and Type Comparison (>, <, >=, <=, is)
relationalExpression
    : additiveExpression (OPERATOR_RELATIONAL additiveExpression)*     # relationalComparison
    | additiveExpression IS typeExpression                             # isExpression
    ;

// Level 4: Additive Operators (+, -, >>)
additiveExpression
    : multiplicativeExpression (OPERATOR_ADDITIVE multiplicativeExpression)*
    ;

// Level 3: Multiplicative Operators (*, /)
multiplicativeExpression
    : typeCoercionExpression (OPERATOR_MULTIPLICATIVE typeCoercionExpression)*
    ;

// Level 2: Type Coercion (`as`)
typeCoercionExpression
    : unaryExpression (AS typeExpression formatOption?)?
    ;

// Formatting options within `{}`
formatOption
    : '{' IDENTIFIER ':' STRING '}'
    ;

// Level 1: Unary Operators (-, not)
unaryExpression
    : SIZE_OF expression                   # sizeOfExpression
    | SIZE_OF '(' expression ')'           # sizeOfExpressionWithParentheses
    | UPPER expression                     # upperExpression
    | UPPER '(' expression ')'             # upperExpressionWithParentheses
    | LOWER expression                     # lowerExpression
    | LOWER '(' expression ')'             # lowerExpressionWithParentheses
    | NOT expression                       # notExpression
    | '-' expression                       # negativeExpression
    | primaryExpression                    # primaryExpressionWrapper
    ;

// **Primary Expressions (Highest Precedence)**
primaryExpression
    : inlineLambda                           # lambdaExpression
    | grouped                                # groupedExpression
    | literal                                # literalExpression
    | functionCall                           # functionCallExpression
    | array                                  # arrayExpression
    | object                                 # objectExpression
    | builtInFunction                        # builtInFunctionExpression
    | IDENTIFIER                             # identifierExpression
    | VALUE_IDENTIFIER                       # valueIdentifierExpression
    | INDEX_IDENTIFIER                       # indexIdentifierExpression
    | PAYLOAD                                # payloadExpression
    | primaryExpression selectorExpression   # selectorExpressionWrapper
    ;

// Built-in functions
builtInFunction
    : NOW '(' ')'                            # nowFunction
    ;

// Grouped expressions
grouped: '(' expression ')';

selectorExpression
    : DOT IDENTIFIER                         # singleValueSelector
    | DOT STAR IDENTIFIER                    # multiValueSelector
    | OPERATOR_RANGE IDENTIFIER              # descendantsSelector
    | LSQUARE expression RSQUARE             # indexedSelector
    | DOT AT IDENTIFIER                      # attributeSelector
    | QUESTION                               # existenceQuerySelector
    ;

// Literals
literal
    : STRING
    | NUMBER
    | BOOLEAN
    | DATE
    | REGEX;

// Arrays
array: LSQUARE (expression (COMMA expression)*)? RSQUARE;

// Objects
object
    : LCURLY objectField (COMMA objectField)* RCURLY  # multiFieldObject
    | LCURLY objectField RCURLY                       # singleFieldObject
    ;

objectField
    : IDENTIFIER COLON expression              # unquotedKeyField
    | STRING COLON expression                  # quotedKeyField
    | '(' expression ')' COLON expression      # dynamicKeyField
    ;

// Function calls
functionCall: IDENTIFIER '(' (expression (COMMA expression)*)? ')';

// Type expressions for DataWeave 2.0
typeExpression
    : IDENTIFIER                              # namedType
    | 'String'                               # stringType
    | 'Number'                               # numberType
    | 'Boolean'                              # booleanType
    | 'DateTime'                             # dateTimeType
    | 'LocalDateTime'                        # localDateTimeType
    | 'Date'                                 # dateType
    | 'Time'                                 # timeType
    | 'Array' '<' typeExpression '>'         # arrayType
    | 'Object'                               # objectType
    | 'Any'                                  # anyType
    ;
