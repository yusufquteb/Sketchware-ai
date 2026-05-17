package pro.sketchware.compiler;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Normalizes generated Activity/Fragment Java so legacy ECJ builds do not fail on
 * common empty-hole patterns and anonymous-listener context pitfalls.
 */
public final class GeneratedCodeSanitizer {

    private static final Set<String> LAMBDA_COMPATIBLE_TYPES = Set.of(
            "Runnable",
            "View.OnClickListener",
            "OnClickListener",
            "View.OnLongClickListener",
            "OnLongClickListener",
            "View.OnTouchListener",
            "OnTouchListener",
            "SwipeRefreshLayout.OnRefreshListener",
            "OnRefreshListener",
            "CompoundButton.OnCheckedChangeListener",
            "OnCheckedChangeListener",
            "AdapterView.OnItemClickListener",
            "OnItemClickListener",
            "AdapterView.OnItemLongClickListener",
            "OnItemLongClickListener",
            "DialogInterface.OnClickListener",
            "OnFailureListener",
            "OnSuccessListener",
            "OnCompleteListener"
    );

    private GeneratedCodeSanitizer() {
    }

    public static String sanitize(String code, String outerClassName, boolean isFragment) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String sanitized = GeneratedCodeSyntaxFixer.fix(code);
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sanitized);
            LexicalPreservingPrinter.setup(compilationUnit);
            compilationUnit.accept(new SanitizingVisitor(), SanitizerContext.create(outerClassName, isFragment));
            optimizeImports(compilationUnit);
            return LexicalPreservingPrinter.print(compilationUnit);
        } catch (ParseProblemException ignored) {
            // Keep the safe syntax-only fixes if parsing fails instead of risking destructive rewrites.
            return sanitized;
        }
    }

    private static void optimizeImports(CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().isEmpty()) {
            return;
        }

        String packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse("");
        Set<String> referencedNames = collectReferencedNames(compilationUnit);

        List<ImportDeclaration> imports = new ArrayList<>(compilationUnit.getImports());
        LinkedHashMap<String, ImportDeclaration> optimizedImports = new LinkedHashMap<>();

        imports.sort(Comparator.comparing(ImportDeclaration::getNameAsString)
                .thenComparing(ImportDeclaration::isStatic)
                .thenComparing(ImportDeclaration::isAsterisk));

        for (ImportDeclaration importDeclaration : imports) {
            if (shouldDropImport(importDeclaration, packageName, referencedNames)) {
                continue;
            }

            optimizedImports.putIfAbsent(importKey(importDeclaration), importDeclaration.clone());
        }

        compilationUnit.getImports().clear();
        optimizedImports.values().forEach(importDeclaration -> compilationUnit.getImports().add(importDeclaration));
    }

    private static boolean shouldDropImport(ImportDeclaration importDeclaration,
                                            String packageName,
                                            Set<String> referencedNames) {
        if (importDeclaration.isStatic()) {
            return false;
        }

        String importedName = importDeclaration.getNameAsString();
        if (importedName.startsWith("java.lang.")) {
            return true;
        }

        String importedPackage = importedName.contains(".")
                ? importedName.substring(0, importedName.lastIndexOf('.'))
                : "";
        if (!packageName.isEmpty() && importedPackage.equals(packageName)) {
            return true;
        }

        if (importDeclaration.isAsterisk()) {
            return false;
        }

        String simpleName = importDeclaration.getName().getIdentifier();
        return !referencedNames.contains(simpleName);
    }

    private static String importKey(ImportDeclaration importDeclaration) {
        return importDeclaration.isStatic() + ":" + importDeclaration.isAsterisk() + ":" + importDeclaration.getNameAsString();
    }

    private static Set<String> collectReferencedNames(CompilationUnit compilationUnit) {
        Set<String> referencedNames = new HashSet<>();
        compilationUnit.findAll(ClassOrInterfaceType.class)
                .forEach(type -> referencedNames.add(type.getNameAsString()));
        compilationUnit.findAll(ObjectCreationExpr.class)
                .forEach(expression -> referencedNames.add(expression.getType().getNameAsString()));
        compilationUnit.findAll(NameExpr.class)
                .forEach(expression -> referencedNames.add(expression.getNameAsString()));
        compilationUnit.findAll(AnnotationExpr.class)
                .forEach(annotation -> referencedNames.add(annotation.getName().getIdentifier()));
        compilationUnit.findAll(FieldAccessExpr.class).forEach(fieldAccessExpr -> {
            if (fieldAccessExpr.getScope().isNameExpr()) {
                referencedNames.add(fieldAccessExpr.getScope().asNameExpr().getNameAsString());
            }
        });
        return referencedNames;
    }

    private static final class SanitizingVisitor extends ModifierVisitor<SanitizerContext> {

        @Override
        public Expression visit(MethodCallExpr methodCall, SanitizerContext context) {
            super.visit(methodCall, context);

            if (methodCall.getScope().isEmpty()) {
                Expression scope = context.getScopeFor(methodCall.getNameAsString());
                if (scope != null) {
                    methodCall.setScope(scope);
                }
            }

            if (isUnscopedStringValueOf(methodCall)) {
                methodCall.addArgument(new StringLiteralExpr(""));
            }

            if (methodCall.getNameAsString().equals("setChecked") && methodCall.getArguments().isEmpty()) {
                methodCall.addArgument(new BooleanLiteralExpr(false));
            }

            if (methodCall.getNameAsString().equals("setProgress") && methodCall.getArguments().isEmpty()) {
                methodCall.addArgument(new IntegerLiteralExpr("0"));
            }

            return methodCall;
        }

        @Override
        public Expression visit(ObjectCreationExpr expression, SanitizerContext context) {
            super.visit(expression, context);
            return maybeConvertToLambda(expression);
        }

        @Override
        public Expression visit(ThisExpr thisExpr, SanitizerContext context) {
            super.visit(thisExpr, context);
            if (thisExpr.getTypeName().isPresent() || !isBareThisArgument(thisExpr)) {
                return thisExpr;
            }
            return context.bareThisReplacement().clone();
        }

        private Expression maybeConvertToLambda(ObjectCreationExpr expression) {
            if (expression.getAnonymousClassBody().isEmpty()) {
                return expression;
            }
            if (!isLambdaCompatibleType(expression.getType().asString())) {
                return expression;
            }

            List<BodyDeclaration<?>> members = expression.getAnonymousClassBody().orElse(new NodeList<>());
            if (members.size() != 1 || !(members.get(0) instanceof MethodDeclaration methodDeclaration)) {
                return expression;
            }

            if (methodDeclaration.getBody().isEmpty()) {
                return expression;
            }

            LambdaExpr lambdaExpr = new LambdaExpr();
            NodeList<com.github.javaparser.ast.body.Parameter> parameters = new NodeList<>();
            methodDeclaration.getParameters().forEach(parameter -> {
                com.github.javaparser.ast.body.Parameter lambdaParameter = parameter.clone();
                lambdaParameter.setType(new UnknownType());
                lambdaParameter.setVarArgs(false);
                parameters.add(lambdaParameter);
            });
            lambdaExpr.setParameters(parameters);
            lambdaExpr.setEnclosingParameters(parameters.size() != 1);
            lambdaExpr.setBody(methodDeclaration.getBody().get().clone());
            return lambdaExpr;
        }

        private boolean isLambdaCompatibleType(String typeName) {
            if (LAMBDA_COMPATIBLE_TYPES.contains(typeName)) {
                return true;
            }

            int genericStart = typeName.indexOf('<');
            if (genericStart > 0) {
                return LAMBDA_COMPATIBLE_TYPES.contains(typeName.substring(0, genericStart));
            }

            return false;
        }

        private boolean isUnscopedStringValueOf(MethodCallExpr methodCall) {
            if (!methodCall.getNameAsString().equals("valueOf") || !methodCall.getArguments().isEmpty()) {
                return false;
            }

            return methodCall.getScope()
                    .filter(Expression::isNameExpr)
                    .map(Expression::asNameExpr)
                    .map(nameExpr -> nameExpr.getNameAsString().equals("String"))
                    .orElse(false);
        }

        private boolean isBareThisArgument(ThisExpr thisExpr) {
            Node parent = thisExpr.getParentNode().orElse(null);
            if (parent instanceof NodeWithArguments<?> withArguments) {
                return withArguments.getArguments().stream().anyMatch(argument -> argument == thisExpr);
            }
            if (parent instanceof ExplicitConstructorInvocationStmt invocationStmt) {
                return invocationStmt.getArguments().stream().anyMatch(argument -> argument == thisExpr);
            }
            return false;
        }
    }

    private record SanitizerContext(
            Expression bareThisReplacement,
            Expression applicationContextScope,
            Expression baseContextScope,
            Expression systemServiceScope,
            Expression findViewByIdScope,
            Expression runOnUiThreadScope,
            Expression startActivityScope,
            Expression finishScope) {

        static SanitizerContext create(String outerClassName, boolean isFragment) {
            String trimmedOuterClassName = Objects.requireNonNullElse(outerClassName, "").trim();
            String activityScope;
            if (isFragment) {
                activityScope = "getActivity()";
            } else {
                activityScope = trimmedOuterClassName.isEmpty() ? "this" : trimmedOuterClassName + ".this";
            }
            String contextScope = isFragment ? "getContext()" : activityScope;

            return new SanitizerContext(
                    parseExpression(activityScope),
                    parseExpression(contextScope),
                    parseExpression(activityScope),
                    parseExpression(contextScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope)
            );
        }

        Expression getScopeFor(String methodName) {
            return switch (methodName) {
                case "getApplicationContext" -> applicationContextScope.clone();
                case "getBaseContext" -> baseContextScope.clone();
                case "getSystemService" -> systemServiceScope.clone();
                case "findViewById" -> findViewByIdScope.clone();
                case "runOnUiThread" -> runOnUiThreadScope.clone();
                case "startActivity" -> startActivityScope.clone();
                case "finish" -> finishScope.clone();
                default -> null;
            };
        }

        private static Expression parseExpression(String expression) {
            return StaticJavaParser.parseExpression(expression);
        }
    }
}
