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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javaparser.JavaParser;
import deadlocktracker.containers.DeadlockClass;
import deadlocktracker.containers.DeadlockEnum;
import deadlocktracker.containers.DeadlockFunction;
import deadlocktracker.containers.DeadlockLock;
import deadlocktracker.containers.DeadlockStorage;
import deadlocktracker.containers.Pair;
import deadlocktracker.graph.DeadlockAbstractType;
import deadlocktracker.graph.DeadlockGraphEntry;
import deadlocktracker.graph.DeadlockGraphNodeCall;
import deadlocktracker.graph.DeadlockGraphNodeLock;
import deadlocktracker.graph.DeadlockGraphNodeScript;
import deadlocktracker.graph.DeadlockGraphMethod;
import deadlocktracker.strings.LinkedTypes;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraphMaker {
    private static Map<String, Map<String, DeadlockClass>> PublicClasses;
    private static Map<String, Map<String, DeadlockClass>> PrivateClasses;
    private static Map<String, DeadlockClass> AllClasses;
    private static Map<String, DeadlockLock> Locks;
    
    private static Integer objectSetId;
    private static Map<Integer, Integer> ElementalDataTypes;
    private static Integer[] ElementalTypes;
    private static Set<Integer> EnumDataTypes;
    
    private static Map<DeadlockClass, Integer> ClassDataTypeIds;
    private static Map<String, Integer> BasicDataTypeIds;
    
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> ReflectedClasses;
    private static Map<DeadlockClass, List<DeadlockClass>> InheritanceTree;
    private static Pair<Integer, Integer> IgnoredDataRange;
    
    private static List<DeadlockFunction> RunnableMethods;
    
    private static Map<Integer, List<Integer>> CompoundDataTypes = new HashMap<>();
    private static Map<Integer, DeadlockClass> ClassDataTypes = new HashMap<>();
    private static Map<Integer, DeadlockAbstractType> AbstractDataTypes = new HashMap<>();
    private static Map<Integer, String> BasicDataTypes = new HashMap<>();
    private static Map<String, Integer> DereferencedDataTypes = new HashMap<>();
    
    private static Map<Integer, String> EveryDataTypes = new HashMap<>();
    private static Map<String, Integer> EveryDataTypeIds = new HashMap<>();
    
    private static Map<Integer, Set<Integer>> SuperClasses = new HashMap<>();
    private static Map<Integer, Integer> DataWrapper = new HashMap<>();
    
    private static Map<DeadlockFunction, Integer> GraphFunctionIds = new HashMap<>();
    private static Map<DeadlockFunction, DeadlockGraphMethod> GraphFunctions = new HashMap<>();
    
    private static Integer runningFid = 0;
    private static Integer lockId;
    
    private static List<Integer> getArgumentTypes(DeadlockGraphMethod node, JavaParser.ExpressionListContext expList, DeadlockFunction sourceMethod, DeadlockClass sourceClass) {
        List<Integer> ret = new LinkedList<>();
        if(expList != null) {
            for(JavaParser.ExpressionContext exp : expList.expression()) {
                for (Integer argType : parseMethodCalls(node, exp, sourceMethod, sourceClass)) {
                    ret.add((argType != -1 && !argType.equals(ElementalTypes[7])) ? argType : -2);  // make accept any non-determined argument-type
                }
            }
        }
        
        return ret;
    }
    
    private static Integer getWrappedValue(Integer dataType) {
        Integer ret = DataWrapper.get(dataType);
        
        if(ret == null) {
            List<Integer> cdt = CompoundDataTypes.get(dataType);
            
            ret = cdt.get(cdt.size() - 2);
            DataWrapper.put(dataType, ret);
        }
        
        return ret;
    }
    
    private static Integer evaluateLockFunction(String methodName, List<Integer> argTypes, Integer dataType, DeadlockGraphMethod node) {
        switch(methodName) {
            case "lock":
            case "tryLock":
                //System.out.println("adding lock node " + lockId);
                node.addGraphEntry(new DeadlockGraphEntry(new DeadlockGraphNodeLock(lockId, true)));
                break;
                
            case "unlock":
                //System.out.println("adding unlock node " + lockId);
                node.addGraphEntry(new DeadlockGraphEntry(new DeadlockGraphNodeLock(lockId, false)));
                break;
        }
        
        return ElementalTypes[4];
    }
    
    private static Integer evaluateScriptFunction(String methodName, List<Integer> argTypes, Integer dataType, DeadlockGraphMethod node) {
        DeadlockGraphEntry entry = new DeadlockGraphEntry();    
        entry.addGraphEntryPoint(new DeadlockGraphNodeScript());

        node.addGraphEntry(entry);
        return -2;
    }
    
    private static Integer evaluateAbstractFunction(DeadlockGraphMethod node, String methodName, List<Integer> argTypes, Integer dataType, DeadlockAbstractType absType) {
        switch(absType) {
            case MAP:
                if(methodName.contentEquals("entrySet")) {
                    return objectSetId;
                }
                
            case SET:
            case LIST:
            case STACK:
            case PRIORITYQUEUE:
                if(methodName.contentEquals("size")) return ElementalTypes[0];
                //if(methodName.contentEquals("iterator")) return ElementalTypes[0];
                
                return getWrappedValue(dataType);
            
            case REFERENCE:
                if(methodName.contentEquals("get")) {
                    return getWrappedValue(dataType);
                }
                
                return -2;
                
            case LOCK:
                return evaluateLockFunction(methodName, argTypes, dataType, node);
                
            case SCRIPT:
                return evaluateScriptFunction(methodName, argTypes, dataType, node);
                
            case STRING:
            case OTHER:
                return -2;
        }
        
        return -1;
    }
    
    private static DeadlockClass getClassFromType(Integer type) {
        return ClassDataTypes.get(type);
    }
    
    private static void createGraphFunction(DeadlockFunction mdf) {
        GraphFunctionIds.put(mdf, runningFid);
        GraphFunctions.put(mdf, new DeadlockGraphMethod(runningFid, DeadlockStorage.getCanonClassName(mdf.getSourceClass()) + " >> " + mdf.getName()));
        mdf.setId(runningFid);
        
        runningFid++;
    }
    
    private static Integer getLockId(String identifier, DeadlockClass sourceClass) {
        String lockName = DeadlockStorage.getCanonClassName(sourceClass) + "." + identifier;
        DeadlockLock lock = Locks.get(lockName);
        
        if(lock != null) return lock.getId();
        
        for(DeadlockClass mdc : sourceClass.getSuperList()) {
            Integer ret = getLockId(identifier, mdc);
            if (ret > -1) return ret;
        }
        
        if(sourceClass.getParent() != null) {
            Integer ret = getLockId(identifier, sourceClass.getParent());
            if (ret > -1) return ret;
        }
        
        return -1;
    }
    
    private static boolean isImportEnum(String name, DeadlockClass sourceClass) {
        String names[] = name.split("\\.");
        if (names.length == 0) names = new String[]{name};
        
        return sourceClass.getEnums().contains(names[names.length - 1]);
    }
    
    private static void setImportEnums(DeadlockClass sourceClass) {
        Set<String> importedEnums = new HashSet<>();
        for (Pair<String, DeadlockClass> e : sourceClass.getImports()) {
            if (e.getRight() == null) {     // possible candidate for enum item
                String names[] = e.getLeft().split("\\.");
                if (names.length == 0) names = new String[]{e.getLeft()};
                
                DeadlockClass c;
                String path = names[0];
                int s = -1;
                
                int i = 0;
                while (true) {
                    c = AllClasses.get(path);
                    
                    if (c != null) {
                        s = path.length();
                    } else if (s > -1) {
                        if (i == names.length - 1) {
                            if (!names[i].contentEquals("*")) {
                                importedEnums.add(names[names.length - 1]);
                            } else {
                                c = AllClasses.get(path.substring(0, s));
                                if (c.isEnum()) {
                                    DeadlockEnum e1 = (DeadlockEnum) c;
                                    for (String s1 : e1.getEnumItems()) {
                                        importedEnums.add(s1);
                                    }
                                }
                            }
                        }
                        break;
                    }
                    
                    i++;
                    if (i >= names.length) break;
                    
                    path += "." + names[i];
                }
            }
        }
        
        sourceClass.setEnums(importedEnums);
    }
    
    private static Integer getPrimaryTypeOnFieldVars(String name, DeadlockClass sourceClass) {
        Integer t = sourceClass.getFieldVariable(name);
        if(t != null) return t;
        
        if (isImportEnum(name, sourceClass)) {
            return -2;
        }
        
        for(DeadlockClass mdc : sourceClass.getSuperList()) {
            t = getPrimaryTypeOnFieldVars(name, mdc);

            if(t != null) {
                return t;
            }
        }
        
        if(sourceClass.getParent() != null) return getPrimaryTypeOnFieldVars(name, sourceClass.getParent());
        return null;
    }
    
    private static Integer getPrimaryTypeFromLocalVars(String name, DeadlockFunction sourceMethod) {
        Long nameHash = DeadlockStorage.hash64(name);
        
        do {
            Set<Integer> nameTypes = sourceMethod.getLocalVariables().get(nameHash);
            if(nameTypes != null) {
                return nameTypes.size() == 1 ? nameTypes.iterator().next() : -2;    // ignore name allocated multiple times
            }
            
            sourceMethod = sourceMethod.getParent();
        } while(sourceMethod != null);
        
        return null;
    }
    
    private static Integer getPrimaryType(String name, DeadlockFunction sourceMethod, DeadlockClass sourceClass) {
        //System.out.println("trying " + name + " on " + DeadlockStorage.getCanonClassName(sourceClass));
        //System.out.println(localVars);
        
        Integer t = getPrimaryTypeFromLocalVars(name, sourceMethod);
        if(t == null) {
            t = getPrimaryTypeOnFieldVars(name, sourceClass);
        
            if(t == null) {
                // maybe basic types
                t = BasicDataTypeIds.get(LinkedTypes.getLinkedType(name));
                if(t != null && !t.equals(ElementalTypes[5])) {
                    return t;
                }
                
                // maybe class-based types
                DeadlockClass mdc = DeadlockStorage.locateClass(name, sourceClass);
                if(mdc != null) {
                    return ClassDataTypeIds.get(mdc);
                }
                
                if(sourceClass.isEnum()) {    // maybe it's an identifier defining an specific item from an enum, return self-type
                    DeadlockEnum mde = (DeadlockEnum) sourceClass;
                    
                    if(mde.containsEnumItem(name)) {
                        return ClassDataTypeIds.get(sourceClass);
                    }
                }
            }
        }
        
        if(t == null) {
            //System.out.println("FAILED TO FIND '" + name + "' ON " + DeadlockStorage.getCanonClassName(sourceClass) + ", call was " + curCall);
            return -1;
        }
        
        if(t.equals(ElementalTypes[5])) {
            lockId = getLockId(name, sourceClass);
        }
        
        return t;
    }
    
    private static Integer getRelevantType(Integer retType, Set<Integer> templateTypes, DeadlockClass c, Integer expType) {
        if(retType == -2) return retType;
        
        if(templateTypes != null && templateTypes.contains(retType)) {
            List<Integer> mTypes = c.getMaskedTypes();
            int pos;
            for(pos = 0; ; pos++) {
                if(retType.equals(mTypes.get(pos))) {
                    break;
                }
            }
            
            try {
                List<Integer> cTypes = CompoundDataTypes.get(expType);
                retType = cTypes.get(pos);
            } catch(Exception e) {  // e.g. case where objects of unknown types are being compared with equals()
                return -2;
            }
        }
        
        return retType;
    }
    
    private static Integer getLiteralType(JavaParser.LiteralContext ctx) {
        if(ctx.integerLiteral() != null) return ElementalTypes[0];
        if(ctx.floatLiteral() != null) return ElementalTypes[1];
        if(ctx.CHAR_LITERAL() != null) return ElementalTypes[2];
        if(ctx.STRING_LITERAL() != null) return ElementalTypes[3];
        if(ctx.BOOL_LITERAL() != null) return ElementalTypes[4];
        if(ctx.NULL_LITERAL() != null) return ElementalTypes[7];
        
        return -1;
    }
    
    private static Pair<DeadlockFunction, Set<Integer>> getMethodDefinitionFromClass(DeadlockClass c, String method, List<Integer> argTypes) {
        DeadlockFunction mdf = c.getMethod(false, method, argTypes);
        if(mdf != null) {
            Set<Integer> templateTypes = c.getMaskedTypeSet();
            return new Pair<>(mdf, templateTypes);
        }
        
        return null;
    }
    
    private static void getMethodImplementationsFromSubclasses(DeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<DeadlockFunction, Set<Integer>>> implementedFunctions) {
        List<DeadlockClass> subclasses = InheritanceTree.get(c);
        if(subclasses != null) {
            for(DeadlockClass mdc : subclasses) {
                getMethodImplementationsFromSubclasses(mdc, method, expType, argTypes, elementalTypes, implementedFunctions);
                
                Pair<DeadlockFunction, Set<Integer>> classMethodImplementation = getMethodDefinitionFromClass(mdc, method, argTypes);
                if(classMethodImplementation != null && !classMethodImplementation.left.isAbstract()) {
                    implementedFunctions.add(classMethodImplementation);
                }
            }
        }
    }
    
    private static Pair<Pair<DeadlockFunction, Set<Integer>>, Set<Pair<DeadlockFunction, Set<Integer>>>> getMethodImplementations(DeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
        Set<Pair<DeadlockFunction, Set<Integer>>> implementedFunctions = new LinkedHashSet<>();
        Pair<DeadlockFunction, Set<Integer>> retMethod = null;
        
        Pair<DeadlockFunction, Set<Integer>> p = getMethodDefinitionFromClass(c, method, argTypes);
        if(p != null) {
            retMethod = p;
            
            if(!p.left.isAbstract()) {
                implementedFunctions.add(p);
            }
        } else {
            // will need to access superclasses to return a method accessible by the inputted class
            
            for(DeadlockClass sup : c.getSuperList()) {
                DeadlockFunction m = sup.getMethod(true, method, argTypes);
                if(m != null) {
                    retMethod = new Pair<>(m, m.getSourceClass().getMaskedTypeSet());
                    
                    if(!m.isAbstract()) {
                        implementedFunctions.add(retMethod);
                    }
                    
                    break;
                }
            }
            
            if(retMethod == null) {
                // if all else fails, check if parent classes have this function
                DeadlockClass parent = c.getParent();
                
                if(parent != null) {
                    DeadlockFunction m = parent.getMethod(true, method, argTypes);
                    if(m != null) {
                        retMethod = new Pair<>(m, m.getSourceClass().getMaskedTypeSet());
                        
                        if(!m.isAbstract()) {
                            implementedFunctions.add(retMethod);
                        }
                    }
                }
            }
        }
        
        // this serves for developing the split-point entries for the graph (occurs when multiple classes extends a same base class, so it must cover all cases)
        if(c.isInterface() || retMethod == null || retMethod.left.isAbstract()) {
            getMethodImplementationsFromSubclasses(c, method, expType, argTypes, elementalTypes, implementedFunctions);
        }
        
        return new Pair<>(retMethod, implementedFunctions);
    }
    
    private static Pair<DeadlockFunction, Set<Integer>> getTemplateMethodDefinitionFromClass(DeadlockClass c, String method, List<Integer> argTypes) {
        DeadlockFunction mdf = c.getTemplateMethod(false, method, argTypes);
        if(mdf != null) {
            Set<Integer> templateTypes = c.getMaskedTypeSet();
            return new Pair<>(mdf, templateTypes);
        }
        
        return null;
    }
    
    private static void getTemplateMethodImplementationsFromSubclasses(DeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<DeadlockFunction, Set<Integer>>> implementedFunctions) {
        List<DeadlockClass> subclasses = InheritanceTree.get(c);
        if(subclasses != null) {
            for(DeadlockClass mdc : subclasses) {
                getTemplateMethodImplementationsFromSubclasses(mdc, method, expType, argTypes, elementalTypes, implementedFunctions);
            
                Pair<DeadlockFunction, Set<Integer>> classMethodImplementation = getTemplateMethodDefinitionFromClass(mdc, method, argTypes);
                if(classMethodImplementation != null && !classMethodImplementation.left.isAbstract()) {
                    implementedFunctions.add(classMethodImplementation);
                }
            }
        }
    }
    
    private static Pair<Pair<DeadlockFunction, Set<Integer>>, Set<Pair<DeadlockFunction, Set<Integer>>>> getTemplateMethodImplementations(DeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
        Set<Pair<DeadlockFunction, Set<Integer>>> implementedFunctions = new LinkedHashSet<>();
        Pair<DeadlockFunction, Set<Integer>> retMethod = null;
        
        Pair<DeadlockFunction, Set<Integer>> p = getTemplateMethodDefinitionFromClass(c, method, argTypes);
        if(p != null) {
            retMethod = p;
            
            if(!p.left.isAbstract()) {
                implementedFunctions.add(p);
            }
        } else {
            // will need to access superclasses to return a method accessible by the inputted class
            
            for(DeadlockClass sup : c.getSuperList()) {
                DeadlockFunction m = sup.getTemplateMethod(true, method, argTypes);
                if(m != null) {
                    retMethod = new Pair<>(m, m.getSourceClass().getMaskedTypeSet());
                    
                    if(!m.isAbstract()) {
                        implementedFunctions.add(retMethod);
                    }
                    
                    break;
                }
            }
            
            if(retMethod == null) {
                // if all else fails, check if parent classes have this function
                DeadlockClass parent = c.getParent();
                
                if(parent != null) {
                    DeadlockFunction m = parent.getTemplateMethod(true, method, argTypes);
                    if(m != null) {
                        retMethod = new Pair<>(m, m.getSourceClass().getMaskedTypeSet());
                        
                        if(!m.isAbstract()) {
                            implementedFunctions.add(retMethod);
                        }
                    }
                }
            }
        }
        
        // this serves for developing the split-point entries for the graph (occurs when multiple classes extends a same base class, so it must cover all cases)
        if(c.isInterface() || retMethod == null || retMethod.left.isAbstract()) {
            getTemplateMethodImplementationsFromSubclasses(c, method, expType, argTypes, elementalTypes, implementedFunctions);
        }
        
        return new Pair<>(retMethod, implementedFunctions);
    }
    
    private static Set<Integer> getReturnType(DeadlockGraphMethod node, String method, Integer expType, List<Integer> argTypes, JavaParser.MethodCallContext methodCall) {
        Set<Integer> ret = new HashSet<>();
        DeadlockClass c = getClassFromType(expType);
        
        DeadlockFunction retMethod = null;
        Set<Integer> retTemplateTypes = null;
        
        Set<List<Pair<DeadlockFunction, Set<Integer>>>> allMethodImplementations = new LinkedHashSet<>();
        Set<Pair<Pair<DeadlockFunction, Set<Integer>>, Set<Pair<DeadlockFunction, Set<Integer>>>>> metImpl = new HashSet<>();
        
        //System.out.println("requiring return from " + expType + " method " + method);
        
        if(c == null) {
            List<Integer> cTypes = CompoundDataTypes.get(expType);
            if(cTypes != null) {
                int t = cTypes.get(cTypes.size() - 1);
                if (t == -2) {
                    ret.add(-2);
                    return ret;
                }
                
                c = getClassFromType(t);
                if(c == null) {
                    ret.add(-1);
                    return ret;
                }
                
                metImpl.add(getTemplateMethodImplementations(c, method, expType, argTypes, ElementalDataTypes));
            } else {
                ret.add(-1);
                return ret;
            }
        } else {
            if(c.isEnum()) {
                if(method.contentEquals("values")) {    // this will return a Collection of enums, since Collection is being ignored, so this is
                    ret.add(-2);
                    return ret;
                } else if(method.contentEquals("ordinal")) {
                    ret.add(ElementalTypes[0]);
                    return ret;
                } else if(method.contentEquals("name")) {
                    ret.add(ElementalTypes[3]);
                    return ret;
                } else if(method.contentEquals("equals")) {
                    ret.add(ElementalTypes[4]);
                    return ret;
                }
            } else if(c.isInterface()) {
                for(DeadlockClass sc : InheritanceTree.get(c)) {
                    metImpl.add(getMethodImplementations(sc, method, expType, argTypes, ElementalDataTypes));
                }
            }
            
            metImpl.add(getMethodImplementations(c, method, expType, argTypes, ElementalDataTypes));
        }
        
        for(Pair<Pair<DeadlockFunction, Set<Integer>>, Set<Pair<DeadlockFunction, Set<Integer>>>> i : metImpl) {
            allMethodImplementations.add(new LinkedList<>(i.right));
        }
        
        //System.out.println("conflicting funct " + mdf.getName() + " on " + DeadlockStorage.getCanonClassName(mdf.getSourceClass()));
        //System.out.println("adding node " + fid);
        
        /*
        if(node.getId() == 986) {
            System.out.println("DEBUG DO_gachapon");
            
            for(int i = 0; i < allMethodImplementations.size(); i++) {
                DeadlockFunction mdf = allMethodImplementations.get(i);
                
                System.out.println("  " + mdf.getName() + " src " + DeadlockStorage.getCanonClassName(mdf.getSourceClass()));
            }
        }
        */
        
        if(allMethodImplementations.isEmpty()) {
            System.out.println("[Warning] EMPTY method node: " + methodCall.getText() + " @ " + method + " from " + DeadlockStorage.getCanonClassName(c));
        }
        
        if (!allMethodImplementations.isEmpty()) {
            for(List<Pair<DeadlockFunction, Set<Integer>>> mi : allMethodImplementations) {
                DeadlockGraphEntry entry = new DeadlockGraphEntry();

                for(Pair<DeadlockFunction, Set<Integer>> mip : mi) {
                    DeadlockFunction mdf = mip.left;
                    //Set<Integer> tt = mi.right;

                    Integer fid = GraphFunctionIds.get(mdf);
                    entry.addGraphEntryPoint(new DeadlockGraphNodeCall(fid));
                }
                
                node.addGraphEntry(entry);
            }
        }
        
        Set<Integer> retTypes = new HashSet<>();
        for (Pair<Pair<DeadlockFunction, Set<Integer>>, Set<Pair<DeadlockFunction, Set<Integer>>>> i : metImpl) {
            for (Pair<DeadlockFunction, Set<Integer>> p : i.getRight()) {
                retMethod = p.left;
                retTemplateTypes = p.right;

                retTypes.add(getRelevantType(retMethod.getReturn(), retTemplateTypes, c, expType));
            }
        }
        ret.addAll(retTypes);
        
        return ret;
    }
    
    private static Integer getPreparedReturnType(String methodName, Integer thisType) {
        switch(methodName) {
            case "isEmpty":
            case "equals":
            case "equalsIgnoreCase":
            case "contains":
                return ElementalTypes[4];
            
            case "valueOf":
            case "toString":
            case "toLowerCase":
            case "toUpperCase":
            case "trim":
            case "substring":
            case "getKey":
            case "getValue":
            case "getClass":
            case "iterator":
                return -2;
            
            case "hashCode":
            case "compareTo":
                return ElementalTypes[0];
            
            case "clone":
                return thisType;
            
            case "invokeFunction":
                return -2;
                
            case "add":
            case "put":
            case "clear":
                if(AbstractDataTypes.get(thisType) != DeadlockAbstractType.NON_ABSTRACT) {
                    return -2;
                }
                
            default:
                if(methodName.endsWith("alue")) {   // intValue, floatValue, shortValue...
                    return -2;
                }
                
                return -1;
        }
    }
    
    private static Set<Integer> getMethodReturnType(DeadlockGraphMethod node, Integer classType, JavaParser.MethodCallContext methodCall, DeadlockFunction sourceMethod, DeadlockClass sourceClass) {
        Set<Integer> retTypes = new HashSet<>();
        
        if(classType == -2) {
            retTypes.add(-2);
            return retTypes;
        }
        
        //System.out.println("CALL METHODRETURNTYPE for " + classType + " methodcall " + methodCall.getText());
        List<Integer> argTypes = getArgumentTypes(node, methodCall.expressionList(), sourceMethod, sourceClass);
        String methodName = methodCall.IDENTIFIER().getText();
        
        if(!ReflectedClasses.containsKey(classType)) {
            DeadlockAbstractType absType = AbstractDataTypes.get(classType);
            if(absType != null) {
                Integer ret = evaluateAbstractFunction(node, methodName, argTypes, classType, absType);
                retTypes.add(ret);
                
                //if(ret == -1 && absType != DeadlockAbstractType.LOCK) System.out.println("SOMETHING OUT OF CALL FOR " + methodCall.IDENTIFIER().getText() + " ON " + absType /*+ dataNames.get(expType)*/);
                return retTypes;
            } else {
                retTypes = getReturnType(node, methodName, classType, argTypes, methodCall);
                if(retTypes.contains(-1)) {
                    retTypes.remove(-1);
                    retTypes.add(getPreparedReturnType(methodName, classType)); // test for common function names widely used, regardless of data type
                }

                return retTypes;
            }
        } else {
            // follows the return-type pattern for the reflected classes, that returns an specific type if a method name has been recognized, returns the default otherwise
            Pair<Integer, Map<String, Integer>> reflectedData = ReflectedClasses.get(classType);
            
            if(methodName.contentEquals("toString")) {
                retTypes.add(ElementalTypes[3]);
            } else {
                DeadlockAbstractType absType = AbstractDataTypes.get(classType);
                if(absType != null) {
                    if (absType == DeadlockAbstractType.LOCK || absType == DeadlockAbstractType.SCRIPT) {
                        Integer ret = evaluateAbstractFunction(node, methodName, argTypes, classType, absType);
                        retTypes.add(ret);

                        //if(ret == -1 && absType != DeadlockAbstractType.LOCK) System.out.println("SOMETHING OUT OF CALL FOR " + methodCall.IDENTIFIER().getText() + " ON " + absType /*+ dataNames.get(expType)*/);
                        return retTypes;
                    }
                }
                
                Integer ret = reflectedData.right.get(methodName);
                if(ret == null) ret = reflectedData.left;
                retTypes.add(ret);
            }
        }
        
        return retTypes;
    }
    
    private static Integer getPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
        if(ctx.INT() != null || ctx.SHORT() != null || ctx.LONG() != null || ctx.BYTE() != null) return ElementalTypes[0];
        if(ctx.FLOAT() != null || ctx.DOUBLE() != null) return ElementalTypes[1];
        if(ctx.CHAR() != null) return ElementalTypes[2];
        if(ctx.BOOLEAN() != null) return ElementalTypes[4];
        
        return -2;
    }
    
    private static Integer getDereferencedType(String derTypeName, DeadlockClass derClass) {
        Integer derType = DereferencedDataTypes.get(derTypeName);
        if(derType != null) return derType;
        
        if (derClass == null) derType = BasicDataTypeIds.get(derTypeName);
        else derType = ClassDataTypeIds.get(derClass);
        
        DereferencedDataTypes.put(derTypeName, derType);
        return derType;
    }
    
    private static void skimArrayInitializer(JavaParser.ArrayInitializerContext ainiCtx, DeadlockGraphMethod node, DeadlockFunction sourceMethod, DeadlockClass sourceClass) {
        // just process expressions inside and add them to the function graph, disregard return values
        
        for(JavaParser.VariableInitializerContext var : ainiCtx.variableInitializer()) {
            if(var.expression() != null) {
                parseMethodCalls(node, var.expression(), sourceMethod, sourceClass);
            } else if(var.arrayInitializer() != null) {
                skimArrayInitializer(var.arrayInitializer(), node, sourceMethod, sourceClass);
            }
        }
    }
    
    private static Set<Integer> parseMethodCalls(DeadlockGraphMethod node, JavaParser.ExpressionContext call, DeadlockFunction sourceMethod, DeadlockClass sourceClass) {
        Set<Integer> metRetTypes = parseMethodCalls(node, call, sourceMethod, sourceClass, true);
        
        Set<Integer> retTypes = new HashSet<>();
        for (Integer ret : metRetTypes) {
            if(ret == -1 && metRetTypes.size() == 1) {
                // apply a poor-man's filter for missed cases
                String expr = call.getText(), base;

                int idx = expr.indexOf('.');
                if(idx != -1) {
                    base = expr.substring(0, idx);
                } else {
                    base = expr;

                    idx = base.indexOf('(');
                    if(idx != -1) {
                        base = base.substring(0, idx);
                    }
                }

                String methodName = "";
                if(call.bop != null && call.methodCall() != null) {
                    methodName = call.methodCall().IDENTIFIER().getText();
                }

                if(base.contentEquals("Math")) {
                    if(methodName.contentEquals("floor") || methodName.contentEquals("ceil")) {
                        retTypes.add(ElementalTypes[0]);
                        continue;
                    }

                    retTypes.add(ElementalTypes[1]);
                    continue;
                } else if(base.contentEquals("System") || base.contentEquals("Arrays") || base.contentEquals("Collections") || base.contentEquals("Data")) {
                    retTypes.add(-1);
                    continue;
                } else {
                    ret = getPreparedReturnType(methodName, ClassDataTypeIds.get(sourceClass));
                    if(ret != -1) {
                        retTypes.add(ret);
                        continue;
                    }
                }

                System.out.println("[Warning] COULD NOT DETERMINE " + call.getText() + " on src " + DeadlockStorage.getCanonClassName(sourceClass) + ", ret " + ret);
            }
        }
        
        metRetTypes.remove(-1);
        retTypes.addAll(metRetTypes);
        
        return retTypes;
    }
    
    private static boolean isIgnoredRange(Integer type) {
        return type >= IgnoredDataRange.left && type < IgnoredDataRange.right;
    }
    
    private static boolean isIgnoredType(Integer type) {
        if(isIgnoredRange(type)) {
            return true;
        }
        
        List<Integer> cType = CompoundDataTypes.get(type);
        if(cType != null) {
            for(Integer i : cType) {
                if(isIgnoredType(i)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static Integer getThisType(DeadlockClass sourceClass) {
        if (sourceClass == null) return null;
        
        Integer cid = ClassDataTypeIds.get(sourceClass);
        if (cid != null) return cid;

        for (DeadlockClass mdc : sourceClass.getSuperList()) {
            cid = getThisType(mdc);
            if (cid != null) return cid;
        }
        
        cid = getThisType(sourceClass.getParent());
        return cid;
    }
    
    private static Set<Integer> parseMethodCalls(DeadlockGraphMethod node, JavaParser.ExpressionContext call, DeadlockFunction sourceMethod, DeadlockClass sourceClass, boolean filter) {
        JavaParser.ExpressionContext curCtx = call;
        
        Set<Integer> ret = new HashSet<>();
        
        if(curCtx.bop != null) {
            String bopText = curCtx.bop.getText();
            
            if(bopText.contentEquals(".")) {
                JavaParser.ExpressionContext expCtx = curCtx.expression(0);
                
                Set<Integer> metRetTypes = parseMethodCalls(node, expCtx, sourceMethod, sourceClass);
                if(metRetTypes.size() > 0) {
                    for (Integer expType : metRetTypes) {
                        if(expType == null) System.out.println("null on " + expCtx.getText() + " src is " + DeadlockStorage.getCanonClassName(sourceClass));
                        if (curCtx.THIS() == null) {
                            if(expType != -1) {
                                if(expType != -2) {     // expType -2 means the former expression type has been excluded from the search
                                    if(curCtx.methodCall() != null) {
                                        Set<Integer> r = getMethodReturnType(node, expType, curCtx.methodCall(), sourceMethod, sourceClass);
                                        ret.addAll(r);

                                        if(ret.contains(-1)) {
                                            DeadlockClass c = getClassFromType(expType);
                                            if(c != null && c.isInterface()) {  // it's an interface, there's no method implementation to be found there
                                                ret.remove(-1);
                                                ret.add(-2);
                                            }
                                        }

                                        continue;
                                    } else if(curCtx.IDENTIFIER() != null) {
                                        if(isIgnoredType(expType)) {
                                            ret.add(-2);
                                            continue;
                                        }

                                        DeadlockClass c = getClassFromType(expType);
                                        Set<Integer> templateTypes = null;

                                        if(c == null) {
                                            List<Integer> cTypes = CompoundDataTypes.get(expType);
                                            if(cTypes != null) {
                                                c = getClassFromType(cTypes.get(cTypes.size() - 1));

                                                if(c == null) {
                                                    //System.out.println("GFAILED @ " + cTypes.get(cTypes.size() - 1));
                                                } else {
                                                    templateTypes = c.getMaskedTypeSet();
                                                }
                                            }

                                            if(c == null) {
                                                String typeName = BasicDataTypes.get(expType);

                                                if(typeName != null && typeName.charAt(typeName.length() - 1) == ']') {
                                                    if(curCtx.IDENTIFIER().getText().contentEquals("length")) {
                                                        ret.add(ElementalTypes[0]);
                                                        continue;
                                                    }
                                                }

                                                //System.out.println("FAILED @ " + expType);
                                                System.out.println("[Warning] No datatype found for " + curCtx.IDENTIFIER() + " on expression " + curCtx.getText() + " srcclass " + DeadlockStorage.getCanonClassName(sourceClass) + " detected exptype " + expType);
                                                ret.add(-2);
                                                continue;
                                            }
                                        } else {
                                            if(c.isEnum()) {    // it's an identifier defining an specific item from an enum, return self-type
                                                if(curCtx.IDENTIFIER().getText().contentEquals("length")) {
                                                    ret.add(ElementalTypes[0]);
                                                    continue;
                                                }

                                                ret.add(expType);
                                                continue;
                                            }

                                            templateTypes = c.getMaskedTypeSet();
                                        }
                                        
                                        String element = curCtx.IDENTIFIER().getText();
                                        
                                        Integer type = getPrimaryTypeOnFieldVars(element, c);
                                        if(type == null) {
                                            DeadlockClass mdc = DeadlockStorage.locateInternalClass(element, c);  // element could be a private class reference
                                            if(mdc != null) {
                                                ret.add(ClassDataTypeIds.get(mdc));
                                                continue;
                                            }

                                            //System.out.println("SOMETHING OUT OF CALL FOR FIELD " + curCtx.IDENTIFIER().getText() + " ON " + DeadlockStorage.getCanonClassName(c));
                                            ret.add(-1);
                                            continue;
                                        }

                                        ret.add(getRelevantType(type, templateTypes, c, expType));
                                        continue;
                                    } else if(curCtx.THIS() != null) {
                                        ret.add(expType);
                                        continue;
                                    } else if(curCtx.primary() != null) {
                                        if(curCtx.primary().CLASS() != null) {
                                            ret.add(expType);
                                            continue;
                                        }
                                    }
                                } else {
                                    ret.add(-2);
                                    continue;
                                }
                            }
                        } else {
                            ret.add(expType);
                            continue;
                        }
                    }
                    
                    return ret;
                }
            } else if(bopText.contentEquals("+")) {
                // must decide between string concatenation of numeric data types
                
                Set<Integer> s1 = parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
                Set<Integer> s2 = parseMethodCalls(node, curCtx.expression(1), sourceMethod, sourceClass);
                
                for (Integer ret1 : s1) {
                    for (Integer ret2 : s2) {
                        Integer expType = (ret1.equals(ElementalTypes[3]) || ret2.equals(ElementalTypes[3])) ? ElementalTypes[3] : (ret1 != -1 ? ret1 : ret2);
                        ret.add(expType);
                    }
                }
                return ret;
            } else if(bopText.contentEquals("-") || bopText.contentEquals("*") || bopText.contentEquals("/") || bopText.contentEquals("%") || bopText.contentEquals("&") || bopText.contentEquals("^") || bopText.contentEquals("|")) {
                // the resulting type is the same from the left expression, try right if left is undecisive
                
                Set<Integer> s1 = parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
                Set<Integer> s2 = parseMethodCalls(node, curCtx.expression(1), sourceMethod, sourceClass);
                
                ret.addAll(!s1.contains(-1) ? s1 : s2);
                return ret;
            } else if(bopText.contentEquals("?")) {
                Set<Integer> s1 = parseMethodCalls(node, curCtx.expression(1), sourceMethod, sourceClass);
                Set<Integer> s2 = parseMethodCalls(node, curCtx.expression(2), sourceMethod, sourceClass);
                
                ret.addAll(!s1.contains(-1) ? s1 : s2);
                return ret;
            } else if(curCtx.expression().size() == 2 || curCtx.typeType() != null) {  // boolean-type expression
                ret.add(ElementalTypes[4]);
                return ret;
            }
        } else if(curCtx.prefix != null || curCtx.postfix != null) {
            if(curCtx.prefix != null && curCtx.prefix.getText().contentEquals("!")) {
                ret.add(ElementalTypes[4]);
                return ret;
            }
            
            parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
            return ret;
        } else if(curCtx.getChild(curCtx.getChildCount() - 1).getText().contentEquals("]")) {
            JavaParser.ExpressionContext outer = curCtx.expression(0);
            JavaParser.ExpressionContext inner = curCtx.expression(1);
            
            for (Integer outerType : parseMethodCalls(node, outer, sourceMethod, sourceClass)) {
                DeadlockClass outerClass = ClassDataTypes.get(outerType);
                String outerName;
                if (outerClass != null) {
                    outerName = DeadlockStorage.getClassPath(outerClass);
                } else {
                    outerName = BasicDataTypes.get(outerType);
                    
                    outerClass = DeadlockStorage.locateClass(outerName, sourceClass);
                    if (outerClass != null) outerType = ClassDataTypeIds.get(outerClass);
                }
                
                if (outerName.endsWith("]")) outerName = outerName.substring(0, outerName.lastIndexOf("["));
                
                Integer derType;
                if (outerName.endsWith("]")) {
                    derType = getDereferencedType(outerName, outerClass);
                } else {
                    DeadlockClass mdc = DeadlockStorage.locateClass(outerName, sourceClass);
                    if (mdc != null) {
                        derType = ClassDataTypeIds.get(mdc);
                    } else if (BasicDataTypeIds.containsKey(outerName)) {
                        derType = BasicDataTypeIds.get(outerName);
                    } else {
                        derType = -2;
                    }
                }
                
                ret.add(derType);
            }
            
            return ret;
        } else if(curCtx.primary() != null) {
            JavaParser.PrimaryContext priCtx = curCtx.primary();
            
            if(priCtx.IDENTIFIER() != null) {
                Integer r = getPrimaryType(priCtx.IDENTIFIER().getText(), sourceMethod, sourceClass);
                ret.add(r);
                return ret;
            } else if(priCtx.expression() != null) {
                return parseMethodCalls(node, priCtx.expression(), sourceMethod, sourceClass);
            } else if(priCtx.literal() != null) {
                ret.add(getLiteralType(priCtx.literal()));
                return ret;
            } else if(priCtx.THIS() != null) {
                ret.add(getThisType(sourceClass));
                return ret;
            } else if(priCtx.CLASS() != null) {
                ret.add(-2);
                return ret;
            } else if(priCtx.SUPER() != null) {
                if(!sourceClass.getSuperList().isEmpty()) {
                    ret.add(ClassDataTypeIds.get(sourceClass.getSuperList().get(0)));
                    return ret;
                } else {
                    ret.add(-2);
                    return ret;
                }
            }
        } else if(curCtx.getChildCount() == 4 && curCtx.getChild(curCtx.getChildCount() - 2).getText().contentEquals(")")) {   // '(' typeType ')' expression
            parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
            String typeText = curCtx.typeType().getText();
            
            DeadlockClass c = DeadlockStorage.locateClass(typeText, sourceClass);
            if(c != null) {
                ret.add(ClassDataTypeIds.get(c));
                return ret;
            }
            
            Integer i = BasicDataTypeIds.get(typeText);
            ret.add((i != null) ? i : -2);
            return ret;
        } else if(curCtx.NEW() != null) {
            // evaluate functions inside just for the sake of filling the graph
            
            JavaParser.ClassCreatorRestContext cresCtx = curCtx.creator().classCreatorRest();
            if(cresCtx != null) {
                getArgumentTypes(node, cresCtx.arguments().expressionList(), sourceMethod, sourceClass);
            } else {
                JavaParser.ArrayCreatorRestContext aresCtx = curCtx.creator().arrayCreatorRest();
                
                if(aresCtx != null) {
                    if(aresCtx.arrayInitializer() != null) {
                        skimArrayInitializer(aresCtx.arrayInitializer(), node, sourceMethod, sourceClass);
                    }
                    
                    for(JavaParser.ExpressionContext expr : aresCtx.expression()) {
                        parseMethodCalls(node, expr, sourceMethod, sourceClass);
                    }
                }
            }
            
            JavaParser.CreatedNameContext nameCtx = curCtx.creator().createdName();
            if(nameCtx.primitiveType() == null) {
                if(nameCtx.IDENTIFIER().size() > 1) {
                    ret.add(-2);
                    return ret;
                }
                
                String idName = nameCtx.IDENTIFIER(0).getText();
                DeadlockClass c = DeadlockStorage.locateClass(idName, sourceClass);
                
                if(c != null && c.getMaskedTypeSet() == null) {     // if the creator is instancing a compound data type, let it throw a -2
                    ret.add(ClassDataTypeIds.get(c));
                    return ret;
                } else {
                    ret.add(-2);
                    return ret;
                }
            } else {
                ret.add(getPrimitiveType(nameCtx.primitiveType()));
                return ret;
            }
        } else if(curCtx.methodCall() != null) {
            ret.addAll(getMethodReturnType(node, ClassDataTypeIds.get(sourceClass), curCtx.methodCall(), sourceMethod, sourceClass));
            return ret;
        } else if(curCtx.expression().size() == 2) {    // expression ('<' '<' | '>' '>' '>' | '>' '>') expression
            ret.add(ElementalTypes[0]);
            return ret;
        }
        
        ret.add(-1);
        return ret;
    }
    
    private static void parseMethodNode(DeadlockFunction method, DeadlockClass sourceClass) {
        DeadlockGraphMethod node = GraphFunctions.get(method);
        
        for(JavaParser.ExpressionContext call : method.getMethodCalls()) {
            parseMethodCalls(node, call, method, sourceClass);
        }
    }
    
    private static void reinstanceCachedMaps(DeadlockStorage metadata) {
        for(Entry<String, Integer> e : metadata.getBasicDataTypes().entrySet()) {
            Integer i = e.getValue();
            String s = e.getKey();
            
            BasicDataTypes.put(i, s);
            EveryDataTypes.put(i, s);
            EveryDataTypeIds.put(s, i);
            
            DeadlockAbstractType aType = DeadlockAbstractType.getValue(s);
            if(aType != DeadlockAbstractType.NON_ABSTRACT) {
                AbstractDataTypes.put(i, aType);
            }
        }
        
        ClassDataTypeIds = metadata.getClassDataTypes();
        for(Entry<DeadlockClass, Integer> e : ClassDataTypeIds.entrySet()) {
            Integer i = e.getValue();
            DeadlockClass d = e.getKey();
            
            ClassDataTypes.put(i, d);
            EveryDataTypes.put(i, DeadlockStorage.getCanonClassName(d));
            EveryDataTypeIds.put(DeadlockStorage.getCanonClassName(d), i);
            
            DeadlockAbstractType aType = DeadlockAbstractType.getValue(d.getName());
            if(aType != DeadlockAbstractType.NON_ABSTRACT) {
                AbstractDataTypes.put(i, aType);
            }
        }
        
        for(Entry<List<Integer>, Integer> e : metadata.getCompoundDataTypes().entrySet()) {
            Integer i = e.getValue();
            List<Integer> d = e.getKey();
            
            CompoundDataTypes.put(i, d);
            
            DeadlockAbstractType absType = AbstractDataTypes.get(d.get(d.size() - 1));
            if(absType != DeadlockAbstractType.NON_ABSTRACT) {
                AbstractDataTypes.put(i, absType);
            }
        }
        
        BasicDataTypeIds = new HashMap<>();
        for (Entry<Integer, String> e : BasicDataTypes.entrySet()) {
            BasicDataTypeIds.put(e.getValue(), e.getKey());
        }
    }
    
    private static void generateMethodNodes(Map<String, Map<String, DeadlockClass>> packageClasses) {
        for(Map<String, DeadlockClass> m : packageClasses.values()) {
            for(DeadlockClass c : m.values()) {
                for(DeadlockFunction f : c.getMethods()) {
                    createGraphFunction(f);
                }
            }
        }
    }
    
    private static void generateSuperReferences(Map<String, Map<String, DeadlockClass>> packageClasses) {
        for(Map<String, DeadlockClass> m : packageClasses.values()) {
            for(DeadlockClass c : m.values()) {
                Integer classId = ClassDataTypeIds.get(c);
                
                for(DeadlockClass s : c.getSuperList()) {
                    Set<Integer> inherits = SuperClasses.get(classId);
                    
                    if(inherits == null) {
                        inherits = new LinkedHashSet<>();
                        SuperClasses.put(classId, inherits);
                    }
                    
                    inherits.add(ClassDataTypeIds.get(s));
                }
            }
        }
    }
    
    private static void parseMethodNodes(Map<String, Map<String, DeadlockClass>> packageClasses) {
        for(Map<String, DeadlockClass> m : packageClasses.values()) {
            for(DeadlockClass c : m.values()) {
                for(DeadlockFunction f : c.getMethods()) {
                    parseMethodNode(f, c);
                }
            }
        }
    }
    
    private static void parseRunnableMethodNodes() {
        for(DeadlockFunction f : RunnableMethods) {
            createGraphFunction(f);
            parseMethodNode(f, f.getSourceClass());
        }
    }
    
    public static Set<Integer> generateEnumReferences() {
        Set<Integer> enumIds = new HashSet<>();
        
        for(Map<String, DeadlockClass> m : PublicClasses.values()) {
            for(DeadlockClass c : m.values()) {
                if(c.isEnum()) {
                    enumIds.add(ClassDataTypeIds.get(c));
                }
            }
        }
        
        return enumIds;
    }
    
    private static Integer defineObjectSet() {
        Integer objectId = EveryDataTypeIds.get("Object");
        Integer setId = EveryDataTypeIds.get("Set");
        
        for (Entry<Integer, List<Integer>> e : CompoundDataTypes.entrySet()) {
            if (e.getValue().get(0).equals(objectId) && e.getValue().get(1).equals(setId)) {
                return e.getKey();
            }
        }
        
        return -1;
    }
    
    private static void includeAllClassesInternal(Map<String, Map<String, DeadlockClass>> map, boolean isPrivate) {
        if(!isPrivate) {
            for (Entry<String, Map<String, DeadlockClass>> e : map.entrySet()) {
                String path = e.getKey();

                for (Entry<String, DeadlockClass> f : e.getValue().entrySet()) {
                    AllClasses.put(path + f.getKey(), f.getValue());
                }    
            }
        } else {
            for (Entry<String, Map<String, DeadlockClass>> e : map.entrySet()) {
                String path = e.getKey();
                int idx = path.lastIndexOf('.');
                path = path.substring(0, idx + 1);

                for (Entry<String, DeadlockClass> f : e.getValue().entrySet()) {
                    AllClasses.put(path + f.getKey(), f.getValue());
                }    
            }
        }
    }
    
    private static void includeAllClasses() {
        includeAllClassesInternal(PublicClasses, false);
        includeAllClassesInternal(PrivateClasses, true);
    }
    
    public static DeadlockGraph generateSourceGraph(DeadlockStorage metadata) {
        reinstanceCachedMaps(metadata);
        objectSetId = defineObjectSet();
        
        PublicClasses = metadata.getPublicClasses();
        PrivateClasses = metadata.getPrivateClasses();
        
        AllClasses = new HashMap<>();
        includeAllClasses();
        
        for (DeadlockClass c : AllClasses.values()) {
            setImportEnums(c);
        }
        
        Locks = metadata.getLocks();
        ElementalDataTypes = metadata.getElementalDataTypes();
        ElementalTypes = metadata.getElementalTypes();
        ReflectedClasses = metadata.getReflectedClasses();
        InheritanceTree = metadata.getInheritanceTree();
        IgnoredDataRange = metadata.getIgnoredDataRange();
        
        RunnableMethods = metadata.getRunnableMethods();
        
        EnumDataTypes = generateEnumReferences();
        
        generateSuperReferences(PublicClasses);
        generateSuperReferences(PrivateClasses);
        
        generateMethodNodes(PublicClasses);
        generateMethodNodes(PrivateClasses);
        
        DeadlockFunction.installTypeReferences(ElementalDataTypes, CompoundDataTypes, SuperClasses, EnumDataTypes, IgnoredDataRange, ElementalTypes[0]);
        
        try {
            parseMethodNodes(PublicClasses);
            parseMethodNodes(PrivateClasses);
            
            parseRunnableMethodNodes();
        } catch (Exception e) {
            e.printStackTrace();
            
            //dumpMemory();
            
            
            try {
            Thread.sleep(10000000);
            } catch(Exception ex ) {}
        }
        
        return new DeadlockGraph(GraphFunctionIds, GraphFunctions);
    }
    
    private static List<Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>>> generateDumpEntries() {
        List<Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>>> dumpData = new LinkedList<>();
        
        for(Entry<DeadlockFunction, Integer> e : GraphFunctionIds.entrySet()) {
            DeadlockGraphMethod g = GraphFunctions.get(e.getKey());
            
            dumpData.add(new Pair<>(e.getValue(), new Pair<>(e.getKey(), g)));
        }
        
        Collections.sort(dumpData, new Comparator<Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>>>() {
            @Override
            public int compare(Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>> p1, Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>> p2) {
                int v = 0;//p1.right.right.getSourceName().compareTo(p1.right.right.getSourceName());
                if(v == 0) v = p1.left.compareTo(p2.left);
                
                return v;
            }
        });
        
        return dumpData;
    }
    
    public static String translateId(Map<Integer, String> map, Integer i) {
        if(map.get(i) == null) return "(" + i + ")";
        return map.get(i);
    }
    
    public static String translateId(Map<Integer, String> map, List<Integer> list) {
        String s = "[";
        
        for(Integer i : list) {
            if(map.get(i) == null) {
                s += "(" + i + "), ";
            } else {
                s += map.get(i) + ", ";
            }
        }
        
        s += "]";
        return s;
    }
    
    public static Map<DeadlockFunction, DeadlockGraphMethod> getGraphEntries() {
        return GraphFunctions;
    }
    
    public static Map<String, DeadlockLock> getGraphLocks() {
        return Locks;
    }
    
    public static void dumpMemory() {
        System.out.println("ClassDataTypeIds :::::");
        for(Entry<DeadlockClass, Integer> e : ClassDataTypeIds.entrySet()) {
            System.out.println(DeadlockStorage.getCanonClassName(e.getKey()) + " " + e.getValue());
        }
        
        System.out.println("\n\nClassDataTypes :::::");
        for(Entry<Integer, DeadlockClass> e : ClassDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + DeadlockStorage.getCanonClassName(e.getValue()));
        }
        
        System.out.println("\n\nBasicDataTypeIds :::::");
        for(Entry<String, Integer> e : BasicDataTypeIds.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        
        System.out.println("\n\nBasicDataTypes :::::");
        for(Entry<Integer, String> e : BasicDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        
        System.out.println("\n\nCompoundDataTypeIds :::::");
        for(Entry<Integer, List<Integer>> e : CompoundDataTypes.entrySet()) {
            System.out.println(translateId(EveryDataTypes, e.getKey()) + " " + translateId(EveryDataTypes, e.getValue()));
        }
        
        System.out.println("\n\nAbstractDataTypes :::::");
        for(Entry<Integer, DeadlockAbstractType> e : AbstractDataTypes.entrySet()) {
            System.out.println(translateId(EveryDataTypes, e.getKey()) + " " + e.getValue());
        }
        
        System.out.println("\n\nDereferencedDataTypes :::::");
        for(Entry<String, Integer> e : DereferencedDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + translateId(EveryDataTypes, e.getValue()));
        }
    }
    
    public static void dumpGraph() {
        List<Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>>> dumpData = generateDumpEntries();
        
        for(Pair<Integer, Pair<DeadlockFunction, DeadlockGraphMethod>> e : dumpData) {
            List<DeadlockGraphEntry> list = e.right.right.getEntryList();
            
            if(list.size() > 0) {
                System.out.println(DeadlockStorage.getCanonClassName(e.right.left.getSourceClass()) + " >> " + e.right.left.getName() + " id: " + e.left);
                for(DeadlockGraphEntry n : list) {
                    System.out.print(n.getGraphEntryPoints() + " ");
                }
                System.out.println("\n");
            }
        }
    }
    
}
