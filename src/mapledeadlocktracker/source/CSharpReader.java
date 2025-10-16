/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
 */
package mapledeadlocktracker.source;

import mapledeadlocktracker.MapleDeadlockGraphMaker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import mapledeadlocktracker.containers.MapleDeadlockClass;
import mapledeadlocktracker.containers.MapleDeadlockClass.DeadlockClassType;
import mapledeadlocktracker.containers.MapleDeadlockEnum;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockLock;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.containers.Pair;
import mapledeadlocktracker.graph.MapleDeadlockAbstractType;
import mapledeadlocktracker.strings.MapleIgnoredTypes;
import mapledeadlocktracker.strings.MapleLinkedTypes;
import mapledeadlocktracker.strings.MapleReflectedTypes;
import language.csharp.CSharpLexer;
import language.csharp.CSharpParser;
import language.csharp.CSharpParserBaseListener;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 *
 * @author RonanLana
 */
public class CSharpReader extends CSharpParserBaseListener {
	private static MapleDeadlockStorage storage = new MapleDeadlockStorage();
	private static String syncLockTypeName = "SynchLock";

	// ---- cached storage fields ----
	private static Map<String, Map<String, MapleDeadlockClass>> maplePublicClasses = storage.getPublicClasses();
	private static Map<String, Map<String, MapleDeadlockClass>> maplePrivateClasses = storage.getPrivateClasses();

	private static Map<String, MapleDeadlockLock> mapleLocks = storage.getLocks();
	private static Map<String, MapleDeadlockLock> mapleReadWritemapleLocks = storage.getReadWriteLocks();

	private static Map<MapleDeadlockClass, Integer> mapleClassDataTypes = storage.getClassDataTypes();
	private static Map<List<Integer>, Integer> mapleCompoundDataTypes = storage.getCompoundDataTypes();
	private static Map<String, Integer> mapleBasicDataTypes = storage.getBasicDataTypes();
	private static Map<Integer, Integer> mapleElementalDataTypes = storage.getElementalDataTypes();
	private static Integer[] mapleElementalTypes = storage.getElementalTypes();

	private static Map<MapleDeadlockClass, List<MapleDeadlockClass>> mapleInheritanceTree = storage.getInheritanceTree();
	private static Map<Integer, Pair<Integer, Map<String, Integer>>> mapleReflectedClasses = storage.getReflectedClasses();

	private static Map<MapleDeadlockFunction, Boolean> mapleRunnableFunctions = new HashMap<>();
	private static Set<MapleDeadlockFunction> mapleRunnableMethods = storage.getRunnableMethods();

	//private static Map<Integer, String> mapleCompoundDataNames = new HashMap();   // test purposes only

	// ---- volatile fields ----

	private static AtomicInteger runningId = new AtomicInteger(1);
	private static AtomicInteger runningSyncLockId = new AtomicInteger(0);
	private static AtomicInteger runningTypeId = new AtomicInteger(1);  // volatile id 0 reserved for sync locks
	private static AtomicInteger runningMethodCallCount = new AtomicInteger(0);

	private static Stack<Integer> methodCallCountStack = new Stack();
	private static Stack<MapleDeadlockFunction> methodStack = new Stack();
	private static List<MapleDeadlockClass> classStack = new ArrayList();

	private static Set<String> readLockWaitingSet = new HashSet();
	private static Set<String> writeLockWaitingSet = new HashSet();

	private static Map<String, String> readLockQueue = new HashMap();
	private static Map<String, String> writeLockQueue = new HashMap();

	private static Map<Integer, String> mapleLinkedDataNames = new HashMap();

	private static List<String> currentImportList = new ArrayList<>();
	private static String sourceDirPrefixPath;
	private static Stack<String> currentPackageName = new Stack<>();
	private static String currentCompleteFileClassName;
	private static MapleDeadlockClass currentClass = null;
	private static List<MapleDeadlockClass> customClasses = new LinkedList<>();
	private static boolean currentAbstract = false;

	private static Map<Integer, Pair<MapleDeadlockClass, Integer>> volatileMaskedTypes = new HashMap<>();
	private static Map<Integer, Pair<String, String>> volatileDataTypes = new HashMap<>();  // cannot recover the import classes at the first parsing, so the type definition comes at the second rundown
        
        private static MapleDeadlockClass defaultClass = new MapleDeadlockClass(DeadlockClassType.CLASS, "_DefaultClass", "_package.", "", Collections.emptyList(), true, null);

	public void setSourceDirPrefixPath(String sourceDirPath) {
		sourceDirPath = sourceDirPath.trim().toLowerCase();
		sourceDirPath = sourceDirPath.replace('\\', '/');

		int i = sourceDirPath.lastIndexOf('.') - 1;
		if (i < 0) i = sourceDirPath.length();

		while (i >= 0) {
			switch (sourceDirPath.charAt(i)) {
			case '.':
				sourceDirPrefixPath = sourceDirPath.substring(sourceDirPath.indexOf("/", i) + 1) + "/";
				i = -1;
				break;

			default:
			}

			i--;
		}
	}

	public void setPackageNameFromFilePath(String fileName) {
		String str = new String(fileName);
		str = str.replace('\\', '/');

		int idx = str.lastIndexOf("/");
		if (idx > -1) {
			str = str.substring(0, idx + 1);			
		}

		idx = str.toLowerCase().indexOf(sourceDirPrefixPath);
		str = str.substring(idx + sourceDirPrefixPath.length());
		str = str.replace('/', '.');

		currentPackageName.push(str);
	}

	@Override
	public void enterCompilation_unit(CSharpParser.Compilation_unitContext ctx) {
		MapleDeadlockFunction method = new MapleDeadlockFunction("_global", null, null, currentAbstract);
		methodStack.add(method);

		currentImportList.clear();
	}

	@Override
	public void enterNamespace_declaration(CSharpParser.Namespace_declarationContext ctx) {
		currentPackageName.push(ctx.qualified_identifier().getText() + ".");
	}

	@Override
	public void exitNamespace_declaration(CSharpParser.Namespace_declarationContext ctx) {
		currentPackageName.pop();
	}

	@Override
	public void enterUsing_directives(CSharpParser.Using_directivesContext ctx) {
		for (CSharpParser.Using_directiveContext ctxd : ctx.using_directive()) {
                        String s = "";
			if (ctxd instanceof CSharpParser.UsingAliasDirectiveContext) {
				CSharpParser.UsingAliasDirectiveContext ctxa = (CSharpParser.UsingAliasDirectiveContext) ctxd;
				s = ctxa.namespace_or_type_name().getText();
			} else if (ctxd instanceof CSharpParser.UsingNamespaceDirectiveContext) {
				CSharpParser.UsingNamespaceDirectiveContext ctxn = (CSharpParser.UsingNamespaceDirectiveContext) ctxd;
				s = ctxn.namespace_or_type_name().getText();
			} else if (ctxd instanceof CSharpParser.UsingStaticDirectiveContext) {
				CSharpParser.UsingStaticDirectiveContext ctxs = (CSharpParser.UsingStaticDirectiveContext) ctxd;
				s = ctxs.namespace_or_type_name().getText();
			}
                        
                        currentImportList.add(s);
		}
	}

	@Override
	public void enterClass_member_declaration(CSharpParser.Class_member_declarationContext ctx) {
		currentAbstract = false;

		if (ctx.all_member_modifiers() != null) {
			for (CSharpParser.All_member_modifierContext ctxi : ctx.all_member_modifiers().all_member_modifier()) {
				currentAbstract |= (ctxi.ABSTRACT() != null);
			}
		}
	}

	@Override
	public void enterType_declaration(CSharpParser.Type_declarationContext ctx) {
		currentAbstract = false;

		if(ctx.attributes() != null) {
			for (CSharpParser.Attribute_sectionContext ctxa : ctx.attributes().attribute_section()) {
				if (ctxa.attribute_target() != null && ctxa.attribute_target().keyword() != null) {
					currentAbstract |= (ctxa.attribute_target().keyword().ABSTRACT() != null);
				}
			}
		}
	}

	@Override
	public void enterType_argument_list(CSharpParser.Type_argument_listContext ctx) {
		for(CSharpParser.Type_Context typC : ctx.type_()) {
			Integer mType = getTypeId(typC.getText(), currentCompleteFileClassName);

			volatileMaskedTypes.put(mType, new Pair<>(currentClass, currentClass.getMaskedTypeSize()));
			currentClass.addMaskedType(mType);
		}
	}

	private static List<String> getExtendedImplementedList(CSharpParser.Class_baseContext ttcCtx) {
		List<String> list = new LinkedList<>();

		if (ttcCtx != null) {
			list.add(ttcCtx.class_type().namespace_or_type_name().getText());
			for(CSharpParser.Namespace_or_type_nameContext tt : ttcCtx.namespace_or_type_name()) {
				list.add(tt.getText());
			}	
		}

		return list;
	}

	private static List<String> getExtendedImplementedListForEnum(CSharpParser.Enum_baseContext ttcCtx) {
		List<String> list = new LinkedList<>();

		if (ttcCtx != null) {
			list.add(ttcCtx.type_().getText());
		}

		return list;
	}

	private static List<String> getExtendedImplementedListForInterface(CSharpParser.Interface_baseContext ttcCtx) {
		List<String> list = new LinkedList<>();

		if (ttcCtx != null) {
			for(CSharpParser.Namespace_or_type_nameContext tt : ttcCtx.interface_type_list().namespace_or_type_name()) {
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
	public void enterClass_definition(CSharpParser.Class_definitionContext ctx) {
		String className = ctx.identifier().IDENTIFIER().getText();
		boolean isAbstract = currentAbstract;

		List<String> superNames = getExtendedImplementedList(ctx.class_base());

		if(currentClass != null) {
			classStack.add(currentClass);
			currentClass = new MapleDeadlockClass(DeadlockClassType.CLASS, className, currentPackageName.peek(), getPathName(className), superNames, isAbstract, currentClass);
                        currentClass.addImport(currentPackageName.peek().substring(0, currentPackageName.peek().length() - 1));
		} else {
			currentCompleteFileClassName = currentPackageName.peek() + className;

			int idx = className.indexOf(".");
			if (idx > -1) className = className.substring(idx + 1);

			currentClass = new MapleDeadlockClass(DeadlockClassType.CLASS, className, currentPackageName.peek(), getPathName(className), superNames, isAbstract, null);
                        currentClass.addImport(currentPackageName.peek().substring(0, currentPackageName.peek().length() - 1));
		}

		mapleInheritanceTree.put(currentClass, new LinkedList<>());
	}

	@Override
	public void exitClass_definition(CSharpParser.Class_definitionContext ctx) {
		if(classStack.isEmpty()) {
			for(String s : currentImportList) {
				currentClass.addImport(s);
			}

			if(maplePublicClasses.containsKey(currentPackageName.peek())) {
				maplePublicClasses.get(currentPackageName.peek()).put(currentClass.getPathName(), currentClass);
			} else {
				maplePublicClasses.put(currentPackageName.peek(), newPackageClass(currentClass.getPathName(), currentClass));
			}

			currentClass = null;
		} else {
			String fcn = currentCompleteFileClassName;

			if(maplePrivateClasses.containsKey(fcn)) {
				maplePrivateClasses.get(fcn).put(getPathName(currentClass.getName()), currentClass);
			} else {
				maplePrivateClasses.put(fcn, newPackageClass(getPathName(currentClass.getName()), currentClass));
			}

			MapleDeadlockClass mdc = currentClass;
			currentClass = classStack.remove(classStack.size() - 1);
			currentClass.addPrivateClass(mdc.getName(), mdc);
		}
	}

	@Override
	public void enterEnum_definition(CSharpParser.Enum_definitionContext ctx) {
		String className = ctx.identifier().IDENTIFIER().getText();

		List<String> superNames = getExtendedImplementedListForEnum(ctx.enum_base());

		if(currentClass != null) {
			classStack.add(currentClass);
			currentClass = new MapleDeadlockEnum(className, currentPackageName.peek(), getPathName(className), superNames, currentClass);
		} else {
			currentCompleteFileClassName = currentPackageName.peek() + className;

			int idx = className.indexOf(".");
			if (idx > -1) className = className.substring(idx + 1);

			currentClass = new MapleDeadlockEnum(className, currentPackageName.peek(), getPathName(className), superNames, null);
		}

		mapleInheritanceTree.put(currentClass, new LinkedList<>());
	}

	@Override
	public void exitEnum_definition(CSharpParser.Enum_definitionContext ctx) {
		if(classStack.isEmpty()) {
			for(String s : currentImportList) {
				currentClass.addImport(s);
			}

			if(maplePublicClasses.containsKey(currentPackageName.peek())) {
				maplePublicClasses.get(currentPackageName.peek()).put(currentClass.getPathName(), currentClass);
			} else {
				maplePublicClasses.put(currentPackageName.peek(), newPackageClass(currentClass.getPathName(), currentClass));
			}

			currentClass = null;
		} else {
			String fcn = currentCompleteFileClassName;

			if(maplePrivateClasses.containsKey(fcn)) {
				maplePrivateClasses.get(fcn).put(getPathName(currentClass.getName()), currentClass);
			} else {
				maplePrivateClasses.put(fcn, newPackageClass(getPathName(currentClass.getName()), currentClass));
			}

			MapleDeadlockClass mdc = currentClass;
			currentClass = classStack.remove(classStack.size() - 1);
			currentClass.addPrivateClass(mdc.getName(), mdc);
		}
	}

	@Override
	public void enterInterface_definition(CSharpParser.Interface_definitionContext ctx) {
		String className = ctx.identifier().IDENTIFIER().getText();

		List<String> superNames = getExtendedImplementedListForInterface(ctx.interface_base());

		if(currentClass != null) {
			classStack.add(currentClass);
			currentClass = new MapleDeadlockClass(DeadlockClassType.INTERFACE, className, currentPackageName.peek(), getPathName(className), superNames, true, currentClass);
                        currentClass.addImport(currentPackageName.peek().substring(0, currentPackageName.peek().length() - 1));
		} else {
			currentCompleteFileClassName = currentPackageName.peek() + className;
			currentClass = new MapleDeadlockClass(DeadlockClassType.INTERFACE, className, currentPackageName.peek(), getPathName(className), superNames, true, null);
                        currentClass.addImport(currentPackageName.peek().substring(0, currentPackageName.peek().length() - 1));
		}

		mapleInheritanceTree.put(currentClass, new LinkedList<>());
	}

	@Override
	public void exitInterface_definition(CSharpParser.Interface_definitionContext ctx) {
		if(classStack.isEmpty()) {
			for(String s : currentImportList) {
				currentClass.addImport(s);
			}

			if(maplePublicClasses.containsKey(currentPackageName.peek())) {
				maplePublicClasses.get(currentPackageName.peek()).put(currentClass.getPathName(), currentClass);
			} else {
				maplePublicClasses.put(currentPackageName.peek(), newPackageClass(currentClass.getPathName(), currentClass));
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
	public void enterInterface_member_declaration(CSharpParser.Interface_member_declarationContext ctx) {
		CSharpParser.IdentifierContext idCtx = ctx.identifier();

		if(idCtx != null) {
			String curText = ctx.type_().getText();
			Integer type = getTypeId(curText, currentCompleteFileClassName);

			currentClass.addFieldVariable(type, idCtx.getText());
		}
	}

	private static Map<String, MapleDeadlockClass> newPackageClass(String s, MapleDeadlockClass c) {
		Map<String, MapleDeadlockClass> m = new HashMap<>();
		m.put(s, c);

		return m;
	}

	private List<String> fullClassMethodName(List<String> list) {
		List<String> ret = new ArrayList<>(2);

		String path = "";
		String methodName = list.remove(list.size() - 1);

		for (String name : list) {
			path += name + ".";
		}
		if (!path.isEmpty()) path = path.substring(0, path.length() - 1);

		ret.add(path);
		ret.add(methodName);

		return ret;
	}

	private void enterMethodDeclaration(List<String> list, CSharpParser.Method_declarationContext ctx, String retTypeName) {
		String className = list.get(0);
		String methodName = list.get(1);

		MapleDeadlockClass mdc;
		if (!className.isEmpty()) {
			mdc = MapleDeadlockStorage.locateClass(className, currentClass);
		} else {
			mdc = currentClass;
		}
                
                MapleDeadlockFunction method = new MapleDeadlockFunction(methodName, mdc, methodStack.isEmpty() ? null : methodStack.peek(), currentAbstract);
		Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> retParamTypes = getMethodMetadata(ctx, retTypeName, method);

		method.setMethodMetadata(retParamTypes.left, retParamTypes.right.left, retParamTypes.right.right);
		methodStack.add(method);

		methodCallCountStack.add(runningMethodCallCount.get());
		runningMethodCallCount.set(0);
                
                if (mdc != null) mdc.addClassMethod(method);
	}

	private void exitMethodDeclaration(boolean lambdaMethod) {
		MapleDeadlockFunction method = methodStack.pop();
                if(lambdaMethod) {
			// book-keeping possible Runnable functions to be dealt with later on the parsing

			mapleRunnableFunctions.put(method, true);
		}

		runningMethodCallCount.set(methodCallCountStack.pop());
	}
        
        @Override
	public void enterProperty_declaration(CSharpParser.Property_declarationContext ctx) {
		String typeText = ((CSharpParser.Typed_member_declarationContext) ctx.getParent()).type_().getText();
                String vdName = ctx.member_name().getText();
                
                String tt = getFullTypeText(typeText, vdName);
                int type = getTypeId(tt, currentCompleteFileClassName);

                if (currentClass != null) currentClass.addFieldVariable(type, vdName);
	}

	@Override
	public void enterMethod_declaration(CSharpParser.Method_declarationContext ctx) {
		List<String> list = new ArrayList<>();
                
                for (CSharpParser.IdentifierContext name : ctx.method_member_name().identifier()) {
			list.add(name.getText());
		}

                String typeName = "void";
                if (ctx.getParent() instanceof CSharpParser.Typed_member_declarationContext)  {
                        typeName = ((CSharpParser.Typed_member_declarationContext) ctx.getParent()).type_().getText();
                }
                
		enterMethodDeclaration(fullClassMethodName(list), ctx, typeName);
	}

	@Override
	public void exitMethod_declaration(CSharpParser.Method_declarationContext ctx) {
		exitMethodDeclaration(false);
	}
        
        @Override
	public void enterLambda_expression(CSharpParser.Lambda_expressionContext ctx) {
		MapleDeadlockFunction method = new MapleDeadlockFunction("_unidentified_"  + mapleRunnableMethods.size(), defaultClass, methodStack.isEmpty() ? null : methodStack.peek(), currentAbstract);
		Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> retParamTypes = getLambdaMethodMetadata(ctx, "void", method);

		method.setMethodMetadata(retParamTypes.left, retParamTypes.right.left, retParamTypes.right.right);
		methodStack.add(method);

		methodCallCountStack.add(runningMethodCallCount.get());
		runningMethodCallCount.set(0);
	}
        
        @Override
	public void exitLambda_expression(CSharpParser.Lambda_expressionContext ctx) {
		exitMethodDeclaration(true);
	}

	private void enterLocalFunctionDeclaration(List<String> list, CSharpParser.Local_function_declarationContext ctx) {
		String className = list.get(0);
		String methodName = list.get(1);

		MapleDeadlockClass mdc = MapleDeadlockStorage.locateClass(className, currentClass);

		MapleDeadlockFunction method = new MapleDeadlockFunction(methodName, mdc, methodStack.isEmpty() ? null : methodStack.peek(), currentAbstract);
		Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> retParamTypes = getLocalFunctionMetadata(ctx, method);

		method.setMethodMetadata(retParamTypes.left, retParamTypes.right.left, retParamTypes.right.right);
		methodStack.add(method);

		methodCallCountStack.add(runningMethodCallCount.get());
		runningMethodCallCount.set(0);
	}

	@Override
	public void enterLocal_function_declaration(CSharpParser.Local_function_declarationContext ctx) {
		List<String> list = new ArrayList<>(2);
		list.add("_");
		list.add(ctx.local_function_header().identifier().IDENTIFIER().getText());

		enterLocalFunctionDeclaration(list, ctx);
	}

	@Override
	public void exitLocal_function_declaration(CSharpParser.Local_function_declarationContext ctx) {
		exitMethodDeclaration(false);
	}

	@Override
	public void enterConstructor_declaration(CSharpParser.Constructor_declarationContext ctx) {
		String methodName = ctx.identifier().IDENTIFIER().getText();

		MapleDeadlockFunction method = new MapleDeadlockFunction(methodName, currentClass, null, false);
		Pair<List<Integer>, Map<Long, Integer>> pTypes = getMethodParameterTypes(ctx.formal_parameter_list(), method);

		method.setMethodMetadata(-1, pTypes.left, pTypes.right);
		methodStack.add(method);
	}

	@Override
	public void exitConstructor_declaration(CSharpParser.Constructor_declarationContext ctx) {
		MapleDeadlockFunction method = methodStack.pop();
		if (currentClass != null) currentClass.addClassMethod(method);
	}

	@Override
	public void enterField_declaration(CSharpParser.Field_declarationContext ctx) {
		List<String> vdNames = ctx.variable_declarators().variable_declarator().stream()
				.map(vdItem -> vdItem.identifier().getText())
				.collect(Collectors.toList());

		processVariableDeclarations(true, ((CSharpParser.Typed_member_declarationContext) ctx.getParent()).type_().getText(), vdNames);
	}
        
        @Override
	public void enterEmbedded_statement(CSharpParser.Embedded_statementContext ctx) {
                if (ctx.simple_embedded_statement() != null) {
                        if (ctx.simple_embedded_statement() instanceof CSharpParser.ForeachStatementContext) {
                                CSharpParser.ForeachStatementContext fctx = (CSharpParser.ForeachStatementContext) ctx.simple_embedded_statement();
                                if (fctx.local_variable_type().type_() != null) {
                                        processVariableDeclarations(true, fctx.local_variable_type().type_().getText(), Collections.singletonList(fctx.identifier().IDENTIFIER().getText()));
                                }
                        } else if (ctx.simple_embedded_statement() instanceof CSharpParser.LockStatementContext) {
                                String syncLockName = MapleDeadlockGraphMaker.getSyncLockName(((CSharpParser.LockStatementContext) ctx.simple_embedded_statement()).expression().getText(), methodStack.peek().getId());
                                methodStack.peek().addMethodCall(generateSyncLockExpression(syncLockName, true));
                        }
                }
	}
        
        @Override
	public void exitEmbedded_statement(CSharpParser.Embedded_statementContext ctx) {
                if (ctx.simple_embedded_statement() != null) {
                        if (ctx.simple_embedded_statement() instanceof CSharpParser.LockStatementContext) {
                                String syncLockName = MapleDeadlockGraphMaker.getSyncLockName(((CSharpParser.LockStatementContext) ctx.simple_embedded_statement()).expression().getText(), methodStack.peek().getId());
                                methodStack.peek().addMethodCall(generateSyncLockExpression(syncLockName, false));
                        }
                }
	}

	@Override
	public void enterEvent_declaration(CSharpParser.Event_declarationContext ctx) {
		MapleDeadlockFunction method = new MapleDeadlockFunction("_event_"  + mapleRunnableMethods.size(), currentClass, null, false);
		Pair<List<Integer>, Map<Long, Integer>> pTypes = getMethodParameterTypes(null, method);

		method.setMethodMetadata(-1, pTypes.left, pTypes.right);
		methodStack.add(method);
	}

	@Override
	public void exitEvent_declaration(CSharpParser.Event_declarationContext ctx) {
		MapleDeadlockFunction method = methodStack.pop();
		mapleRunnableMethods.add(method);
	}

	@Override
	public void enterLocal_variable_declaration(CSharpParser.Local_variable_declarationContext ctx) {
		if (ctx.local_variable_declarator() != null) {
			processLocalVariableDeclaratorId(ctx.local_variable_type().getText(), ctx.local_variable_declarator().get(ctx.local_variable_declarator().size() - 1).identifier().getText(), methodStack.peek());
		}
	}

	@Override
	public void enterSpecific_catch_clause(CSharpParser.Specific_catch_clauseContext ctx) {
		if (ctx.identifier() != null) {
			processLocalVariableDeclaratorId("Exception", ctx.identifier().getText(), methodStack.peek());
		}
	}

	private void enterElementValuePair(String elementName, String value) {
		String lockName = currentPackageName.peek() + (currentClass != null ? currentClass.getPathName() + ".": "") + elementName;

		if(!readLockWaitingSet.isEmpty() || !writeLockWaitingSet.isEmpty()) {
			if(readLockWaitingSet.contains(lockName)) {
				processLock(currentClass, "ReadLock1", elementName, elementName);
			} else if(writeLockWaitingSet.contains(lockName)) {
				processLock(currentClass, "WriteLock1", elementName, elementName);
			}
		}
	}

	@Override
	public void enterEnum_member_declaration(CSharpParser.Enum_member_declarationContext ctx) {
		enterElementValuePair(ctx.identifier().IDENTIFIER().getText(), ctx.identifier().IDENTIFIER().getText());
	}

	@Override
	public void enterArg_declaration(CSharpParser.Arg_declarationContext ctx) {
		enterElementValuePair(ctx.identifier().IDENTIFIER().getText(), ctx.identifier().IDENTIFIER().getText());
	}

	@Override
	public void enterConstant_declarator(CSharpParser.Constant_declaratorContext ctx) {
		enterElementValuePair(ctx.identifier().IDENTIFIER().getText(), ctx.identifier().IDENTIFIER().getText());
	}

	@Override
	public void enterLet_clause(CSharpParser.Let_clauseContext ctx) {
		if (ctx.expression() != null) {
			enterElementValuePair(ctx.identifier().IDENTIFIER().getText(), ctx.expression().getChild(0).getText());
		}
	}

	@Override
	public void enterMember_declarator(CSharpParser.Member_declaratorContext ctx) {
		if (ctx.expression() != null) {
			enterElementValuePair(ctx.identifier().IDENTIFIER().getText(), ctx.expression().getChild(0).getText());
		}
	}

	private static CSharpParser.Unary_expressionContext generateSyncLockExpression(String syncLockName, boolean lock) {
		String lockStrExpr = syncLockName + "." + (lock ? "_lock" : "_unlock") + "()";
		CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(lockStrExpr));
		CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
		CSharpParser parser = new CSharpParser(commonTokenStream);

		return parser.unary_expression();
	}

	private static void addMethodFromExpression(CSharpParser.Unary_expressionContext ctx) {
		MapleDeadlockFunction mdf = methodStack.peek();

		if(!ctx.primary_expression().method_invocation().isEmpty() || !ctx.primary_expression().member_access().isEmpty()) {
			mdf.addMethodCall(ctx);
		}
	}

	@Override
	public void enterUnary_expression(CSharpParser.Unary_expressionContext ctx) {
		runningMethodCallCount.incrementAndGet();
	}

	@Override
	public void exitUnary_expression(CSharpParser.Unary_expressionContext ctx) {
		int count = runningMethodCallCount.decrementAndGet();
		if(count == 0 && !methodStack.isEmpty() && ctx.primary_expression() != null && ctx.primary_expression().method_invocation().size() > 0) {
			addMethodFromExpression(ctx);
		}
	}
        
	private static Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> getLambdaMethodMetadata(CSharpParser.Lambda_expressionContext ctx, String retTypeName, MapleDeadlockFunction method) {
		Integer type = getTypeId(retTypeName, currentCompleteFileClassName);
		Pair<List<Integer>, Map<Long, Integer>> params = getLambdaMethodParameterTypes(ctx.anonymous_function_signature(), method);

		return new Pair<>(type, params);
	}
        
        private static Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> getMethodMetadata(CSharpParser.Method_declarationContext ctx, String retTypeName, MapleDeadlockFunction method) {
		Integer type = getTypeId(retTypeName, currentCompleteFileClassName);
		Pair<List<Integer>, Map<Long, Integer>> params = getMethodParameterTypes(ctx.formal_parameter_list(), method);

		return new Pair<>(type, params);
	}

	private static Pair<Integer, Pair<List<Integer>, Map<Long, Integer>>> getLocalFunctionMetadata(CSharpParser.Local_function_declarationContext ctx, MapleDeadlockFunction method) {
		Integer type = getTypeId(ctx.local_function_header().return_type().getText(), currentCompleteFileClassName);
		Pair<List<Integer>, Map<Long, Integer>> params = getMethodParameterTypes(ctx.local_function_header().formal_parameter_list(), method);

		return new Pair<>(type, params);
	}
        
        private static void addMethodParameter(String typeName, String typeText, String nameText, MapleDeadlockFunction method, Map<Long, Integer> params, List<Integer> pTypes) {
                String tt = getFullTypeText(typeName, typeText);
                int typeId = getTypeId(tt, currentCompleteFileClassName);
                pTypes.add(typeId);

                Long val = method.addLocalVariable(typeId, nameText);
                params.put(val, typeId);
        }

	private static Pair<List<Integer>, Map<Long, Integer>> getMethodParameterTypes(CSharpParser.Formal_parameter_listContext ctx, MapleDeadlockFunction method) {
		Map<Long, Integer> params = new HashMap<>();
		List<Integer> pTypes = new LinkedList<>();

		if(ctx != null) {
			CSharpParser.Parameter_arrayContext aCtx = ctx.parameter_array();
                        if (aCtx != null) {
                                while(aCtx != null) {
                                        String typeText = aCtx.array_type().getText();
                                        String nameText = aCtx.identifier().getText();

                                        addMethodParameter(aCtx.array_type().base_type().getText(), typeText, nameText, method, params, pTypes);
                                        
                                        aCtx = ctx.parameter_array();
                                }
                        } else {
                                for (CSharpParser.Fixed_parameterContext paramCtx : ctx.fixed_parameters().fixed_parameter()) {
                                        String typeText = paramCtx.arg_declaration().type_().getText();
                                        String nameText = paramCtx.arg_declaration().identifier().getText();
                                        
                                        addMethodParameter(paramCtx.arg_declaration().type_().base_type().getText(), typeText, nameText, method, params, pTypes);
                                }
                        }
		}

		return new Pair<>(pTypes, params);
	}
        
        private static Pair<List<Integer>, Map<Long, Integer>> getLambdaMethodParameterTypes(CSharpParser.Anonymous_function_signatureContext ctx, MapleDeadlockFunction method) {
		Map<Long, Integer> params = new HashMap<>();
		List<Integer> pTypes = new LinkedList<>();

                CSharpParser.Implicit_anonymous_function_parameter_listContext aCtx = ctx.implicit_anonymous_function_parameter_list();
                if (aCtx != null) {
                        for (CSharpParser.IdentifierContext idCtx : aCtx.identifier()) {
                                addMethodParameter("Object", "Object", idCtx.IDENTIFIER().getText(), method, params, pTypes);
                        }
                } else {
                        CSharpParser.Explicit_anonymous_function_parameter_listContext bCtx = ctx.explicit_anonymous_function_parameter_list();
                        if (bCtx != null) {
                                for (CSharpParser.Explicit_anonymous_function_parameterContext apCtx : bCtx.explicit_anonymous_function_parameter()) {
                                        addMethodParameter(apCtx.type_().base_type().getText(), apCtx.type_().getText(), apCtx.identifier().IDENTIFIER().getText(), method, params, pTypes);
                                }
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

	private static void processFieldVariableDeclarations(String typeText, List<String> vdList) {
		if (currentClass != null) {
			for(String vdName : vdList) {
				String tt = getFullTypeText(typeText, vdName);
				int type = getTypeId(tt, currentCompleteFileClassName);

				currentClass.addFieldVariable(type, vdName);
			}
		}
	}

	private static void processLocalVariableDeclarations(String typeText, List<String> vdList, MapleDeadlockFunction method) {
		for(String vdName : vdList) {
			processLocalVariableDeclaratorId(typeText, vdName, method);
		}
	}

	private static void processLocalVariableDeclaratorId(String typeText, String vdName, MapleDeadlockFunction method) {
		String tt = getFullTypeText(typeText, vdName);    
		int type = getTypeId(tt, currentCompleteFileClassName);

		String vdId = vdName;
		int idx = vdId.indexOf('[');
		if (idx > -1) {
			vdId.substring(0, idx);
		}

		method.addLocalVariable(type, vdId);
	}

	private static Integer getTypeId(String type, String fileClass) {
		Integer t = runningTypeId.getAndIncrement();
		volatileDataTypes.put(t, new Pair<>(type, fileClass));

		return t;
	}

	private static void processVariableDeclarations(boolean isFieldVar, String typeText, List<String> vdList) {
		if(typeText.contains("Lock")) {
			for(String vdName : vdList) {
				processLock(currentClass, typeText, vdName, vdName);
			}
		}

		if(isFieldVar) processFieldVariableDeclarations(typeText, vdList);
		else processLocalVariableDeclarations(typeText, vdList, methodStack.peek());
	}

	private static Pair<String, String> captureLockNameAndReference(CSharpParser.Member_declaratorContext ctx) {
		String name = "_", reference = "_";

		CSharpParser.IdentifierContext c1 = ctx.identifier();
		if (c1 != null) {
			CSharpParser.ExpressionContext c2 = ctx.expression();

			if(c2 != null) {
				if(c2.getText().contains("Lock(")) {	// this is a lock initializer
					try {
						name = c1.IDENTIFIER().getText();
						reference = c2.assignment().unary_expression().primary_expression().identifier().get(0).IDENTIFIER().getText();
					} catch (NullPointerException e) {}	// do nothing

					return new Pair<>(name, reference);
				}
			}
		}

		return null;
	}

	public static void processLock(MapleDeadlockClass mdc, String typeText, String name, String reference) {
		boolean isRead = typeText.contains("Read");
		boolean isWrite = typeText.contains("Write");

		String lockName = MapleDeadlockStorage.getCanonClassName(mdc) + "." + name;
		String  refName = MapleDeadlockStorage.getCanonClassName(mdc) + "." + reference;

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

			mapleReadWritemapleLocks.put(lockName, rwLock);
		} else if(isRead) {
			if(reference != null) {
				if(!readLockWaitingSet.remove(refName)) {
					readLockQueue.put(refName, lockName);
				} else {
					mapleLocks.put(lockName, mapleReadWritemapleLocks.get(refName));
				}
			} else {
				mapleLocks.put(lockName, null);
			}
		} else if(isWrite) {
			if(reference != null) {
				if(!writeLockWaitingSet.remove(refName)) {
					writeLockQueue.put(refName, lockName);
				} else {
					mapleLocks.put(lockName, mapleReadWritemapleLocks.get(refName));
				}
			} else {
				mapleLocks.put(lockName, null);
			}
		} else {
			mapleLocks.put(lockName, instanceNewLock(lockName));
		}
                
                mdc.addFieldVariable(0, name);
	}

	public static MapleDeadlockLock instanceNewLock(String lockName) {
		return new MapleDeadlockLock(runningId.getAndIncrement(), lockName);
	}

	private static MapleDeadlockClass getPublicClass(String packageName, String className) {
		MapleDeadlockClass mdc = maplePublicClasses.get(packageName).get(className);

		//if(mdc == null) System.out.println("FAILED TO FIND PUBLIC '" + className + "' @ '" + packageName + "'");
		return mdc;
	}

	private static MapleDeadlockClass getPrivateClass(String packageName, String className) {
		//System.out.println("trying " + packageName + " on " + className);
		Map<String, MapleDeadlockClass> m = maplePrivateClasses.get(packageName);
		MapleDeadlockClass mdc = (m != null) ? m.get(className) : null;

		//if(mdc == null) System.out.println("FAILED TO FIND PRIVATE '" + className + "' @ '" + packageName + "'");
		return mdc;
	}

	private static List<MapleDeadlockClass> getAllmaplePrivateClassesWithin(String treeName, Map<String, MapleDeadlockClass> privateMap) {
		List<MapleDeadlockClass> list = new LinkedList<>();

		if (privateMap != null) {
			for(Entry<String, MapleDeadlockClass> m : privateMap.entrySet()) {
				if(m.getKey().startsWith(treeName)) {
					list.add(m.getValue());
				}
			}
		}

		return list;
	}

	private static boolean isEnumClass(String packageName, String className) {
		MapleDeadlockClass mdc = getPrivateClass(packageName, className);
		if(mdc != null) {
			return mdc.isEnum();
		}

		return false;
	}

	private static void parseImportClass(MapleDeadlockClass mdc) {
                for(String s : mdc.getImportNames()) {
                        List<Pair<String, String>> p = new LinkedList<>();
                        
                        String packName = MapleDeadlockStorage.getPublicPackageName(s + ".");
                        if (packName != null) {
                                while (packName != null) {
                                        p.add(new Pair<>(packName, "*"));
                                        
                                        int idx = s.lastIndexOf(".");
                                        if (idx < 0) break;
                                        
                                        s = s.substring(0, idx);
                                        
                                        packName = MapleDeadlockStorage.getPublicPackageName(s + ".");
                                }
                        } else {
                                Pair<String, String> ps = MapleDeadlockStorage.locateClassPath(s);
                                if (ps != null) p.add(ps);
                        }
                        
			for (Pair<String, String> ps : p) {
				String packageName = ps.left;
				String className = ps.right;

				Map<String, MapleDeadlockClass> m = maplePublicClasses.get(packageName);

				if(m != null) {
					mdc.removeImport(s);    // changing full names for class name

					if(!className.contentEquals("*")) {
						MapleDeadlockClass importedClass = getPublicClass(packageName, className);
						mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
					} else {
						for(MapleDeadlockClass packClass : m.values()) {
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

							for(MapleDeadlockClass packClass : getAllmaplePrivateClassesWithin(className.substring(0, idx - 1), maplePrivateClasses.get(packageName))) {
								mdc.updateImport(packClass.getPackageName() + packClass.getPathName(), s, packClass);
							}
						} else {
							MapleDeadlockClass importedClass = getPrivateClass(packageName, className);
							if(importedClass != null) {
								mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
							}
						}
					} else {
						MapleDeadlockClass importedClass = getPrivateClass(packageName, className);
						mdc.updateImport(importedClass.getPackageName() + importedClass.getPathName(), s, importedClass);
					}
				}
			}
		}
	}

	private static void parseImportClasses() {
		for(Map<String, MapleDeadlockClass> mdp : maplePublicClasses.values()) {
			for(MapleDeadlockClass mdc : mdp.values()) {
				parseImportClass(mdc);
			}
		}

		for(Entry<String, Map<String, MapleDeadlockClass>> e : maplePrivateClasses.entrySet()) {
			String pc = e.getKey();

			Pair<String, String> p = MapleDeadlockStorage.locateClassPath(pc);
			String packName = p.left;
			String className = p.right;

			MapleDeadlockClass mdc = maplePublicClasses.get(packName).get(className);

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
				MapleDeadlockClass mdc2 = MapleDeadlockStorage.locateClass(MapleDeadlockStorage.getNameFromCanonClass(mdc), mdc);

				List<String> superNames = mdc.getSuperNameList();
				for(String supName : superNames) {
					MapleDeadlockClass parent = mdc.getParent();
					if(parent != null && mdc2.isInterface() && supName.contentEquals(parent.getName())) {
						List<MapleDeadlockClass> list = mapleInheritanceTree.get(mdc2);
						if(list != null) {
							list.add(parent);
						}
					} else {
						MapleDeadlockClass sup = mdc.getImport(supName);
						if (mdc2 != sup && sup != null) {
							mdc2.addSuper(sup);

							List<MapleDeadlockClass> list = mapleInheritanceTree.get(mdc2);
							if(list != null) {
								list.add(sup);
							}

							//if(sup == null) System.out.println("NULL SUPER '" + superName + "' FOR '" + mdc.getName() + "'");
						}
					}
				}

				MapleDeadlockClass parent = mdc2.getParent();
				if(parent != null) {
					List<MapleDeadlockClass> list = mapleInheritanceTree.get(parent);
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

		int st = Math.min(type.indexOf('('), type.indexOf('<')) + 1, en = 0;
		int c = 1;
		for(int i = st; i < type.length(); i++) {
			char ch = type.charAt(i);

			if(ch == ',') {
				if(c == 1) {
					ret.add(parseWrappedType(type.substring(st, i)));
					st = i + 1;
				}
			} else if(ch == '(' || ch == '<') {
				c++;
			} else if(ch == ')' || ch == '<') {
				c--;
				en = i;
			}
		}

		if(st == en) return null;

		ret.add(type.substring(st, en).trim());
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

		int en = type.indexOf('(');
		if(en != -1) t = type.substring(0, en);

		if (MapleIgnoredTypes.isDataTypeIgnored(t)) {
			return mapleBasicDataTypes.get("Object");
		}

		switch (MapleDeadlockAbstractType.getValue(t)) {
		case LOCK:
			return mapleElementalTypes[5];

		case SCRIPT:
			return mapleElementalTypes[6];
		}

		try {
			targetClass = pc.getImport(t);
			if (targetClass == null) {
				String path = MapleDeadlockStorage.getPublicPackageName(pc);
				targetClass = maplePublicClasses.get(path).get(t);
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
					ret = mapleBasicDataTypes.get(targetClass.getPackageName() + targetClass.getName() + nameChanged);
					if(ret == null) {
						ret = runningTypeId.getAndIncrement();
						mapleBasicDataTypes.put(targetClass.getPackageName() + targetClass.getName() + nameChanged, ret);
					}

					nameChanged += "[]";
				}
			}

			ret = mapleClassDataTypes.get(targetClass);
			if(ret == null) {
				ret = classId;
				mapleClassDataTypes.put(targetClass, ret);
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
		} else if(c > 0) {
			for(int i = 0; i < c; i++) {
				t += "[]";
			}

			ret = mapleBasicDataTypes.get(t);
			if(ret == null) {
				ret = runningTypeId.getAndIncrement();
				mapleBasicDataTypes.put(t, ret);
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
		if(type.contentEquals("void")) return -2;

		Integer ret = fetchDataType(type, MapleDeadlockStorage.locateClass(p.right));
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

		for(Map<String, MapleDeadlockClass> m : maplePublicClasses.values()) {
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

		updatePackageReferences(maplePublicClasses);
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
		instantiateElementalDataType("Invocable");

		instantiateIgnoredDataTypes();
	}

	private static void generateElementalDataTypes() {
		mapleElementalTypes[0] = mapleBasicDataTypes.get("int");
		mapleElementalTypes[1] = mapleBasicDataTypes.get("int");    // float, but let numbers have the same data reference for the sake of simplicity
		mapleElementalTypes[2] = mapleBasicDataTypes.get("char");
		mapleElementalTypes[3] = mapleBasicDataTypes.get("String");
		mapleElementalTypes[4] = mapleBasicDataTypes.get("boolean");
		mapleElementalTypes[5] = mapleBasicDataTypes.get("Lock");
		mapleElementalTypes[6] = mapleBasicDataTypes.get("Invocable");
		mapleElementalTypes[7] = mapleBasicDataTypes.get("null");

		for(Pair<String, String> p : MapleLinkedTypes.getLinkedTypes()) {
			linkElementalDataTypes(p.left, p.right);
		}
	}

	private static void generateDereferencedDataTypes() {
		for(Entry<String, Integer> e : mapleBasicDataTypes.entrySet()) {
			String s = e.getKey();

			int c = countOccurrences(s, '[');
			if(c > 0) {
				MapleDeadlockClass targetClass = MapleDeadlockStorage.locatePublicClass(s.substring(0, s.indexOf('[')), null);
				if (targetClass != null) {
					String nameChanged = "";
					for(int i = 0; i < c; i++) {
						nameChanged += "[]";
					}

					if(nameChanged.length() > 0) {
						Integer i = mapleBasicDataTypes.get(targetClass.getPackageName() + targetClass.getName() + nameChanged);
						if(i == null) {
							i = runningTypeId.getAndIncrement();
							mapleBasicDataTypes.put(targetClass.getPackageName() + targetClass.getName() + nameChanged, i);
						}
					}
				} else {
					Integer i = mapleBasicDataTypes.get(s);
					if(i == null) {
						i = runningTypeId.getAndIncrement();
						mapleBasicDataTypes.put(s, i);
					}
				}
			}
		}
	}

	public static void solvemapleRunnableFunctions() {
		for(Entry<MapleDeadlockFunction, Boolean> runMdf : mapleRunnableFunctions.entrySet()) {
			MapleDeadlockFunction mdf = runMdf.getKey();
			updateFunctionReferences(mdf);

			MapleDeadlockClass mdc = mdf.getSourceClass();
			if(runMdf.getValue()) {
				mapleRunnableMethods.add(mdf);
			}

			mdc.addClassMethod(mdf);
		}
	}

	private static void referenceCustomClasses() {
		for (MapleDeadlockClass c : customClasses) {
			MapleDeadlockClass sup = MapleDeadlockStorage.locateClass(c.getName(), c);
			c.addSuper(sup);
		}
		customClasses.clear();
	}

	private static void referencemapleReadWritemapleLocks() {
		for (Entry<String, MapleDeadlockLock> e : mapleReadWritemapleLocks.entrySet()) {
			mapleLocks.put(e.getKey(), e.getValue());
		}
	}

	public static MapleDeadlockStorage compileProjectData() {
		parseImportClasses();

		parseSuperClasses(maplePublicClasses);
		parseSuperClasses(maplePrivateClasses);

		Map<String, Map<String, MapleDeadlockClass>> m = new HashMap<>();
		Map<String, MapleDeadlockClass> n = new HashMap<>();

		m.put("", n);
		for(MapleDeadlockClass c : customClasses) {
			n.put(c.getName(), c);
		}
		parseSuperClasses(m);

		referenceCustomClasses();
		referencemapleReadWritemapleLocks();

		/*
                for(Entry<Integer, Pair<String, String>> v : volatileDataTypes.entrySet()) {
                        System.out.println(v.getKey() + " : " + v.getValue());
                }
                
                for(Map<String, DeadlockClass> o : maplePublicClasses.values()) {
                        for(DeadlockClass mdc : o.values()) {
                                System.out.println(mdc);
                        }
                }

                for(Map<String, DeadlockClass> o : maplePrivateClasses.values()) {
                        for(DeadlockClass mdc : o.values()) {
                                System.out.println(mdc);
                        }
                }
                */
                
		parseDataTypes();
		fetchDataType("Set<Object>", null);

		generateDereferencedDataTypes();
		generateReflectedDataTypes();

		solvemapleRunnableFunctions();
                
                return storage;
	}
}
