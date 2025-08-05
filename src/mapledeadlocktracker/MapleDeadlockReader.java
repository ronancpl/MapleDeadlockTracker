/*
    This file is part of the MapleQuestAdvisor planning tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker;

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
import mapledeadlocktracker.containers.MapleDeadlockClass;
import mapledeadlocktracker.containers.MapleDeadlockClass.MapleDeadlockClassType;
import mapledeadlocktracker.containers.MapleDeadlockEnum;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockLock;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.containers.Pair;
import mapledeadlocktracker.strings.MapleIgnoredTypes;
import mapledeadlocktracker.strings.MapleLinkedTypes;
import mapledeadlocktracker.strings.MapleReflectedTypes;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockReader extends JavaParserBaseListener {
    private static int data[][][][][][][] = new int[7][7][7][7][7][7][7];
    private static Pair<Integer, Integer> date[][][][][][][] = new Pair[7][7][7][7][7][7][7];
    private static MapleDeadlockStorage storage = new MapleDeadlockStorage();
    private static String syncLockTypeName = "SyncLock";
    
    // ---- cached storage fields ----
    private static Map<String, Map<String, MapleDeadlockClass>> maplePublicPackages = storage.getPublicClasses();
    private static Map<String, Map<String, MapleDeadlockClass>> maplePrivateClasses = storage.getPrivateClasses();
    
    private static Map<String, MapleDeadlockLock> mapleLocks = storage.getLocks();
    private static Map<String, MapleDeadlockLock> mapleReadWriteLocks = storage.getReadWriteLocks();
    
    private static Map<MapleDeadlockClass, Integer> mapleClassDataTypes = storage.getClassDataTypes();
    private static Map<List<Integer>, Integer> mapleCompoundDataTypes = storage.getCompoundDataTypes();
    private static Map<String, Integer> mapleBasicDataTypes = storage.getBasicDataTypes();
    private static Map<Integer, Integer> mapleElementalDataTypes = storage.getElementalDataTypes();
    private static Integer[] mapleElementalTypes = storage.getElementalTypes();
    
    private static Map<MapleDeadlockClass, List<MapleDeadlockClass>> mapleInheritanceTree = storage.getInheritanceTree();
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> mapleReflectedClasses = storage.getReflectedClasses();
    
    private static Map<MapleDeadlockFunction, Boolean> mapleRunnableFunctions = new HashMap<>();
    private static List<MapleDeadlockFunction> mapleRunnableMethods = storage.getRunnableMethods();
    
    //private static Map<Integer, String> mapleCompoundDataNames = new HashMap();   // test purposes only
    
    // ---- volatile fields ----
    
    private static AtomicInteger runningId = new AtomicInteger(0);
    private static AtomicInteger runningSyncLockId = new AtomicInteger(0);
    private static AtomicInteger runningTypeId = new AtomicInteger(1);  // volatile id 0 reserved for sync locks
    private static AtomicInteger runningMethodCallCount = new AtomicInteger(0);
    
    private static Stack<Integer> methodCallCountStack = new Stack();
    private static Stack<MapleDeadlockFunction> methodStack = new Stack();
    private static List<MapleDeadlockClass> classStack = new ArrayList();
    private static Stack<Integer> syncLockStack = new Stack();
    
    private static Set<String> readLockWaitingSet = new HashSet();
    private static Set<String> writeLockWaitingSet = new HashSet();
    
    private static Map<String, String> readLockQueue = new HashMap();
    private static Map<String, String> writeLockQueue = new HashMap();
    
    private static Map<Integer, String> mapleLinkedDataNames = new HashMap();
    
    private static List<String> currentImportList = new ArrayList<>();
    private static String currentPackageName;
    private static String currentCompleteFileClassName;
    private static MapleDeadlockClass currentClass = null;
    private static boolean currentAbstract = false;
    
    private static Map<Integer, Pair<MapleDeadlockClass, Integer>> volatileMaskedTypes = new HashMap<>();
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
            MapleDeadlockFunction method = new MapleDeadlockFunction("class", currentClass, null, false);
            
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
        
        for(MapleDeadlockClass mdc : classStack) {
            path += mdc.getName() + ".";
        }
        path += className;
        
        return path;
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
            currentClass = new MapleDeadlockClass(MapleDeadlockClassType.CLASS, className, currentCompleteFileClassName, getPathName(className), superNames, isAbstract, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new MapleDeadlockClass(MapleDeadlockClassType.CLASS, className, currentPackageName, getPathName(className), superNames, isAbstract, null);
        }
        
        mapleInheritanceTree.put(currentClass, new LinkedList());
        
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
            
            if(maplePublicPackages.containsKey(currentPackageName)) {
                maplePublicPackages.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                maplePublicPackages.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(maplePrivateClasses.containsKey(fcn)) {
                maplePrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                maplePrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            MapleDeadlockClass mdc = currentClass;
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
            currentClass = new MapleDeadlockEnum(className, currentCompleteFileClassName, getPathName(className), superNames, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new MapleDeadlockEnum(className, currentPackageName, getPathName(className), superNames, null);
        }
        
        mapleInheritanceTree.put(currentClass, new LinkedList());
    }
    
    @Override
    public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        if(classStack.isEmpty()) {
            for(String s : currentImportList) {
                currentClass.addImport(s);
            }
            
            if(maplePublicPackages.containsKey(currentPackageName)) {
                maplePublicPackages.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                maplePublicPackages.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(maplePrivateClasses.containsKey(fcn)) {
                maplePrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                maplePrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            MapleDeadlockClass mdc = currentClass;
            currentClass = classStack.remove(classStack.size() - 1);
            currentClass.addPrivateClass(mdc.getName(), mdc);
        }
    }
    
    @Override
    public void enterEnumConstant(JavaParser.EnumConstantContext ctx) {
        MapleDeadlockEnum mde = (MapleDeadlockEnum) currentClass;
        mde.addEnumItem(ctx.IDENTIFIER().getText());
    }
    
    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        String className = ctx.IDENTIFIER().getText();
        
        JavaParser.TypeListContext extended = ctx.typeList();    // from rule "(EXTENDS typeList)?"
        List<String> superNames = getExtendedImplementedList(null, extended);
        
        if(currentClass != null) {
            classStack.add(currentClass);
            currentClass = new MapleDeadlockClass(MapleDeadlockClassType.INTERFACE, className, currentCompleteFileClassName, getPathName(className), superNames, true, currentClass);
        } else {
            currentCompleteFileClassName = currentPackageName + className;
            currentClass = new MapleDeadlockClass(MapleDeadlockClassType.INTERFACE, className, currentPackageName, getPathName(className), superNames, true, null);
        }
        
        mapleInheritanceTree.put(currentClass, new LinkedList());
    }
    
    @Override
    public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        if(classStack.isEmpty()) {
            for(String s : currentImportList) {
                currentClass.addImport(s);
            }
            
            if(maplePublicPackages.containsKey(currentPackageName)) {
                maplePublicPackages.get(currentPackageName).put(currentClass.getPathName(), currentClass);
            } else {
                maplePublicPackages.put(currentPackageName, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            currentClass = null;
        } else {
            String fcn = currentCompleteFileClassName;
            
            if(maplePrivateClasses.containsKey(fcn)) {
                maplePrivateClasses.get(fcn).put(currentClass.getPathName(), currentClass);
            } else {
                maplePrivateClasses.put(fcn, newPackageClass(currentClass.getPathName(), currentClass));
            }
            
            MapleDeadlockClass mdc = currentClass;
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
    
    private static Map<String, MapleDeadlockClass> newPackageClass(String s, MapleDeadlockClass c) {
        Map<String, MapleDeadlockClass> m = new HashMap<>();
        m.put(s, c);
        
        return m;
    }
    
    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        String methodName = ctx.IDENTIFIER().getText();
        
        MapleDeadlockFunction method = new MapleDeadlockFunction(methodName, currentClass, methodStack.isEmpty() ? null : methodStack.peek(), currentAbstract);
        Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> retParamTypes = getMethodMetadata(ctx, method);
        
        method.setMethodMetadata(retParamTypes.left, retParamTypes.right.left, retParamTypes.right.right);
        methodStack.add(method);
        
        methodCallCountStack.add(runningMethodCallCount.get());
        runningMethodCallCount.set(0);
    }
    
    @Override
    public void exitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        MapleDeadlockFunction method = methodStack.pop();
        
        String methodName = method.getName();
        if(methodName.contentEquals("run") && method.getParameters().isEmpty()) {
            // book-keeping possible Runnable functions to be dealt with later on the parsing
            
            mapleRunnableFunctions.put(method, !methodStack.isEmpty());
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
        
        MapleDeadlockFunction method = new MapleDeadlockFunction(methodName, currentClass, null, false);
        Pair<List<Integer>, Map<Long, Integer>> pTypes = getMethodParameterTypes(ctx.formalParameters(), method);
        
        method.setMethodMetadata(-1, pTypes.left, pTypes.right);
        methodStack.add(method);
    }
    
    @Override
    public void exitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        MapleDeadlockFunction method = methodStack.pop();
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
        MapleDeadlockFunction mdf = methodStack.peek();
        
        for(JavaParser.ExpressionContext exp : list) {
            if(exp.methodCall() != null || hasMethodCall(exp)) {
                mdf.addMethodCall(exp);
            }
        }
    }
    
    private static Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> getMethodMetadata(JavaParser.MethodDeclarationContext ctx, MapleDeadlockFunction method) {
        Integer type = getTypeId(ctx.typeTypeOrVoid().getText(), currentCompleteFileClassName);
        Pair<List<Integer>, Map<Long, Integer>> params = getMethodParameterTypes(ctx.formalParameters(), method);
        
        return new Pair<>(type, params);
    }
    
    private static Pair<List<Integer>, Map<Long, Integer>> getMethodParameterTypes(JavaParser.FormalParametersContext ctx, MapleDeadlockFunction method) {
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
    
    private static void processLocalVariableDeclarations(String typeText, List<JavaParser.VariableDeclaratorContext> vdList, MapleDeadlockFunction method) {
        for(JavaParser.VariableDeclaratorContext vd : vdList) {
            processLocalVariableDeclaratorId(typeText, vd.variableDeclaratorId(), method);
        }
    }
    
    private static void processLocalVariableDeclaratorId(String typeText, JavaParser.VariableDeclaratorIdContext vi, MapleDeadlockFunction method) {
        String tt = getFullTypeText(typeText, vi.getText());    
        int type = getTypeId(tt, currentCompleteFileClassName);
        
        method.addLocalVariable(type, vi.IDENTIFIER().getText());
    }
    
    private static void processLocalVariableDeclaratorId(String typeText, String identifier, MapleDeadlockFunction method) {
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
        
        return null;
    }
    
    private static Byte getLockStatementType(String stmt) {
        switch(stmt) {
            case "unlock":
                return 0;

            case "lock":
                return 1;
            
            case "trylock":
                return 2;
                
            default:    // unknown type
                return -1;
        }
    }
    
    private static Pair<String, Byte> captureLockStatement(JavaParser.ExpressionContext ctx) {
        if(ctx != null) {
            String lockName;
            
            if(ctx.getText().contains("lock(")) {     // this is a lock/unlock statement
                lockName = captureInitializer(ctx);

                if(lockName != null) {
                    JavaParser.MethodCallContext stmt = (JavaParser.MethodCallContext) ctx.getChild(2);
                    Byte type = getLockStatementType(stmt.IDENTIFIER().getText());
                    
                    if(type > -1) {
                        return new Pair<>(lockName, type);
                    }
                }
            }
        }
        
        return null;
    }
    
    private static void processLock(String typeText, String name, String reference) {
        boolean isRead = typeText.contains("Read");
        boolean isWrite = typeText.contains("Write");

        String lockName = currentClass.getPackageName() + currentClass.getPathName() + "." + name;
        String  refName = currentClass.getPackageName() + currentClass.getPathName() + "." + reference;
        
        //System.out.println("Parsing lock : '" + typeText + "' name: '" + lockName + "' ref: '" + refName + "'");
        
        if(isRead && isWrite) {
            MapleDeadlockLock rwLock = instanceNewLock(lockName);
            
            String queued;
            queued = readLockQueue.remove(lockName);
            if(queued != null) {
                mapleLocks.put(queued, rwLock);
            } else {
                readLockWaitingSet.add(lockName);
            }
            
            queued = writeLockQueue.remove(lockName);
            if(queued != null) {
                mapleLocks.put(queued, rwLock);
            } else {
                writeLockWaitingSet.add(lockName);
            }
            
            mapleReadWriteLocks.put(lockName, rwLock);
        } else if(isRead) {
            if(reference != null) {
                if(!readLockWaitingSet.remove(refName)) {
                    readLockQueue.put(refName, lockName);
                } else {
                    mapleLocks.put(lockName, mapleReadWriteLocks.get(refName));
                }
            } else {
                mapleLocks.put(lockName, null);
            }
        } else if(isWrite) {
            if(reference != null) {
                if(!writeLockWaitingSet.remove(refName)) {
                    writeLockQueue.put(refName, lockName);
                } else {
                    mapleLocks.put(lockName, mapleReadWriteLocks.get(refName));
                }
            } else {
                mapleLocks.put(lockName, null);
            }
        } else {
            mapleLocks.put(lockName, instanceNewLock(lockName));
        }
    }
    
    private static MapleDeadlockLock instanceNewLock(String lockName) {
        return new MapleDeadlockLock(runningId.getAndIncrement(), lockName);
    }
    
    private static MapleDeadlockClass getPublicClass(String className, String packageName) {
        MapleDeadlockClass mdc = maplePublicPackages.get(packageName).get(className);
        
        //if(mdc == null) System.out.println("FAILED TO FIND PUBLIC '" + className + "' @ '" + packageName + "'");
        return mdc;
    }
    
    private static MapleDeadlockClass getPrivateClass(String packageName, String className) {
        //System.out.println("trying " + packageName + " on " + className);
        MapleDeadlockClass mdc = maplePrivateClasses.get(packageName).get(className);
        
        //if(mdc == null) System.out.println("FAILED TO FIND PRIVATE '" + className + "' @ '" + packageName + "'");
        return mdc;
    }
    
    private static List<MapleDeadlockClass> getAllPrivateClassesWithin(String treeName, Map<String, MapleDeadlockClass> privateMap) {
        List<MapleDeadlockClass> list = new LinkedList();
        
        for(Entry<String, MapleDeadlockClass> m : privateMap.entrySet()) {
            if(m.getKey().startsWith(treeName)) {
                list.add(m.getValue());
            }
        }
        
        return list;
    }
    
    private static boolean isEnumClass(String packageName, String className) {
        int idx = className.lastIndexOf('.');
        if(idx == -1) return false;
        
        MapleDeadlockClass mdc = getPrivateClass(packageName, className.substring(0, idx));
        if(mdc != null) {
            return mdc.isEnum();
        }
        
        return false;
    }
    
    private static void parseImportClass(MapleDeadlockClass mdc) {
        for(String s : mdc.getImportNames()) {
            int j = s.lastIndexOf('.');
            String packageName = s.substring(0, j + 1);
            String className = s.substring(j + 1);

            Map<String, MapleDeadlockClass> m = maplePublicPackages.get(packageName);
            
            if(m != null) {
                mdc.removeImport(s);    // changing full names for class name
                
                if(!className.contentEquals("*")) {
                    MapleDeadlockClass importedClass = getPublicClass(className, packageName);
                    mdc.updateImport(importedClass.getPathName(), s, importedClass);
                } else {
                    for(MapleDeadlockClass packClass : m.values()) {
                        mdc.updateImport(packClass.getPathName(), s, packClass);
                    }
                }
            } else {
                //System.out.println("\n\nfailed finding " + s + " on PUBLIC");
                //check private imports in case of failure
                
                Pair<String, String> privateNames = MapleDeadlockStorage.getPrivateNameParts(s);
                if(privateNames != null) {
                    packageName = privateNames.left;
                    className = privateNames.right;
                    
                    if(!isEnumClass(packageName, className)) {
                        int idx = className.lastIndexOf('*');
                        if(idx != -1) {
                            s = s.substring(0, idx);

                            for(MapleDeadlockClass packClass : getAllPrivateClassesWithin(className, maplePrivateClasses.get(packageName))) {
                                mdc.updateImport(packClass.getPathName(), s, packClass);
                            }
                        } else {
                            MapleDeadlockClass importedClass = getPrivateClass(packageName, className);
                            mdc.updateImport(importedClass.getPathName(), s, importedClass);
                        }
                    } else {
                        MapleDeadlockClass importedClass = getPrivateClass(packageName, className.substring(0, className.lastIndexOf('.')));
                        mdc.updateImport(importedClass.getPathName(), s, importedClass);
                    }
                }
            }
        }
    }
    
    private static void parseImportClasses() {
        for(Map<String, MapleDeadlockClass> mdp : maplePublicPackages.values()) {
            for(MapleDeadlockClass mdc : mdp.values()) {
                parseImportClass(mdc);
            }
        }
        
        for(Entry<String, Map<String, MapleDeadlockClass>> e : maplePrivateClasses.entrySet()) {
            String pc = e.getKey();
            
            int idx = pc.lastIndexOf('.');
            
            String packName = pc.substring(0, idx + 1);
            String className = pc.substring(idx + 1);
            
            MapleDeadlockClass mdc = maplePublicPackages.get(packName).get(className);
            if(mdc == null) System.out.println("COULD NOT FIND COMMON CLASS " + className + " @ " + packName);
            
            for(MapleDeadlockClass c : e.getValue().values()) {
                for(Pair<String, MapleDeadlockClass> s: mdc.getImports()) {
                    if(s.right != null) c.addImport(MapleDeadlockStorage.getCanonClassName(s.right));
                    else c.addImport(s.left);
                }
                
                parseImportClass(c);
            }
        }
    }
    
    private static void parseSuperClasses(Map<String, Map<String, MapleDeadlockClass>> classes) {
        for(Map<String, MapleDeadlockClass> m : classes.values()) {
            for(MapleDeadlockClass mdc : m.values()) {
                List<String> superNames = mdc.getSuperNameList();
            
                for(String supName : superNames) {
                    MapleDeadlockClass sup = MapleDeadlockStorage.locateClass(supName, mdc);
                    mdc.addSuper(sup);
                    
                    List<MapleDeadlockClass> list = mapleInheritanceTree.get(sup);
                    if(list != null) {
                        list.add(mdc);
                    }
                    
                    //if(sup == null) System.out.println("NULL SUPER '" + superName + "' FOR '" + mdc.getName() + "'");
                }
            }
        }
    }
    
    private static List<String> getWrappedTypes(String type) {
        List<String> ret = new LinkedList<>();
        
        int st = type.indexOf('<') + 1, en = 0;
        int c = 1;
        for(int i = st; i < type.length(); i++) {
            char ch = type.charAt(i);

            if(ch == ',') {
                if(c == 1) {
                    ret.add(type.substring(st, i));
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
        Integer e = mapleElementalDataTypes.get(ret);
        if(e != null) ret = e;
        
        return ret;
    }
    
    private static Integer fetchDataType(String type, MapleDeadlockClass pc) {
        List<Integer> compoundType = new LinkedList<>();
        String t = type;
        
        Integer ret = -2;
        MapleDeadlockClass targetClass;     //search for class data type
        
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

        try {
            targetClass = MapleDeadlockStorage.locateClass(t, pc);
        } catch(NullPointerException e) {
            System.out.println("EXCEPTION ON " + t + " ON SRC " + pc.getPackageName() + pc.getPathName());
            targetClass = null;
        }
        
        if(targetClass != null) {
            ret = mapleClassDataTypes.get(targetClass);
            if(ret == null) {
                ret = runningTypeId.getAndIncrement();
                mapleClassDataTypes.put(targetClass, ret);
            }
        }
        
        if(targetClass == null || c > 0) {
            if(c > 0 && targetClass != null) {
                t = MapleDeadlockStorage.getCanonClassName(targetClass);     // whole canon name, ignores whatever is inside <> operators
                
                for(int i = 0; i < c; i++) {
                    t += "[]";
                }
                
                ret = mapleBasicDataTypes.get(t);
                if(ret == null) {
                    ret = runningTypeId.getAndIncrement();
                    mapleBasicDataTypes.put(t, ret);
                }
            } else {
                boolean nameChanged = false;
                
                ret = mapleBasicDataTypes.get(t);
                if(ret != null) {
                    String linkedName = mapleLinkedDataNames.get(ret);
                    if(linkedName != null) {
                        t = linkedName;     // attribute the linked name to this type
                        nameChanged = true;
                    }
                }

                if(c > 0) {
                    for(int i = 0; i < c; i++) {
                        t += "[]";
                    }
                    
                    nameChanged = true;
                }
                
                if(nameChanged) ret = mapleBasicDataTypes.get(t);
                if(ret == null) {
                    idx = t.lastIndexOf('.');
                    if(idx != -1) {
                        // check class
                        
                        String maybeEnum = t.substring(0, idx);
                        try {
                            targetClass = MapleDeadlockStorage.locateClass(maybeEnum, pc);
                        } catch(NullPointerException e) {}
                        
                        if(targetClass != null && targetClass.isEnum()) {
                            ret = mapleElementalTypes[0];   // this is an enum variable
                            
                            mapleBasicDataTypes.put(t, ret);
                            return ret;
                        }
                        
                        t = t.substring(idx + 1);
                    }
                    
                    ret = mapleBasicDataTypes.get(t);
                    if(ret == null) {
                        ret = runningTypeId.getAndIncrement();
                        mapleBasicDataTypes.put(t, ret);
                    }
                }

                if(!compoundType.isEmpty()) {
                    compoundType.add(ret);

                    ret = mapleCompoundDataTypes.get(compoundType);
                    if(ret == null) {
                        ret = runningTypeId.getAndIncrement();
                        //mapleCompoundDataNames.put(ret, type);
                        
                        mapleCompoundDataTypes.put(compoundType, ret);
                    }
                }
            }
        } else {
            if(!compoundType.isEmpty()) {
                compoundType.add(ret);

                ret = mapleCompoundDataTypes.get(compoundType);
                if(ret == null) {
                    ret = runningTypeId.getAndIncrement();
                    //mapleCompoundDataNames.put(ret, type);
                    
                    mapleCompoundDataTypes.put(compoundType, ret);
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
        
        int index = p.right.lastIndexOf('.');
        String pPackage = p.right.substring(0, index + 1);
        String pClass = p.right.substring(index + 1);
        
        MapleDeadlockClass pc = maplePublicPackages.get(pPackage).get(pClass);  // assuming file public classes finds every implemented types
        Integer ret = fetchDataType(type, pc);
        
        return ret;
    }
    
    private static void updateFunctionReferences(MapleDeadlockFunction f) {
        f.setReturn(parseDataType(f.getReturn()));
                    
        List<Integer> pList = f.getParameters();
        for(int i = 0; i < pList.size(); i++) {
            f.updateParameter(i, parseDataType(pList.get(i)));
        }

        for(Entry<Long, List<Integer>> lv : f.getVolatileLocalVariables().entrySet()) {
            List<Integer> localList = lv.getValue();
            Integer localType = parseDataType(localList.get(0));

            if(localList.size() > 1) {
                for(int i = 1; i < localList.size(); i++) {
                    Integer otherType = parseDataType(localList.get(i));

                    if(!localType.equals(otherType)) {
                        System.out.println("[WARNING] Attributing different data types (" + localType + " vs " + otherType + ") for the same variable name '" + f.getLocalVariableName(lv.getKey()) + "' at method '" + f.getName() + "' on " + MapleDeadlockStorage.getCanonClassName(f.getSourceClass()));
                        break;
                    }
                }
            }

            f.updateLocalVariable(lv.getKey(), localType);
        }

        f.clearVolatileLocalVariables();

        for(Entry<Long, Integer> pv : f.getParameterVariables().entrySet()) {
            f.updateParameterVariable(pv.getKey(), parseDataType(pv.getValue()));
        }
    }
    
    private static void updatePackageReferences(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
        for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
            for(MapleDeadlockClass mdc : m.values()) {
                for(Entry<String, Integer> e : mdc.getFieldVariables().entrySet()) {
                    mdc.updateFieldVariable(e.getKey(), parseDataType(e.getValue()));
                }
                
                for(MapleDeadlockFunction f : mdc.getMethods()) {
                    updateFunctionReferences(f);
                }
            }
        }
        
        for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
            for(MapleDeadlockClass mdc : m.values()) {
                mdc.generateMaskedTypeSet();
            }
        }
    }
    
    private static void parseDataTypes() {
        runningTypeId.set(0);   // id 0 reserved for sync locks
        
        instantiateElementalDataTypes();
        generateElementalDataTypes();
        
        for(Map<String, MapleDeadlockClass> m : maplePublicPackages.values()) {
            for(MapleDeadlockClass c : m.values()) {
                mapleClassDataTypes.put(c, runningTypeId.getAndIncrement());
            }
        }
        
        for(Map<String, MapleDeadlockClass> m : maplePrivateClasses.values()) {
            for(MapleDeadlockClass c : m.values()) {
                mapleClassDataTypes.put(c, runningTypeId.getAndIncrement());
            }
        }
        
        for(Entry<Integer, Pair<MapleDeadlockClass, Integer>> e : volatileMaskedTypes.entrySet()) {
            if(e != null) {
                e.getValue().left.updateMaskedType(e.getValue().right, parseDataType(e.getKey()));
            }
        }
        
        updatePackageReferences(maplePublicPackages);
        updatePackageReferences(maplePrivateClasses);
    }
    
    private static void linkElementalDataTypes(String link, String target) {
        Integer typeId = runningTypeId.getAndIncrement();
        
        mapleBasicDataTypes.put(link, typeId);
        mapleElementalDataTypes.put(typeId, mapleBasicDataTypes.get(target));
        mapleLinkedDataNames.put(typeId, target);
    }
    
    private static void instantiateElementalDataType(String s) {
        mapleBasicDataTypes.put(s, runningTypeId.getAndIncrement());
    }
    
    private static void instantiateIgnoredDataTypes() {
        Integer start = runningTypeId.get();
        
        for(String s : MapleIgnoredTypes.getIgnoredTypes()) {
            instantiateElementalDataType(s);
        }
        
        storage.setIgnoredDataRange(new Pair<>(start, runningTypeId.get()));
    }
    
    private static void generateReflectedDataTypes() {
        for(MapleReflectedTypes mrt : MapleReflectedTypes.getReflectedTypes()) {
            Integer mrtId = mapleBasicDataTypes.get(mrt.getName());
            Integer mrtDefReturn = mapleBasicDataTypes.get(mrt.getDefaultReturn());
            Map<String, Integer> mrtMethods = new HashMap<>();
            
            for(Entry<String, String> emrt : mrt.getMethodReturns().entrySet()) {
                mrtMethods.put(emrt.getKey(), mapleBasicDataTypes.get(emrt.getValue()));
            }
            
            mapleReflectedClasses.put(mrtId, new Pair<>(mrtDefReturn, mrtMethods));
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
        
        instantiateIgnoredDataTypes();
    }
    
    private static void generateElementalDataTypes() {
        mapleElementalTypes[0] = mapleBasicDataTypes.get("int");
        mapleElementalTypes[1] = mapleBasicDataTypes.get("int");    // float, but let numbers have the same data reference for the sake of simplicity
        mapleElementalTypes[2] = mapleBasicDataTypes.get("char");
        mapleElementalTypes[3] = mapleBasicDataTypes.get("String");
        mapleElementalTypes[4] = mapleBasicDataTypes.get("boolean");
        mapleElementalTypes[5] = mapleBasicDataTypes.get("null");
        
        for(Pair<String, String> p : MapleLinkedTypes.getLinkedTypes()) {
            linkElementalDataTypes(p.left, p.right);
        }
    }
    
    private static void generateDereferencedDataTypes() {
        List<String> basicDataTypes = new LinkedList<>();
        List<String> classDataTypes = new LinkedList<>();
        
        for(Entry<String, Integer> e : mapleBasicDataTypes.entrySet()) {
            String s = e.getKey();
            
            int c = countOccurrences(s, '[');
            if(c > 0) {
                for(int i = 0; i < c - 1; i++) {
                    s = s.substring(0, s.lastIndexOf('['));
                    basicDataTypes.add(s);
                }
                
                s = s.substring(0, s.lastIndexOf('['));
                if(!s.contains(".")) {
                    basicDataTypes.add(s);
                } else {
                    classDataTypes.add(s);
                }
            }
        }
        
        for(String s : basicDataTypes) {
            if(mapleBasicDataTypes.get(s) == null) {
                mapleBasicDataTypes.put(s, runningTypeId.getAndIncrement());
            }
        }
        
        for(String s : classDataTypes) {
            MapleDeadlockClass mdc = MapleDeadlockStorage.locateCanonClass(s);
            
            if(mdc != null) {
                if(mapleClassDataTypes.get(mdc) == null) {
                    mapleClassDataTypes.put(mdc, runningTypeId.getAndIncrement());
                }
            } else {
                System.out.println("ERROR on locating '" + s + "'");
            }
        }
    }
    
    public static void solveRunnableFunctions() {
        for(Entry<MapleDeadlockFunction, Boolean> runMdf : mapleRunnableFunctions.entrySet()) {
            MapleDeadlockFunction mdf = runMdf.getKey();
            updateFunctionReferences(mdf);
            
            MapleDeadlockClass mdc = mdf.getSourceClass();
            if(runMdf.getValue() || mdc.getSuperNameList().contains("Runnable")) {
                mapleRunnableMethods.add(mdf);
            }
            
            mdc.addClassMethod(mdf);
        }
    }
    
    public static MapleDeadlockStorage compileProjectData() {
        //System.out.println(storage);
        
        parseImportClasses();
        
        parseSuperClasses(maplePublicPackages);
        parseSuperClasses(maplePrivateClasses);
        
        /*
        for(Entry<Integer, Pair<String, String>> v : volatileDataTypes.entrySet()) {
            System.out.println(v.getKey() + " : " + v.getValue());
        }
        
        for(Map<String, MapleDeadlockClass> m : maplePublicPackages.values()) {
            for(MapleDeadlockClass mdc : m.values()) {
                System.out.println(mdc);
            }
        }
        */
        
        parseDataTypes();
        
        generateDereferencedDataTypes();
        
        generateReflectedDataTypes();
        
        solveRunnableFunctions();
        
        /*
        for(MapleDeadlockFunction v : mapleRunnableMethods) {
            System.out.println(v);
        }
        */
        
        return storage;
    }
}
