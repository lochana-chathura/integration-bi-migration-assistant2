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
package common;

import common.BallerinaModel.ClassDef;
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.MinutiaeList;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.ObjectFieldNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.tools.text.TextDocuments;
import org.ballerinalang.formatter.core.Formatter;
import org.ballerinalang.formatter.core.FormatterException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static common.BallerinaModel.Function;
import static common.BallerinaModel.Import;
import static common.BallerinaModel.Listener;
import static common.BallerinaModel.ModuleTypeDef;
import static common.BallerinaModel.ModuleVar;
import static common.BallerinaModel.ObjectField;
import static common.BallerinaModel.Parameter;
import static common.BallerinaModel.Remote;
import static common.BallerinaModel.Resource;
import static common.BallerinaModel.Service;
import static common.BallerinaModel.Statement;
import static common.BallerinaModel.TextDocument;
import static common.BallerinaModel.TypeDesc;

public class CodeGenerator {
    private final TextDocument textDocument;

    public CodeGenerator(TextDocument textDocument) {
        this.textDocument = textDocument;
    }

    /**
     * Generates a syntax tree from IR TextDocument.
     *
     * @return SyntaxTree
     */
    public SyntaxTree generateSyntaxTree() {
        List<ImportDeclarationNode> imports = new ArrayList<>();
        for (Import importDeclaration : textDocument.imports()) {
            ImportDeclarationNode importDeclarationNode =
                    NodeParser.parseImportDeclaration(importDeclaration.toString());
            imports.add(importDeclarationNode);
        }

        List<ModuleMemberDeclarationNode> moduleMembers = new ArrayList<>(textDocument.astNodes());

        for (ModuleTypeDef moduleTypeDef : textDocument.moduleTypeDefs()) {
            TypeDefinitionNode typeDefinitionNode = (TypeDefinitionNode) NodeParser.parseModuleMemberDeclaration(
                    moduleTypeDef.toString());
            moduleMembers.add(typeDefinitionNode);
        }

        for (ModuleVar moduleVar : textDocument.moduleVars()) {
            ModuleMemberDeclarationNode member = NodeParser.parseModuleMemberDeclaration(moduleVar.toString());
            moduleMembers.add(member);
        }

        for (Listener listener : textDocument.listeners()) {
            ModuleMemberDeclarationNode member = NodeParser.parseModuleMemberDeclaration(listener.toString());
            moduleMembers.add(member);
        }

        for (Service service : textDocument.services()) {
            String listenerRefs = constructCommaSeparatedString(service.listenerRefs());
            String comment = service.comment().isPresent() ? service.comment().get().toString() : "";
            ServiceDeclarationNode serviceDecl = (ServiceDeclarationNode) NodeParser.parseModuleMemberDeclaration(
                    String.format("%sservice %s on %s { }", comment, service.basePath(), listenerRefs));

            List<Node> members = new ArrayList<>();
            for (ObjectField field : service.fields()) {
                ObjectFieldNode objectFieldNode = (ObjectFieldNode) NodeParser.parseObjectMember(
                        String.format("%s %s;", field.type(), field.name()));
                members.add(objectFieldNode);
            }

            if (service.initFunc().isPresent()) {
                members.add(genFunctionDefinitionNode(service.initFunc().get()));
            }

            for (Resource resource : service.resources()) {
                String funcParamStr = constructFunctionParameterString(resource.parameters(), false);
                FunctionDefinitionNode resourceMethod = (FunctionDefinitionNode) NodeParser.parseObjectMember(
                        String.format("resource function %s %s(%s) %s {}",
                                resource.resourceMethodName(), resource.path(), funcParamStr,
                                getReturnTypeDescriptor(resource.returnType())));

                FunctionBodyBlockNode funcBodyBlock = constructFunctionBodyBlock(resource.body());
                resourceMethod = resourceMethod.modify().withFunctionBody(funcBodyBlock).apply();
                members.add(resourceMethod);
            }

            for (Function function : service.functions()) {
                FunctionDefinitionNode funcDefn = genFunctionDefinitionNode(function);
                members.add(funcDefn);
            }

            for (Remote remote : service.remoteFunctions()) {
                Function function = remote.function();
                String funcParamStr = constructFunctionParameterString(function.parameters(), false);
                FunctionDefinitionNode remoteMethod = (FunctionDefinitionNode) NodeParser.parseObjectMember(
                        String.format("remote function %s(%s) %s {}",
                                function.functionName(), funcParamStr,
                                getReturnTypeDescriptor(function.returnType())));

                FunctionBodyBlockNode funcBodyBlock = constructFunctionBodyBlock(
                        ((BallerinaModel.BlockFunctionBody) function.body()).statements());
                remoteMethod = remoteMethod.modify().withFunctionBody(funcBodyBlock).apply();
                members.add(remoteMethod);
            }

            NodeList<Node> nodeList = NodeFactory.createNodeList(members);
            serviceDecl = serviceDecl.modify().withMembers(nodeList).apply();
            moduleMembers.add(serviceDecl);
        }

        for (ClassDef classDef : textDocument.classDefs()) {
            String inclusionsStr = classDef.typeInclusions().stream().map("*%s;"::formatted)
                    .collect(Collectors.joining());
            String fieldsStr = classDef.fields().stream().map(ObjectField::toString).collect(Collectors.joining());
            List<FunctionDefinitionNode> funcDefNode = new ArrayList<>(classDef.methods().size());
            for (Function f : classDef.methods()) {
                funcDefNode.add(genFunctionDefinitionNode(f));
            }
            String funcStr = funcDefNode.stream().map(FunctionDefinitionNode::toSourceCode)
                    .collect(Collectors.joining());
            String classDefStr = "class %s { %s %s %s }".formatted(classDef.className(), inclusionsStr, fieldsStr,
                    funcStr);
            moduleMembers.add(NodeParser.parseModuleMemberDeclaration(classDefStr));
        }

        for (Function f : textDocument.functions()) {
            String funcParamString = constructFunctionParameterString(f.parameters(), false);
            String methodName = f.functionName();
            FunctionDefinitionNode functionDefinitionNode;
            if (f.body() instanceof BallerinaModel.BlockFunctionBody) {
                FunctionDefinitionNode fd = (FunctionDefinitionNode) NodeParser.parseModuleMemberDeclaration(
                        String.format("%sfunction %s(%s) %s {}", getVisibilityQualifier(f.visibilityQualifier()),
                                methodName, funcParamString, getReturnTypeDescriptor(f.returnType())));
                functionDefinitionNode = generateBallerinaFunction(fd, f.body());
            } else {
                functionDefinitionNode = generateBallerinaExternalFunction(f, funcParamString, methodName);
            }
            moduleMembers.add(functionDefinitionNode);
        }

        for (String f : textDocument.intrinsics()) {
            moduleMembers.add(NodeParser.parseModuleMemberDeclaration(f));
        }

        NodeList<ImportDeclarationNode> importDecls = NodeFactory.createNodeList(imports);
        NodeList<ModuleMemberDeclarationNode> moduleMemberDecls = NodeFactory.createNodeList(moduleMembers);

        MinutiaeList eofLeadingMinutiae;
        if (textDocument.Comments().isEmpty()) {
            eofLeadingMinutiae = NodeFactory.createEmptyMinutiaeList();
        } else {
            String comments = String.join("\n", textDocument.Comments());
            eofLeadingMinutiae = parseLeadingMinutiae(comments);
        }

        SyntaxTree syntaxTree = createSyntaxTree(importDecls, moduleMemberDecls, eofLeadingMinutiae);
        // This is to a hack to avoid OOM when we give huge projects
        if (System.getenv("BAL_MIGRATE_SKIP_FORMATTING") == null) {
            syntaxTree = formatSyntaxTree(syntaxTree);
        }
        return syntaxTree;
    }

    private FunctionDefinitionNode genFunctionDefinitionNode(Function function) {
        String funcParamString = constructFunctionParameterString(function.parameters(), false);
        FunctionDefinitionNode functionDefinitionNode;
        if (function.body() instanceof BallerinaModel.BlockFunctionBody) {
            functionDefinitionNode = (FunctionDefinitionNode) NodeParser.parseObjectMember(
                    String.format("%sfunction %s(%s) %s {}", getVisibilityQualifier(
                                    function.visibilityQualifier()), function.functionName(), funcParamString,
                            getReturnTypeDescriptor(function.returnType())));
            functionDefinitionNode = generateBallerinaFunction(functionDefinitionNode, function.body());
        } else {
            functionDefinitionNode = generateBallerinaExternalFunction(function, funcParamString,
                    function.functionName());
        }
        return functionDefinitionNode;
    }

    private FunctionDefinitionNode generateBallerinaExternalFunction(Function f, String funcParamString,
                                                                     String methodName) {
        BallerinaModel.ExternFunctionBody body = (BallerinaModel.ExternFunctionBody) f.body();
        return (FunctionDefinitionNode) NodeParser.parseModuleMemberDeclaration(
                String.format("%sfunction %s(%s) %s  = %s { %s } external;", getVisibilityQualifier(
                        f.visibilityQualifier()),
                        methodName, funcParamString, getReturnTypeDescriptor(f.returnType()), body.annotation(),
                        getExternBody((BallerinaModel.ExternFunctionBody) f.body())));
    }

    private String getExternBody(BallerinaModel.ExternFunctionBody body) {
        StringBuilder s = new StringBuilder();
        s.append("\n'class: \"").append(body.className()).append("\"");
        if (body.javaMethodName().isPresent()) {
            s.append(",\n name: \"").append(body.javaMethodName().get()).append("\"");
        }
        if (body.paramTypes().isPresent()) {
            s.append(",\n paramTypes: [");
            String paramTypeString = String.join(", ",
                    body.paramTypes().get().stream()
                            .map(param -> "\"" + param + "\"")
                            .toList()
            );

            s.append(paramTypeString).append("]");
        }
        return s.append("\n").toString();
    }

    private FunctionDefinitionNode generateBallerinaFunction(FunctionDefinitionNode fd,
                                                             BallerinaModel.FunctionBody body) {
        FunctionBodyBlockNode funcBodyBlock = constructFunctionBodyBlock(((BallerinaModel.BlockFunctionBody)
                body).statements());
        return fd.modify().withFunctionBody(funcBodyBlock).apply();
    }

    private static MinutiaeList parseLeadingMinutiae(String leadingMinutiae) {
        return NodeParser.parseImportDeclaration(leadingMinutiae + "\nimport x/y;").leadingMinutiae();
    }

    private static SyntaxTree createSyntaxTree(NodeList<ImportDeclarationNode> importDecls,
                                               NodeList<ModuleMemberDeclarationNode> moduleMemberDecls,
                                               MinutiaeList eofLeadingMinutiae) {
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(
                importDecls,
                moduleMemberDecls,
                NodeFactory.createToken(SyntaxKind.EOF_TOKEN, eofLeadingMinutiae, NodeFactory.createEmptyMinutiaeList())
        );

        SyntaxTree syntaxTree = SyntaxTree.from(TextDocuments.from(""));
        syntaxTree = syntaxTree.modifyWith(modulePartNode);
        return syntaxTree;
    }

    public static SyntaxTree formatSyntaxTree(SyntaxTree syntaxTree) {
        try {
            syntaxTree = Formatter.format(syntaxTree);
        } catch (FormatterException e) {
            throw new RuntimeException("Error formatting the syntax tree");
        }
        return syntaxTree;
    }

    private String getReturnTypeDescriptor(Optional<TypeDesc> returnType) {
        return returnType.map(r ->  String.format("returns %s", r)).orElse("");
    }

    private String getVisibilityQualifier(Optional<String> visibilityQualifier) {
        return visibilityQualifier.map(s -> s + " ").orElse("");
    }

    private String constructCommaSeparatedString(List<String> strings) {
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<String> iterator = strings.iterator();
        while (iterator.hasNext()) {
            String listener = iterator.next();
            stringBuilder.append(listener);
            if (iterator.hasNext()) {
                stringBuilder.append(", ");
            }
        }
        return stringBuilder.toString();
    }

    private String constructFunctionParameterString(List<Parameter> parameters, boolean skipDefaultExpr) {
        if (skipDefaultExpr) {
            return String.join(",", parameters.stream().map(p -> String.format("%s %s", p.type(), p.name()))
                    .toList());
        }

        return String.join(",", parameters.stream().map(p -> p.defaultExpr().isPresent() ?
                String.format("%s %s = %s", p.type(), p.name(), p.defaultExpr().get().expr()) :
                String.format("%s %s", p.type(), p.name())).toList());
    }

    private FunctionBodyBlockNode constructFunctionBodyBlock(List<Statement> body) {
        List<String> stmtList = new ArrayList<>();
        for (Statement statement : body) {
            stmtList.add(statement.toString());
        }

        String joinedStatements = String.join("", stmtList);
        return NodeParser.parseFunctionBodyBlock(String.format("{ %s }", joinedStatements));
    }
}
