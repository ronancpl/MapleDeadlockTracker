/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package deadlocktracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import javalexer.JavaLexer;
import javaparser.JavaParser;
import javaparser.JavaParserBaseListener;
import deadlocktracker.containers.DeadlockClass;
import deadlocktracker.containers.DeadlockClass.DeadlockClassType;
import deadlocktracker.containers.DeadlockEnum;
import deadlocktracker.containers.DeadlockFunction;
import deadlocktracker.containers.DeadlockLock;
import deadlocktracker.containers.DeadlockStorage;
import deadlocktracker.containers.Pair;
import deadlocktracker.graph.DeadlockAbstractType;
import deadlocktracker.strings.IgnoredTypes;
import deadlocktracker.strings.LinkedTypes;
import deadlocktracker.strings.ReflectedTypes;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 *
 * @author RonanLana
 */
public class DeadlockReader extends JavaParserBaseListener {
    private static DeadlockStorage storage = new DeadlockStorage();
    private static String syncLockTypeName = "SyncLock";
    
    // ---- cached storage fields ----
    private static Map<String, Map<String, DeadlockClass>> PublicClasses = storage.getPublicClasses();
    private static Map<String, Map<String, DeadlockClass>> PrivateClasses = storage.getPrivateClasses();
    
    private static Map<String, DeadlockLock> Locks = storage.getLocks();
    private static Map<String, DeadlockLock> ReadWriteLocks = storage.getReadWriteLocks();
    
    private static Map<DeadlockClass, Integer> ClassDataTypes = storage.getClassDataTypes();
    private static Map<List<Integer>, Integer> CompoundDataTypes = storage.getCompoundDataTypes();
    private static Map<String, Integer> BasicDataTypes = storage.getBasicDataTypes();
    private static Map<Integer, Integer> ElementalDataTypes = storage.getElementalDataTypes();
    private static Integer[] ElementalTypes = storage.getElementalTypes();
    
    private static Map<DeadlockClass, List<DeadlockClass>> InheritanceTree = storage.getInheritanceTree();
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> ReflectedClasses = storage.getReflectedClasses();
    
    private static Map<DeadlockFunction, Boolean> RunnableFunctions = new HashMap<>();
    private static List<DeadlockFunction> RunnableMethods = storage.getRunnableMethods();
    
    //private static Map<Integer, String> CompoundDataNames = new HashMap();   // test purposes only
    
    // ---- volatile fields ----
    
    private static AtomicInteger runningId = new AtomicInteger(1);
    private static AtomicInteger runningSyncLockId = new AtomicInteger(0);
    private static AtomicInteger runningTypeId = new AtomicInteger(1);  // volatile id 0 reserved for sync locks
    private static AtomicInteger runningMethodCallCount = new AtomicInteger(0);
    
    private static Stack<Integer> methodCallCountStack = new Stack();
    private static Stack<DeadlockFunction> methodStack = new Stack();
    private static List<DeadlockClass> classStack = new ArrayList();
    private static Stack<Integer> syncLockStack = new Stack();
    
    private static Set<String> readLockWaitingSet = new HashSet();
    private static Set<String> writeLockWaitingSet = new HashSet();
    
    private static Map<String, String> readLockQueue = new HashMap();
    private static Map<String, String> writeLockQueue = new HashMap();
    
    private static Map<Integer, String> LinkedDataNames = new HashMap();
    
    private static List<String> currentImportList = new ArrayList<>();
    private static String currentPackageName;
    private static String currentCompleteFileClassName;
    private static DeadlockClass currentClass = null;
    private static List<DeadlockClass> customClasses = new LinkedList<>();
    private static boolean currentAbstract = false;
    
    private static Map<Integer, Pair<DeadlockClass, Integer>> volatileMaskedTypes = new HashMap<>();
    private static Map<Integer, Pair<String, String>> volatileDataTypes = new HashMap<>();  // cannot recover the import classes at the first parsing, so the type definition comes at the second rundown
    
    @Override
    public void enterCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        currentImportList.clear();
    }
    
    @Override
    public void enterImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
        String s = ctx.qualifiedName().getText();
        if(!(ctx.getChild(ctx.getChildCount() - 2) instanceof JavaParser.QualifiedNameContext)) {   // implies an ".*" import was found
            s += ".*";
        }
        
        currentImportList.add(s);
    }
    
    @Override
    public void enterPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
        currentPackageName = ctx.qualifiedName().getText() + ".";
    }
    
    @Override
    public void exitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) { }
    
    @Override
    public void enterClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
        currentAbstract = false;
        
        if(ctx.memberDeclaration() != null) {
            currentAbstract = hasAbstractModifier(ctx.modifier());
        } else if(ctx.block() != null) {
            DeadlockFunction method = new DeadlockFunction("class", currentClass, null, false);
            
            method.setMethodMetadata(-1, new LinkedList<Integer>(), new HashMap<Long, Integer>());
            methodStack.add(method);
        }
    }
    
    @Override
    public void exitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
        if(ctx.block() != null) {
            methodStack.pop();
        }
    }
    
    private static boolean hasAbstractModifier(List<JavaParser.ModifierContext> mods) {
        for(JavaParser.ModifierContext mctx : mods) {
            if(mctx.classOrInterfaceModifier() != null && mctx.classOrInterfaceModifier().ABSTRACT() != null) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean hasSynchronizedModifier(List<JavaParser.ModifierContext> mods) {
        for(JavaParser.ModifierContext mctx : mods) {
            if(mctx.SYNCHRONIZED() != null) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void enterInterfaceBodyDeclaration(JavaParser.InterfaceBodyDeclarationContext ctx) {
        currentAbstract = false;
        
        if(ctx.interfaceMemberDeclaration() != null) {
            currentAbstract = hasAbstractModifier(ctx.modifier());
        }
    }
    
    private boolean isAbstractTypeDeclaration(List<JavaParser.ClassOrInterfaceModifierContext> list) {
        for(JavaParser.ClassOrInterfaceModifierContext cim : list) {
            if(cim.ABSTRACT() != null) {
                return true;
            }
        }
        
        return false;
    }
    
    private static List<String> getExtendedImplementedList(JavaParser.TypeTypeContext ttcCtx, JavaParser.TypeListContext tlcCtx) {
        List<String> list = new LinkedList<>();
        
        if(ttcCtx != null) {
            list.add(ttcCtx.getText());
        }
        
        if(tlcCtx != null) {
            for(JavaParser.TypeTypeContext tt : tlcCtx.typeType()) {
                list.add(tt.getText());
            }
        }
        
        return list;
    }
    
    private static String getPathName(String className) {
        String path = "";
        
        for(DeadlockClass mdc : classStack) {
            path += mdc.getName() + ".";
        }
        path += className;
        
        return path;
    }
    
    @Override
    public void enterCreator(JavaParser.CreatorContext ctx) {
        if (ctx.createdName().IDENTIFIER().size() > 0 && ctx.classCreatorRest() != null && ctx.classCreatorRest().classBody() != null) {
            String className = ctx.createdName().IDENTIFIER().get(0).getText();
            className = className + "_" + (customClasses.size() + 1);

            classStack.add(currentClass);

            List<String> supNames = new LinkedList<>();
            supNames.add(currentClass.getName());

            currentClass = new DeadlockClass(DeadlockClassType.CLASS, className, currentPackageName, getPathName(className), supNames, false, currentClass);
            customClasses.add(currentClass);

            InheritanceTree.put(currentClass, new LinkedList<>());
        }
    }
    
    @Override
    public void exitCreator(JavaParser.CreatorContext ctx) {
        if (ctx.createdName().IDENTIFIER().size() > 0 && ctx.classCreatorRest() != null && ctx.classCreatorRest().classBody() != null) {
            String fcn = currentCompleteFileClassName;
            
            if (PrivateClasses.containsKey(fcn)) {
                PrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                PrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            DeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterInnerCreator(JavaParser.InnerCreatorContext ctx) {
        if (ctx.classCreatorRest() != null && ctx.classCreatorRest().classBody() != null) {
            String className = ctx.IDENTIFIER().getText();
            className = className + "_" + (customClasses.size() + 1);
            classStack.add(currentClass);

            List<String> supNames = new LinkedList<>();
            supNames.add(currentClass.getName());

            currentClass = new DeadlockClass(DeadlockClassType.CLASS, className, currentPackageName, getPathName(className), supNames, false, currentClass);
            customClasses.add(currentClass);

            InheritanceTree.put(currentClass, new LinkedList<>());
        }
    }
    
    @Override
    public void exitInnerCreator(JavaParser.InnerCreatorContext ctx) {
        if (ctx.classCreatorRest() != null && ctx.classCreatorRest().classBody() != null) {
            String fcn = currentCompleteFileClassName;
            if (PrivateClasses.containsKey(fcn)) {
                PrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                PrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }

            DeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        String className = ctx.IDENTIFIER().getText();
        boolean isAbstract = false;
        
        JavaParser.TypeTypeContext extended = ctx.typeType();       // from rule "(EXTENDS typeType)?"
        JavaParser.TypeListContext implemented = ctx.typeList();    // from rule "(IMPLEMENTS typeList)?"
        List<String> superNames = getExtendedImplementedList(extended, implemented);
        
        ParserRuleContext parCtx = ctx.getParent();
        if(parCtx instanceof JavaParser.TypeDeclarationContext) {
            JavaParser.TypeDeclarationContext tdc = (JavaParser.TypeDeclarationContext) parCtx;
            isAbstract = isAbstractTypeDeclaration(tdc.classOrInterfaceModifier());
        } else if(parCtx instanceof JavaParser.LocalTypeDeclarationContext) {
            JavaParser.LocalTypeDeclarationContext ltdc = (JavaParser.LocalTypeDeclarationContext) parCtx;
            isAbstract = isAbstractTypeDeclaration(ltdc.classOrInterfaceModifier());
        }
        
        if(currentClass != null) {
            classStack.add(currentClass);
            currentClass = new DeadlockClass(DeadlockClassType.CLASS, className, currentPackageName, getPathName(className), superNames, isAbstract, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new DeadlockClass(DeadlockClassType.CLASS, className, currentPackageName, getPathName(className), superNames, isAbstract, null);
        }
        
        InheritanceTree.put(currentClass, new LinkedList<>());
        
        JavaParser.TypeParametersContext maskCtx = ctx.typeParameters();
        if(maskCtx != null) {
            for(JavaParser.TypeParameterContext typC : maskCtx.typeParameter()) {
                Integer mType = getTypeId(typC.getText(), currentCompleteFileClassName);
                
                volatileMaskedTypes.put(mType, new Pair<>(currentClass, currentClass.getMaskedTypeSize()));
                currentClass.addMaskedType(mType);
            }
        }
    }
    
    @Override
    public void exitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        if(classStack.isEmpty()) {
            for(String s : currentImportList) {
                currentClass.addImport(s);
            }
            
            if(PublicClasses.containsKey(currentPackageName)) {
                PublicClasses.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                PublicClasses.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(PrivateClasses.containsKey(fcn)) {
                PrivateClasses.get(fcn).put(getPathName(currentClass.getName()), currentClass);
            } else {
                PrivateClasses.put(fcn, newPackageClass(getPathName(currentClass.getName()), currentClass));
            }
            
            DeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        String className = ctx.IDENTIFIER().getText();
        
        JavaParser.TypeListContext implemented = ctx.typeList();    // from rule "(IMPLEMENTS typeList)?"
        List<String> superNames = getExtendedImplementedList(null, implemented);
        
        if(currentClass != null) {
            classStack.add(currentClass);
            currentClass = new DeadlockEnum(className, currentPackageName, getPathName(className), superNames, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new DeadlockEnum(className, currentPackageName, getPathName(className), superNames, null);
        }
        
        InheritanceTree.put(currentClass, new LinkedList<>());
    }
    
    @Override
    public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        if(classStack.isEmpty()) {
            for(String s : currentImportList) {
                currentClass.addImport(s);
            }
            
            if(PublicClasses.containsKey(currentPackageName)) {
                PublicClasses.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                PublicClasses.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(PrivateClasses.containsKey(fcn)) {
                PrivateClasses.get(fcn).put(getPathName(currentClass.getName()), currentClass);
            } else {
                PrivateClasses.put(fcn, newPackageClass(getPathName(currentClass.getName()), currentClass));
            }
            
            DeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterEnumConstant(JavaParser.EnumConstantContext ctx) {
        DeadlockEnum mde = (DeadlockEnum) currentClass;
        mde.addEnumItem(ctx.IDENTIFIER().getText());
    }
    
    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        String className = ctx.IDENTIFIER().getText();
        
        JavaParser.TypeListContext extended = ctx.typeList();    // from rule "(EXTENDS typeList)?"
        List<String> superNames = getExtendedImplementedList(null, extended);
        
        if(currentClass != null) {
            classStack.add(currentClass);
            currentClass = new DeadlockClass(DeadlockClassType.INTERFACE, className, currentPackageName, getPathName(className), superNames, true, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new DeadlockClass(DeadlockClassType.INTERFACE, className, currentPackageName, getPathName(className), superNames, true, null);
        }
        
        InheritanceTree.put(currentClass, new LinkedList<>());
    }
    
    @Override
    public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        if(classStack.isEmpty()) {
            for(String s : currentImportList) {
                currentClass.addImport(s);
            }
            
            if(PublicClasses.containsKey(currentPackageName)) {
                PublicClasses.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                PublicClasses.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(PrivateClasses.containsKey(fcn)) {
                PrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                PrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            DeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterInterfaceMemberDeclaration(JavaParser.InterfaceMemberDeclarationContext ctx) {
        JavaParser.ConstDeclarationContext cdCtx = ctx.constDeclaration();
        
        if(cdCtx != null) {
            List<JavaParser.ConstantDeclaratorContext> cdList = cdCtx.constantDeclarator();
            String typeText = cdCtx.typeType().getText();
            
            for(JavaParser.ConstantDeclaratorContext cd : cdList) {
                String curText = cd.getText();
                curText = curText.substring(0, curText.indexOf('='));
                
                String tt = getFullTypeText(typeText, curText);
                Integer type = getTypeId(tt, currentCompleteFileClassName);
                
                currentClass.addFieldVariable(type, cd.IDENTIFIER().getText());
            }
        }
    }
    
    private static Map<String, DeadlockClass> newPackageClass(String s, DeadlockClass c) {
        Map<String, DeadlockClass> m = new HashMap<>();
        m.put(s, c);
        
        return m;
    }
    
    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        String methodName = ctx.IDENTIFIER().getText();
        
        DeadlockFunction method = new DeadlockFunction(methodName, currentClass, methodStack.isEmpty() ? null : methodStack.peek(), currentAbstract);
        Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> retParamTypes = getMethodMetadata(ctx, method);
        
        method.setMethodMetadata(retParamTypes.left, retParamTypes.right.left, retParamTypes.right.right);
        methodStack.add(method);
        
        methodCallCountStack.add(runningMethodCallCount.get());
        runningMethodCallCount.set(0);
    }
    
    @Override
    public void exitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        DeadlockFunction method = methodStack.pop();
        
        String methodName = method.getName();
        if(methodName.contentEquals("run") && method.getParameters().isEmpty()) {
            // book-keeping possible Runnable functions to be dealt with later on the parsing
            
            RunnableFunctions.put(method, !methodStack.isEmpty());
        } else {
            JavaParser.ClassBodyDeclarationContext par;
            if(ctx.getParent() instanceof JavaParser.MemberDeclarationContext) {
                par = (JavaParser.ClassBodyDeclarationContext) ctx.getParent().getParent();
            } else {
                par = (JavaParser.ClassBodyDeclarationContext) ctx.getParent().getParent().getParent();
            }
            
            List<JavaParser.ModifierContext> mods = par.modifier();
            
            if(mods != null && hasSynchronizedModifier(mods)) {
                Integer syncLockId = runningSyncLockId.getAndIncrement();
                String syncLockName = getSyncLockName(syncLockId);
                
                currentClass.addFieldVariable(0, syncLockName);
                processLock(syncLockTypeName, syncLockName, "");   // create a lock representation of the synchronized modifier
                method.setSynchronizedModifier(generateSyncLockExpression(syncLockName, true), generateSyncLockExpression(syncLockName, false));
            }
            
            currentClass.addClassMethod(method);
        }
        
        runningMethodCallCount.set(methodCallCountStack.pop());
    }
    
    @Override
    public void enterConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        String methodName = ctx.IDENTIFIER().getText();
        
        DeadlockFunction method = new DeadlockFunction(methodName, currentClass, null, false);
        Pair<List<Integer>, Map<Long, Integer>> pTypes = getMethodParameterTypes(ctx.formalParameters(), method);
        
        method.setMethodMetadata(-1, pTypes.left, pTypes.right);
        methodStack.add(method);
    }
    
    @Override
    public void exitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        DeadlockFunction method = methodStack.pop();
        currentClass.addClassMethod(method);
    }
    
    @Override
    public void enterFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
        processVariableDeclarations(true, ctx.typeType().getText(), ctx.variableDeclarators().variableDeclarator());
    }
    
    @Override
    public void enterLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
        processVariableDeclarations(false, ctx.typeType().getText(), ctx.variableDeclarators().variableDeclarator());
    }
    
    @Override
    public void enterEnhancedForControl(JavaParser.EnhancedForControlContext ctx) {
        processLocalVariableDeclaratorId(ctx.typeType().getText(), ctx.variableDeclaratorId(), methodStack.peek());
    }
    
    @Override
    public void enterResource(JavaParser.ResourceContext ctx) {
        processLocalVariableDeclaratorId(ctx.classOrInterfaceType().getText(), ctx.variableDeclaratorId(), methodStack.peek());
    }
    
    @Override
    public void enterCatchClause(JavaParser.CatchClauseContext ctx) {
        // ctx.catchType().getText(), but generalize all possible types to the basic Exception one
        
        processLocalVariableDeclaratorId("Exception", ctx.IDENTIFIER().getText(), methodStack.peek());
    }
    
    
    @Override
    public void enterElementValuePair(JavaParser.ElementValuePairContext ctx) {
        String lockName = currentPackageName + currentClass.getPathName() + "." + ctx.IDENTIFIER().getText();
                
        if(!readLockWaitingSet.isEmpty() || !writeLockWaitingSet.isEmpty()) {
            if(readLockWaitingSet.contains(lockName)) {
                processLock("ReadLock1", ctx.IDENTIFIER().getText(), captureInitializer(ctx.elementValue().expression()));
            } else if(writeLockWaitingSet.contains(lockName)) {
                processLock("WriteLock1", ctx.IDENTIFIER().getText(), captureInitializer(ctx.elementValue().expression()));
            }
        }
    }
    
    private static String getSyncLockName(Integer syncLockId) {
        return "synchLock" + syncLockId;
    }
    
    private static JavaParser.ExpressionContext generateSyncLockExpression(String syncLockName, boolean lock) {
        String lockStrExpr = syncLockName + "." + (lock ? "lock" : "unlock") + "();";
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(lockStrExpr));
        CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(commonTokenStream);
        
        return parser.expression();
    }
    
    @Override
    public void enterStatement(JavaParser.StatementContext ctx) {
        if(ctx.SYNCHRONIZED() != null) {
            Integer syncLockId = runningSyncLockId.getAndIncrement();
            String syncLockName = getSyncLockName(syncLockId);
            
            currentClass.addFieldVariable(0, syncLockName);
            processLock(syncLockTypeName, syncLockName, "");   // create a lock representation of the synchronized modifier
            methodStack.peek().addMethodCall(generateSyncLockExpression(syncLockName, true));
            
            syncLockStack.push(syncLockId);
        } else {
            Pair<String, String> lockData = captureLockNameAndReference(ctx.expression(0));

            if(lockData != null) {
                String refName = currentPackageName + currentClass.getPathName() + "." + lockData.right;

                if(!readLockWaitingSet.isEmpty() || !writeLockWaitingSet.isEmpty()) {
                    if(readLockWaitingSet.contains(refName)) {
                        processLock("ReadLock2", lockData.left, lockData.right);
                    } else if(writeLockWaitingSet.contains(refName)) {
                        processLock("WriteLock2", lockData.left, lockData.right);
                    }
                }
            }
            /*
            else {
                Pair<String, Byte> lockStmt = captureLockStatement(ctx.expression(0));

                if(lockStmt != null) {

                }
            }
            */
        }
    }
    
    @Override
    public void exitStatement(JavaParser.StatementContext ctx) {
        if(ctx.SYNCHRONIZED() != null) {
            Integer syncLockId = syncLockStack.pop();
            methodStack.peek().addMethodCall(generateSyncLockExpression(getSyncLockName(syncLockId), false));
        }
    }
    
    @Override
    public void enterMethodCall(JavaParser.MethodCallContext ctx) {
        runningMethodCallCount.incrementAndGet();
    }
    
    @Override
    public void exitMethodCall(JavaParser.MethodCallContext ctx) {
        int count = runningMethodCallCount.decrementAndGet();
        
        if(count == 0 && !methodStack.isEmpty()) {
            ParserRuleContext parCtx = ctx.getParent().getParent();
            
            if(parCtx instanceof JavaParser.StatementContext) {
                JavaParser.StatementContext sc = (JavaParser.StatementContext) parCtx;
                addMethodsFromExpressionList(sc.expression());
            } else if(parCtx instanceof JavaParser.ParExpressionContext) {
                JavaParser.ParExpressionContext pc = (JavaParser.ParExpressionContext) parCtx;
                methodStack.peek().addMethodCall(pc.expression());
            } else if(parCtx instanceof JavaParser.ExpressionContext) {
                JavaParser.ExpressionContext ec = (JavaParser.ExpressionContext) parCtx;
                addMethodsFromExpressionList(ec.expression());
            } else if(parCtx instanceof JavaParser.VariableInitializerContext) {
                JavaParser.VariableInitializerContext vic = (JavaParser.VariableInitializerContext) parCtx;
                methodStack.peek().addMethodCall(vic.expression());
            }
        }
    }
    
    private static boolean hasMethodCall(JavaParser.ExpressionContext exp) {
        for(JavaParser.ExpressionContext e : exp.expression()) {
            if(e.methodCall() != null) {
                return true;
            }
            
            return hasMethodCall(e);
        }
        
        return false;
    }
    
    private static void addMethodsFromExpressionList(List<JavaParser.ExpressionContext> list) {
        DeadlockFunction mdf = methodStack.peek();
        
        for(JavaParser.ExpressionContext exp : list) {
            if(exp.methodCall() != null || hasMethodCall(exp)) {
                mdf.addMethodCall(exp);
            }
        }
    }
    
    private static Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> getMethodMetadata(JavaParser.MethodDeclarationContext ctx, DeadlockFunction method) {
        Integer type = getTypeId(ctx.typeTypeOrVoid().getText(), currentCompleteFileClassName);
        Pair<List<Integer>, Map<Long, Integer>> params = getMethodParameterTypes(ctx.formalParameters(), method);
        
        return new Pair<>(type, params);
    }
    
    private static Pair<List<Integer>, Map<Long, Integer>> getMethodParameterTypes(JavaParser.FormalParametersContext ctx, DeadlockFunction method) {
        Map<Long, Integer> params = new HashMap<>();
        List<Integer> pTypes = new LinkedList<>();
        
        JavaParser.FormalParameterListContext pList = ctx.formalParameterList();
        if(pList != null) {
            for(JavaParser.FormalParameterContext fp : pList.formalParameter()) {
                String tt = getFullTypeText(fp.typeType().getText(), fp.variableDeclaratorId().getText());
                int typeId = getTypeId(tt, currentCompleteFileClassName);
                pTypes.add(typeId);
                
                Long val = method.addLocalVariable(typeId, fp.variableDeclaratorId().IDENTIFIER().getText());
                params.put(val, typeId);
            }
            
            if(pList.lastFormalParameter() != null) {
                JavaParser.LastFormalParameterContext lfp = pList.lastFormalParameter();
                
                String tt = getFullTypeText(lfp.typeType().getText(), lfp.variableDeclaratorId().getText());
                int typeId = getTypeId(tt, currentCompleteFileClassName);
                pTypes.add(typeId);
                
                Long val = method.addLocalVariable(typeId, lfp.variableDeclaratorId().IDENTIFIER().getText());
                params.put(val, typeId);
                
                method.setEllipsis(true);
            }
        }
        
        return new Pair<>(pTypes, params);
    }
    
    private static int countOccurrences(String haystack, char needle) {
        int count = 0;
        for (int i=0; i < haystack.length(); i++) {
            if (haystack.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }
    
    private static String getFullTypeText(String typeText, String curText) {
        String tt = typeText;
            
        int count = countOccurrences(curText, '[');
        for(int i = 0; i < count; i++) {
            tt += "[]";
        }
        
        return tt;
    }
    
    private static void processFieldVariableDeclarations(String typeText, List<JavaParser.VariableDeclaratorContext> vdList) {
        for(JavaParser.VariableDeclaratorContext vd : vdList) {
            JavaParser.VariableDeclaratorIdContext vi = vd.variableDeclaratorId();
            String tt = getFullTypeText(typeText, vi.getText());
            int type = getTypeId(tt, currentCompleteFileClassName);
            
            currentClass.addFieldVariable(type, vi.IDENTIFIER().getText());
        }
    }
    
    private static void processLocalVariableDeclarations(String typeText, List<JavaParser.VariableDeclaratorContext> vdList, DeadlockFunction method) {
        for(JavaParser.VariableDeclaratorContext vd : vdList) {
            processLocalVariableDeclaratorId(typeText, vd.variableDeclaratorId(), method);
        }
    }
    
    private static void processLocalVariableDeclaratorId(String typeText, JavaParser.VariableDeclaratorIdContext vi, DeadlockFunction method) {
        String tt = getFullTypeText(typeText, vi.getText());    
        int type = getTypeId(tt, currentCompleteFileClassName);
        
        method.addLocalVariable(type, vi.IDENTIFIER().getText());
    }
    
    private static void processLocalVariableDeclaratorId(String typeText, String identifier, DeadlockFunction method) {
        int type = getTypeId(typeText, currentCompleteFileClassName);
        
        method.addLocalVariable(type, identifier);
    }
    
    private static Integer getTypeId(String type, String fileClass) {
        Integer t = runningTypeId.getAndIncrement();
        volatileDataTypes.put(t, new Pair<>(type, fileClass));
        
        return t;
    }
    
    private static void processVariableDeclarations(boolean isFieldVar, String typeText, List<JavaParser.VariableDeclaratorContext> vdList) {
        if(typeText.contains("Lock")) {
            for(JavaParser.VariableDeclaratorContext vd : vdList) {
                String refLock = null;

                JavaParser.VariableInitializerContext vi = vd.variableInitializer();
                if(vi != null) {
                    refLock = captureInitializer(vi.expression());
                }

                processLock(typeText, vd.variableDeclaratorId().getText(), refLock);
            }
        }
        
        if(isFieldVar) processFieldVariableDeclarations(typeText, vdList);
        else processLocalVariableDeclarations(typeText, vdList, methodStack.peek());
    }
    
    private static Pair<String, String> captureLockNameAndReference(JavaParser.ExpressionContext ctx) {
        if(ctx == null) return null;
        
        String name, reference;
        if(ctx.getChildCount() == 3 && ctx.getChild(1).getText().contains("=")) {
            JavaParser.ExpressionContext c1 = ctx.expression(0), c2 = ctx.expression(1);
            
            if(c1 != null && c2 != null) {
                if(c2.getText().contains("Lock(")) {     // this is a lock initializer
                    reference = captureInitializer(c2);
                    
                    if(c1.primary() != null) {
                        name = c1.primary().IDENTIFIER().getText();
                    } else {
                        while(c1.expression() != null && c1.expression().size() > 0)
                            c1 = c1.expression(0);

                        if(c1.primary() != null && c1.primary().IDENTIFIER() != null) name = c1.primary().IDENTIFIER().getText();
                        else name = "_name";
                    }
                    
                    return new Pair<>(name, reference);
                }
            }
        }
        
        return null;
    }
    
    private static String captureInitializer(JavaParser.ExpressionContext expr) {
        if(expr.getChildCount() > 2 && expr.getChild(2) instanceof JavaParser.MethodCallContext) {
            expr = (JavaParser.ExpressionContext) expr.getChild(0);
            
            if(expr.primary() != null) {
                return expr.primary().IDENTIFIER().getText();
            }
        }
        
        return "";
    }
    
    private static void processLock(String typeText, String name, String reference) {
        boolean isRead = typeText.contains("Read");
        boolean isWrite = typeText.contains("Write");

        String lockName = DeadlockStorage.getCanonClassName(currentClass) + "." + name;
        String  refName = DeadlockStorage.getCanonClassName(currentClass) + "." + reference;
        
        //System.out.println("Parsing lock : '" + typeText + "' name: '" + lockName + "' ref: '" + refName + "'");
        
        if(isRead && isWrite) {
            DeadlockLock rwLock = instanceNewLock(lockName);
            
            String queued;
            queued = readLockQueue.remove(lockName);
            if(queued != null) {
                Locks.put(queued, rwLock);
            } else {
                readLockWaitingSet.add(lockName);
            }
            
            queued = writeLockQueue.remove(lockName);
            if(queued != null) {
                Locks.put(queued, rwLock);
            } else {
                writeLockWaitingSet.add(lockName);
            }
            
            ReadWriteLocks.put(lockName, rwLock);
        } else if(isRead) {
            if(reference != null) {
                if(!readLockWaitingSet.remove(refName)) {
                    readLockQueue.put(refName, lockName);
                } else {
                    Locks.put(lockName, ReadWriteLocks.get(refName));
                }
            } else {
                Locks.put(lockName, null);
            }
        } else if(isWrite) {
            if(reference != null) {
                if(!writeLockWaitingSet.remove(refName)) {
                    writeLockQueue.put(refName, lockName);
                } else {
                    Locks.put(lockName, ReadWriteLocks.get(refName));
                }
            } else {
                Locks.put(lockName, null);
            }
        } else {
            Locks.put(lockName, instanceNewLock(lockName));
        }
    }
    
    private static DeadlockLock instanceNewLock(String lockName) {
        return new DeadlockLock(runningId.getAndIncrement(), lockName);
    }
    
    private static DeadlockClass getPublicClass(String packageName, String className) {
        DeadlockClass mdc = PublicClasses.get(packageName).get(className);
        
        //if(mdc == null) System.out.println("FAILED TO FIND PUBLIC '" + className + "' @ '" + packageName + "'");
        return mdc;
    }
    
    private static DeadlockClass getPrivateClass(String packageName, String className) {
        //System.out.println("trying " + packageName + " on " + className);
        Map<String, DeadlockClass> m = PrivateClasses.get(packageName);
        DeadlockClass mdc = (m != null) ? m.get(className) : null;
        
        //if(mdc == null) System.out.println("FAILED TO FIND PRIVATE '" + className + "' @ '" + packageName + "'");
        return mdc;
    }
    
    private static List<DeadlockClass> getAllPrivateClassesWithin(String treeName, Map<String, DeadlockClass> privateMap) {
        List<DeadlockClass> list = new LinkedList<>();
        
        if (privateMap != null) {
            for(Entry<String, DeadlockClass> m : privateMap.entrySet()) {
                if(m.getKey().startsWith(treeName)) {
                    list.add(m.getValue());
                }
            }
        }
        
        return list;
    }
    
    private static boolean isEnumClass(String packageName, String className) {
        DeadlockClass mdc = getPrivateClass(packageName, className);
        if(mdc != null) {
            return mdc.isEnum();
        }
        
        return false;
    }
    
    private static void parseImportClass(DeadlockClass mdc) {
        for(String s : mdc.getImportNames()) {
            Pair<String, String> p = DeadlockStorage.locateClassPath(s);
            if(p != null) {
                String packageName = p.left;
                String className = p.right;

                Map<String, DeadlockClass> m = PublicClasses.get(packageName);

                if(m != null) {
                    mdc.removeImport(s);    // changing full names for class name

                    if(!className.contentEquals("*")) {
                        DeadlockClass importedClass = getPublicClass(packageName, className);
                        mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
                    } else {
                        for(DeadlockClass packClass : m.values()) {
                            if (mdc != packClass) {
                                mdc.updateImport(packClass.getPackageName() + packClass.getPathName(), s, packClass);
                            }
                        }
                    }
                } else {
                    //System.out.println("\n\nfailed finding " + s + " on PUBLIC");
                    //check private imports in case of failure
                    
                    if(!isEnumClass(packageName, className)) {
                        int idx = className.lastIndexOf('*');
                        if(idx != -1) {
                            s = s.substring(0, idx);

                            for(DeadlockClass packClass : getAllPrivateClassesWithin(className.substring(0, idx - 1), PrivateClasses.get(packageName))) {
                                mdc.updateImport(packClass.getPackageName() + packClass.getPathName(), s, packClass);
                            }
                        } else {
                            DeadlockClass importedClass = getPrivateClass(packageName, className);
                            if(importedClass != null) {
                                mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
                            }
                        }
                    } else {
                        DeadlockClass importedClass = getPrivateClass(packageName, className);
                        mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
                    }
                }
            }
        }
    }
    
    private static void parseImportClasses() {
        for(Map<String, DeadlockClass> mdp : PublicClasses.values()) {
            for(DeadlockClass mdc : mdp.values()) {
                parseImportClass(mdc);
            }
        }
        
        for(Entry<String, Map<String, DeadlockClass>> e : PrivateClasses.entrySet()) {
            String pc = e.getKey();
            
            Pair<String, String> p = DeadlockStorage.locateClassPath(pc);
            String packName = p.left;
            String className = p.right;
            
            DeadlockClass mdc = PublicClasses.get(packName).get(className);
            
            for(DeadlockClass c : e.getValue().values()) {
                for(Pair<String, DeadlockClass> s: mdc.getImports()) {
                    if(s.right != null) c.addImport(DeadlockStorage.getCanonClassName(s.right));
                    else c.addImport(s.left);
                }
                
                parseImportClass(c);
            }
        }
    }
    
    private static void parseSuperClasses(Map<String, Map<String, DeadlockClass>> classes) {
        for(Map<String, DeadlockClass> m : classes.values()) {
            for(DeadlockClass mdc : m.values()) {
                DeadlockClass mdc2 = DeadlockStorage.locateClass(DeadlockStorage.getNameFromCanonClass(mdc), mdc);
                
                List<String> superNames = mdc.getSuperNameList();
                for(String supName : superNames) {
                    DeadlockClass parent = mdc.getParent();
                    if(parent != null && mdc2.isInterface() && supName.contentEquals(parent.getName())) {
                        List<DeadlockClass> list = InheritanceTree.get(mdc2);
                        if(list != null) {
                            list.add(parent);
                        }
                    } else {
                        DeadlockClass sup = mdc.getImport(supName);
                        if (mdc2 != sup && sup != null) {
                            mdc2.addSuper(sup);

                            List<DeadlockClass> list = InheritanceTree.get(mdc2);
                            if(list != null) {
                                list.add(sup);
                            }

                            //if(sup == null) System.out.println("NULL SUPER '" + superName + "' FOR '" + mdc.getName() + "'");
                        }
                    }
                }
                
                DeadlockClass parent = mdc2.getParent();
                if(parent != null) {
                    List<DeadlockClass> list = InheritanceTree.get(parent);
                    if(list != null) {
                        list.add(mdc2);
                    }
                }
            }
        }
    }
    
    private static String parseWrappedType(String s) {
        int idx = s.indexOf("extends");     // assumes the extended element
        if(idx > -1) {
            return s.substring(idx + 7);
        }
        
        return s;
    }
    
    private static List<String> getWrappedTypes(String type) {
        List<String> ret = new LinkedList<>();
        
        int st = type.indexOf('<') + 1, en = 0;
        int c = 1;
        for(int i = st; i < type.length(); i++) {
            char ch = type.charAt(i);

            if(ch == ',') {
                if(c == 1) {
                    ret.add(parseWrappedType(type.substring(st, i)));
                    st = i + 1;
                }
            } else if(ch == '<') {
                c++;
            } else if(ch == '>') {
                c--;
                en = i;
            }
        }
        
        if(st == en) return null;
        
        ret.add(type.substring(st, en));
        return ret;
    }
    
    private static Integer filterDataType(Integer ret) {
        Integer e = ElementalDataTypes.get(ret);
        if(e != null) ret = e;
        
        return ret;
    }
    
    private static Integer fetchDataType(String type, DeadlockClass pc) {
        List<Integer> compoundType = new LinkedList<>();
        String t = type;
        
        Integer ret = -2;
        DeadlockClass targetClass;     //search for class data type
        
        int idx = type.indexOf('[');
        int c = 0;
        if(idx == -1) {
            List<String> wrapped = getWrappedTypes(type);
            
            if(wrapped != null) {
                for(String s : wrapped) {
                    compoundType.add(fetchDataType(s, pc));
                }
            }
        } else {
            c = countOccurrences(type, '[');
            
            type = type.substring(0, idx);
            t = type;
        }
        
        int en = type.indexOf('<');
        if(en != -1) t = type.substring(0, en);
        
        if (IgnoredTypes.isDataTypeIgnored(t)) {
            return BasicDataTypes.get("Object");
        }
        
        switch (DeadlockAbstractType.getValue(t)) {
            case LOCK:
                return ElementalTypes[5];
                
            case SCRIPT:
                return ElementalTypes[6];
        }
        
        try {
            targetClass = pc.getImport(t);
            if (targetClass == null) {
                String path = DeadlockStorage.getPublicPackageName(pc);
                targetClass = PublicClasses.get(path).get(t);
            }
        } catch(NullPointerException e) {
            if (pc != null) System.out.println("EXCEPTION ON " + t + " ON SRC " + pc.getPackageName() + pc.getPathName());
            targetClass = null;
        }
        
        if(targetClass != null) {
            Integer classId = runningTypeId.get();
            String nameChanged = "";
            if(c > 0) {
                for(int i = 0; i <= c; i++) {
                    ret = BasicDataTypes.get(targetClass.getPackageName() + targetClass.getName() + nameChanged);
                    if(ret == null) {
                        ret = runningTypeId.getAndIncrement();
                        BasicDataTypes.put(targetClass.getPackageName() + targetClass.getName() + nameChanged, ret);
                    }
                    
                    nameChanged += "[]";
                }
            }

            ret = ClassDataTypes.get(targetClass);
            if(ret == null) {
                ret = classId;
                ClassDataTypes.put(targetClass, ret);
            }
            
            if(!compoundType.isEmpty()) {
                compoundType.add(ret);

                ret = CompoundDataTypes.get(compoundType);
                if(ret == null) {
                    ret = runningTypeId.getAndIncrement();
                    //CompoundDataNames.put(ret, type);

                    CompoundDataTypes.put(compoundType, ret);
                }
            }
        } else if(c > 0) {
            for(int i = 0; i < c; i++) {
                t += "[]";
            }

            ret = BasicDataTypes.get(t);
            if(ret == null) {
                ret = runningTypeId.getAndIncrement();
                BasicDataTypes.put(t, ret);
            }
        } else {
            if(!compoundType.isEmpty()) {
                compoundType.add(ret);

                ret = CompoundDataTypes.get(compoundType);
                if(ret == null) {
                    ret = runningTypeId.getAndIncrement();
                    //CompoundDataNames.put(ret, type);
                    
                    CompoundDataTypes.put(compoundType, ret);
                }
            }
        }
        
        return ret;
    }
    
    private static Integer parseDataType(Integer volatileType) {
        if(volatileType <= 0 && volatileType >= -1) {
            return volatileType;
        }
        
        Pair<String, String> p = volatileDataTypes.get(volatileType);
        String type = p.left;
        if(type.contentEquals("void")) return -2;
        
        Integer ret = fetchDataType(type, DeadlockStorage.locateClass(p.right));
        return ret;
    }
    
    private static void updateFunctionReferences(DeadlockFunction f) {
        f.setReturn(parseDataType(f.getReturn()));
                    
        List<Integer> pList = f.getParameters();
        for(int i = 0; i < pList.size(); i++) {
            f.updateParameter(i, parseDataType(pList.get(i)));
        }

        for(Entry<Long, List<Integer>> lv : f.getVolatileLocalVariables().entrySet()) {
            List<Integer> localList = lv.getValue();
            Set<Integer> localTypes = new HashSet<>();

            for(int i = 0; i < localList.size(); i++) {
                Integer type = parseDataType(localList.get(i));

                if(!localTypes.contains(type)) {
                    localTypes.add(type);
                }
            }
            
            f.updateLocalVariable(lv.getKey(), localTypes);
        }

        f.clearVolatileLocalVariables();

        for(Entry<Long, Integer> pv : f.getParameterVariables().entrySet()) {
            f.updateParameterVariable(pv.getKey(), parseDataType(pv.getValue()));
        }
    }
    
    private static void updatePackageReferences(Map<String, Map<String, DeadlockClass>> packageClasses) {
        for(Map<String, DeadlockClass> m : packageClasses.values()) {
            for(DeadlockClass mdc : m.values()) {
                for(Entry<String, Integer> e : mdc.getFieldVariables().entrySet()) {
                    mdc.updateFieldVariable(e.getKey(), parseDataType(e.getValue()));
                }
                
                for(DeadlockFunction f : mdc.getMethods()) {
                    updateFunctionReferences(f);
                }
            }
        }
        
        for(Map<String, DeadlockClass> m : packageClasses.values()) {
            for(DeadlockClass mdc : m.values()) {
                mdc.generateMaskedTypeSet();
            }
        }
    }
    
    private static void parseDataTypes() {
        runningTypeId.set(0);   // id 0 reserved for sync locks
        
        instantiateElementalDataTypes();
        generateElementalDataTypes();
        
        for(Map<String, DeadlockClass> m : PublicClasses.values()) {
            for(DeadlockClass c : m.values()) {
                ClassDataTypes.put(c, runningTypeId.getAndIncrement());
            }
        }
        
        for(Map<String, DeadlockClass> m : PrivateClasses.values()) {
            for(DeadlockClass c : m.values()) {
                ClassDataTypes.put(c, runningTypeId.getAndIncrement());
            }
        }
        
        for(Entry<Integer, Pair<DeadlockClass, Integer>> e : volatileMaskedTypes.entrySet()) {
            if(e != null) {
                e.getValue().left.updateMaskedType(e.getValue().right, parseDataType(e.getKey()));
            }
        }
        
        updatePackageReferences(PublicClasses);
        updatePackageReferences(PrivateClasses);
    }
    
    private static void linkElementalDataTypes(String link, String target) {
        Integer typeId = runningTypeId.getAndIncrement();
        
        BasicDataTypes.put(link, typeId);
        ElementalDataTypes.put(typeId, BasicDataTypes.get(target));
        LinkedDataNames.put(typeId, target);
    }
    
    private static void instantiateElementalDataType(String s) {
        BasicDataTypes.put(s, runningTypeId.getAndIncrement());
    }
    
    private static void instantiateIgnoredDataTypes() {
        Integer start = runningTypeId.get();
        
        for(String s : IgnoredTypes.getIgnoredTypes()) {
            instantiateElementalDataType(s);
        }
        
        storage.setIgnoredDataRange(new Pair<>(start, runningTypeId.get()));
    }
    
    private static void generateReflectedDataTypes() {
        for(ReflectedTypes mrt : ReflectedTypes.getReflectedTypes()) {
            Integer mrtId = BasicDataTypes.get(mrt.getName());
            Integer mrtDefReturn = BasicDataTypes.get(mrt.getDefaultReturn());
            Map<String, Integer> mrtMethods = new HashMap<>();
            
            for(Entry<String, String> emrt : mrt.getMethodReturns().entrySet()) {
                mrtMethods.put(emrt.getKey(), BasicDataTypes.get(emrt.getValue()));
            }
            
            ReflectedClasses.put(mrtId, new Pair<>(mrtDefReturn, mrtMethods));
        }
    }
    
    private static void instantiateElementalDataTypes() {
        instantiateElementalDataType(syncLockTypeName);
        
        // basic language types
        instantiateElementalDataType("int");
        instantiateElementalDataType("float");
        instantiateElementalDataType("char");
        instantiateElementalDataType("String");
        instantiateElementalDataType("boolean");
        instantiateElementalDataType("null");
        instantiateElementalDataType("byte");
        
        instantiateElementalDataType("int[]");
        instantiateElementalDataType("long[]");
        
        // basic abstract data types
        instantiateElementalDataType("Set");
        instantiateElementalDataType("List");
        instantiateElementalDataType("Map");
        instantiateElementalDataType("Lock");
        instantiateElementalDataType("Invocable");
        
        instantiateIgnoredDataTypes();
    }
    
    private static void generateElementalDataTypes() {
        ElementalTypes[0] = BasicDataTypes.get("int");
        ElementalTypes[1] = BasicDataTypes.get("int");    // float, but let numbers have the same data reference for the sake of simplicity
        ElementalTypes[2] = BasicDataTypes.get("char");
        ElementalTypes[3] = BasicDataTypes.get("String");
        ElementalTypes[4] = BasicDataTypes.get("boolean");
        ElementalTypes[5] = BasicDataTypes.get("Lock");
        ElementalTypes[6] = BasicDataTypes.get("Invocable");
        ElementalTypes[7] = BasicDataTypes.get("null");
        
        for(Pair<String, String> p : LinkedTypes.getLinkedTypes()) {
            linkElementalDataTypes(p.left, p.right);
        }
    }
    
    private static void generateDereferencedDataTypes() {
        for(Entry<String, Integer> e : BasicDataTypes.entrySet()) {
            String s = e.getKey();
            
            int c = countOccurrences(s, '[');
            if(c > 0) {
                DeadlockClass targetClass = DeadlockStorage.locatePublicClass(s.substring(0, s.indexOf('[')), null);
                if (targetClass != null) {
                    String nameChanged = "";
                    for(int i = 0; i < c; i++) {
                        nameChanged += "[]";
                    }

                    if(nameChanged.length() > 0) {
                        Integer i = BasicDataTypes.get(targetClass.getPackageName() + targetClass.getName() + nameChanged);
                        if(i == null) {
                            i = runningTypeId.getAndIncrement();
                            BasicDataTypes.put(targetClass.getPackageName() + targetClass.getName() + nameChanged, i);
                        }
                    }
                } else {
                    Integer i = BasicDataTypes.get(s);
                    if(i == null) {
                        i = runningTypeId.getAndIncrement();
                        BasicDataTypes.put(s, i);
                    }
                }
            }
        }
    }
    
    public static void solveRunnableFunctions() {
        for(Entry<DeadlockFunction, Boolean> runMdf : RunnableFunctions.entrySet()) {
            DeadlockFunction mdf = runMdf.getKey();
            updateFunctionReferences(mdf);
            
            DeadlockClass mdc = mdf.getSourceClass();
            if(runMdf.getValue() || mdc.getSuperNameList().contains("Runnable")) {
                RunnableMethods.add(mdf);
            }
            
            mdc.addClassMethod(mdf);
        }
    }
    
    private static void referenceCustomClasses() {
        for (DeadlockClass c : customClasses) {
            DeadlockClass sup = DeadlockStorage.locateClass(c.getName(), c);
            c.addSuper(sup);
        }
        customClasses.clear();
    }
    
    private static void referenceReadWriteLocks() {
        for (Entry<String, DeadlockLock> e : ReadWriteLocks.entrySet()) {
            Locks.put(e.getKey(), e.getValue());
        }
    }
    
    public static DeadlockStorage compileProjectData() {
        //System.out.println(storage);
        
        parseImportClasses();
        
        parseSuperClasses(PublicClasses);
        parseSuperClasses(PrivateClasses);
        
        Map<String, Map<String, DeadlockClass>> m = new HashMap<>();
        Map<String, DeadlockClass> n = new HashMap<>();
        
        m.put("", n);
        for(DeadlockClass c : customClasses) {
            n.put(c.getName(), c);
        }
        parseSuperClasses(m);
        
        referenceCustomClasses();
        referenceReadWriteLocks();
        
        /*
        for(Entry<Integer, Pair<String, String>> v : volatileDataTypes.entrySet()) {
            System.out.println(v.getKey() + " : " + v.getValue());
        }
        
        for(Map<String, DeadlockClass> m : PublicClasses.values()) {
            for(DeadlockClass mdc : m.values()) {
                System.out.println(mdc);
            }
        }
        */
        
        parseDataTypes();
        fetchDataType("Set<Object>", null);
        
        generateDereferencedDataTypes();
        generateReflectedDataTypes();
        
        solveRunnableFunctions();
        
        return storage;
    }
}
