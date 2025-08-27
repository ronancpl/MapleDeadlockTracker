/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker;

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
import mapledeadlocktracker.containers.MapleDeadlockClass;
import mapledeadlocktracker.containers.MapleDeadlockEnum;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockLock;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.containers.Pair;
import mapledeadlocktracker.graph.MapleDeadlockAbstractType;
import mapledeadlocktracker.graph.MapleDeadlockGraphEntry;
import mapledeadlocktracker.graph.MapleDeadlockGraphNodeCall;
import mapledeadlocktracker.graph.MapleDeadlockGraphNodeLock;
import mapledeadlocktracker.graph.MapleDeadlockGraphNodeScript;
import mapledeadlocktracker.graph.MapleDeadlockGraphMethod;
import mapledeadlocktracker.strings.MapleLinkedTypes;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphMaker {
    private static Map<String, Map<String, MapleDeadlockClass>> maplePublicClasses;
    private static Map<String, Map<String, MapleDeadlockClass>> maplePrivateClasses;
    private static Map<String, MapleDeadlockClass> mapleAllClasses;
    private static Map<String, MapleDeadlockLock> mapleLocks;
    
    private static Integer objectSetId;
    private static Map<Integer, Integer> mapleElementalDataTypes;
    private static Integer[] mapleElementalTypes;
    private static Set<Integer> mapleEnumDataTypes;
    
    private static Map<MapleDeadlockClass, Integer> mapleClassDataTypeIds;
    private static Map<String, Integer> mapleBasicDataTypeIds;
    
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> mapleReflectedClasses;
    private static Map<MapleDeadlockClass, List<MapleDeadlockClass>> mapleInheritanceTree;
    private static Pair<Integer, Integer> mapleIgnoredDataRange;
    
    private static List<MapleDeadlockFunction> mapleRunnableMethods;
    
    private static Map<Integer, List<Integer>> mapleCompoundDataTypes = new HashMap<>();
    private static Map<Integer, MapleDeadlockClass> mapleClassDataTypes = new HashMap<>();
    private static Map<Integer, MapleDeadlockAbstractType> mapleAbstractDataTypes = new HashMap<>();
    private static Map<Integer, String> mapleBasicDataTypes = new HashMap<>();
    private static Map<String, Integer> mapleDereferencedDataTypes = new HashMap<>();
    
    private static Map<Integer, String> mapleEveryDataTypes = new HashMap<>();
    private static Map<String, Integer> mapleEveryDataTypeIds = new HashMap<>();
    
    private static Map<Integer, Set<Integer>> mapleSuperClasses = new HashMap<>();
    private static Map<Integer, Integer> mapleDataWrapper = new HashMap<>();
    
    private static Map<MapleDeadlockFunction, Integer> mapleGraphFunctionIds = new HashMap<>();
    private static Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> mapleGraphFunctions = new HashMap<>();
    
    private static Integer runningFid = 0;
    private static Integer lockId;
    
    private static List<Integer> getArgumentTypes(MapleDeadlockGraphMethod node, JavaParser.ExpressionListContext expList, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
        List<Integer> ret = new LinkedList<>();
        if(expList != null) {
            for(JavaParser.ExpressionContext exp : expList.expression()) {
                for (Integer argType : parseMethodCalls(node, exp, sourceMethod, sourceClass)) {
                    ret.add((argType != -1 && !argType.equals(mapleElementalTypes[7])) ? argType : -2);  // make accept any non-determined argument-type
                }
            }
        }
        
        return ret;
    }
    
    private static Integer getWrappedValue(Integer dataType) {
        Integer ret = mapleDataWrapper.get(dataType);
        
        if(ret == null) {
            List<Integer> cdt = mapleCompoundDataTypes.get(dataType);
            
            ret = cdt.get(cdt.size() - 2);
            mapleDataWrapper.put(dataType, ret);
        }
        
        return ret;
    }
    
    private static Integer evaluateLockFunction(String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockGraphMethod node) {
        switch(methodName) {
            case "lock":
            case "tryLock":
                //System.out.println("adding lock node " + lockId);
                node.addGraphEntry(new MapleDeadlockGraphEntry(new MapleDeadlockGraphNodeLock(lockId, true)));
                break;
                
            case "unlock":
                //System.out.println("adding unlock node " + lockId);
                node.addGraphEntry(new MapleDeadlockGraphEntry(new MapleDeadlockGraphNodeLock(lockId, false)));
                break;
        }
        
        return mapleElementalTypes[4];
    }
    
    private static Integer evaluateScriptFunction(String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockGraphMethod node) {
        MapleDeadlockGraphEntry entry = new MapleDeadlockGraphEntry();    
        entry.addGraphEntryPoint(new MapleDeadlockGraphNodeScript());

        node.addGraphEntry(entry);
        return -2;
    }
    
    private static Integer evaluateAbstractFunction(MapleDeadlockGraphMethod node, String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockAbstractType absType) {
        switch(absType) {
            case MAP:
                if(methodName.contentEquals("entrySet")) {
                    return objectSetId;
                }
                
            case SET:
            case LIST:
            case STACK:
            case PRIORITYQUEUE:
                if(methodName.contentEquals("size")) return mapleElementalTypes[0];
                //if(methodName.contentEquals("iterator")) return mapleElementalTypes[0];
                
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
    
    private static MapleDeadlockClass getClassFromType(Integer type) {
        return mapleClassDataTypes.get(type);
    }
    
    private static void createGraphFunction(MapleDeadlockFunction mdf) {
        mapleGraphFunctionIds.put(mdf, runningFid);
        mapleGraphFunctions.put(mdf, new MapleDeadlockGraphMethod(runningFid, MapleDeadlockStorage.getCanonClassName(mdf.getSourceClass()) + " >> " + mdf.getName()));
        mdf.setId(runningFid);
        
        runningFid++;
    }
    
    private static Integer getLockId(String identifier, MapleDeadlockClass sourceClass) {
        String lockName = MapleDeadlockStorage.getCanonClassName(sourceClass) + "." + identifier;
        MapleDeadlockLock lock = mapleLocks.get(lockName);
        
        if(lock != null) return lock.getId();
        
        for(MapleDeadlockClass mdc : sourceClass.getSuperList()) {
            Integer ret = getLockId(identifier, mdc);
            if (ret > -1) return ret;
        }
        
        if(sourceClass.getParent() != null) {
            Integer ret = getLockId(identifier, sourceClass.getParent());
            if (ret > -1) return ret;
        }
        
        return -1;
    }
    
    private static boolean isImportEnum(String name, MapleDeadlockClass sourceClass) {
        String names[] = name.split("\\.");
        if (names.length == 0) names = new String[]{name};
        
        return sourceClass.getEnums().contains(names[names.length - 1]);
    }
    
    private static void setImportEnums(MapleDeadlockClass sourceClass) {
        Set<String> importedEnums = new HashSet<>();
        for (Pair<String, MapleDeadlockClass> e : sourceClass.getImports()) {
            if (e.getRight() == null) {     // possible candidate for enum item
                String names[] = e.getLeft().split("\\.");
                if (names.length == 0) names = new String[]{e.getLeft()};
                
                MapleDeadlockClass c;
                String path = names[0];
                int s = -1;
                
                int i = 0;
                while (true) {
                    c = mapleAllClasses.get(path);
                    
                    if (c != null) {
                        s = path.length();
                    } else if (s > -1) {
                        if (i == names.length - 1) {
                            if (!names[i].contentEquals("*")) {
                                importedEnums.add(names[names.length - 1]);
                            } else {
                                c = mapleAllClasses.get(path.substring(0, s));
                                if (c.isEnum()) {
                                    MapleDeadlockEnum e1 = (MapleDeadlockEnum) c;
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
    
    private static Integer getPrimaryTypeOnFieldVars(String name, MapleDeadlockClass sourceClass) {
        Integer t = sourceClass.getFieldVariable(name);
        if(t != null) return t;
        
        if (isImportEnum(name, sourceClass)) {
            return -2;
        }
        
        for(MapleDeadlockClass mdc : sourceClass.getSuperList()) {
            t = getPrimaryTypeOnFieldVars(name, mdc);

            if(t != null) {
                return t;
            }
        }
        
        if(sourceClass.getParent() != null) return getPrimaryTypeOnFieldVars(name, sourceClass.getParent());
        return null;
    }
    
    private static Integer getPrimaryTypeFromLocalVars(String name, MapleDeadlockFunction sourceMethod) {
        Long nameHash = MapleDeadlockStorage.hash64(name);
        
        do {
            Set<Integer> nameTypes = sourceMethod.getLocalVariables().get(nameHash);
            if(nameTypes != null) {
                return nameTypes.size() == 1 ? nameTypes.iterator().next() : -2;    // ignore name allocated multiple times
            }
            
            sourceMethod = sourceMethod.getParent();
        } while(sourceMethod != null);
        
        return null;
    }
    
    private static Integer getPrimaryType(String name, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
        //System.out.println("trying " + name + " on " + MapleDeadlockStorage.getCanonClassName(sourceClass));
        //System.out.println(localVars);
        
        Integer t = getPrimaryTypeFromLocalVars(name, sourceMethod);
        if(t == null) {
            t = getPrimaryTypeOnFieldVars(name, sourceClass);
        
            if(t == null) {
                // maybe basic types
                t = mapleBasicDataTypeIds.get(MapleLinkedTypes.getLinkedType(name));
                if(t != null && !t.equals(mapleElementalTypes[5])) {
                    return t;
                }
                
                // maybe class-based types
                MapleDeadlockClass mdc = MapleDeadlockStorage.locateClass(name, sourceClass);
                if(mdc != null) {
                    return mapleClassDataTypeIds.get(mdc);
                }
                
                if(sourceClass.isEnum()) {    // maybe it's an identifier defining an specific item from an enum, return self-type
                    MapleDeadlockEnum mde = (MapleDeadlockEnum) sourceClass;
                    
                    if(mde.containsEnumItem(name)) {
                        return mapleClassDataTypeIds.get(sourceClass);
                    }
                }
            }
        }
        
        if(t == null) {
            //System.out.println("FAILED TO FIND '" + name + "' ON " + MapleDeadlockStorage.getCanonClassName(sourceClass) + ", call was " + curCall);
            return -1;
        }
        
        if(t.equals(mapleElementalTypes[5])) {
            lockId = getLockId(name, sourceClass);
        }
        
        return t;
    }
    
    private static Integer getRelevantType(Integer retType, Set<Integer> templateTypes, MapleDeadlockClass c, Integer expType) {
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
                List<Integer> cTypes = mapleCompoundDataTypes.get(expType);
                retType = cTypes.get(pos);
            } catch(Exception e) {  // e.g. case where objects of unknown types are being compared with equals()
                return -2;
            }
        }
        
        return retType;
    }
    
    private static Integer getLiteralType(JavaParser.LiteralContext ctx) {
        if(ctx.integerLiteral() != null) return mapleElementalTypes[0];
        if(ctx.floatLiteral() != null) return mapleElementalTypes[1];
        if(ctx.CHAR_LITERAL() != null) return mapleElementalTypes[2];
        if(ctx.STRING_LITERAL() != null) return mapleElementalTypes[3];
        if(ctx.BOOL_LITERAL() != null) return mapleElementalTypes[4];
        if(ctx.NULL_LITERAL() != null) return mapleElementalTypes[7];
        
        return -1;
    }
    
    private static Pair<MapleDeadlockFunction, Set<Integer>> getMethodDefinitionFromClass(MapleDeadlockClass c, String method, List<Integer> argTypes) {
        MapleDeadlockFunction mdf = c.getMethod(false, method, argTypes);
        if(mdf != null) {
            Set<Integer> templateTypes = c.getMaskedTypeSet();
            return new Pair<>(mdf, templateTypes);
        }
        
        return null;
    }
    
    private static void getMethodImplementationsFromSubclasses(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions) {
        List<MapleDeadlockClass> subclasses = mapleInheritanceTree.get(c);
        if(subclasses != null) {
            for(MapleDeadlockClass mdc : subclasses) {
                getMethodImplementationsFromSubclasses(mdc, method, expType, argTypes, elementalTypes, implementedFunctions);
                
                Pair<MapleDeadlockFunction, Set<Integer>> classMethodImplementation = getMethodDefinitionFromClass(mdc, method, argTypes);
                if(classMethodImplementation != null && !classMethodImplementation.left.isAbstract()) {
                    implementedFunctions.add(classMethodImplementation);
                }
            }
        }
    }
    
    private static Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> getMethodImplementations(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
        Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions = new LinkedHashSet<>();
        Pair<MapleDeadlockFunction, Set<Integer>> retMethod = null;
        
        Pair<MapleDeadlockFunction, Set<Integer>> p = getMethodDefinitionFromClass(c, method, argTypes);
        if(p != null) {
            retMethod = p;
            
            if(!p.left.isAbstract()) {
                implementedFunctions.add(p);
            }
        } else {
            // will need to access superclasses to return a method accessible by the inputted class
            
            for(MapleDeadlockClass sup : c.getSuperList()) {
                MapleDeadlockFunction m = sup.getMethod(true, method, argTypes);
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
                MapleDeadlockClass parent = c.getParent();
                
                if(parent != null) {
                    MapleDeadlockFunction m = parent.getMethod(true, method, argTypes);
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
    
    private static Pair<MapleDeadlockFunction, Set<Integer>> getTemplateMethodDefinitionFromClass(MapleDeadlockClass c, String method, List<Integer> argTypes) {
        MapleDeadlockFunction mdf = c.getTemplateMethod(false, method, argTypes);
        if(mdf != null) {
            Set<Integer> templateTypes = c.getMaskedTypeSet();
            return new Pair<>(mdf, templateTypes);
        }
        
        return null;
    }
    
    private static void getTemplateMethodImplementationsFromSubclasses(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions) {
        List<MapleDeadlockClass> subclasses = mapleInheritanceTree.get(c);
        if(subclasses != null) {
            for(MapleDeadlockClass mdc : subclasses) {
                getTemplateMethodImplementationsFromSubclasses(mdc, method, expType, argTypes, elementalTypes, implementedFunctions);
            
                Pair<MapleDeadlockFunction, Set<Integer>> classMethodImplementation = getTemplateMethodDefinitionFromClass(mdc, method, argTypes);
                if(classMethodImplementation != null && !classMethodImplementation.left.isAbstract()) {
                    implementedFunctions.add(classMethodImplementation);
                }
            }
        }
    }
    
    private static Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> getTemplateMethodImplementations(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
        Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions = new LinkedHashSet<>();
        Pair<MapleDeadlockFunction, Set<Integer>> retMethod = null;
        
        Pair<MapleDeadlockFunction, Set<Integer>> p = getTemplateMethodDefinitionFromClass(c, method, argTypes);
        if(p != null) {
            retMethod = p;
            
            if(!p.left.isAbstract()) {
                implementedFunctions.add(p);
            }
        } else {
            // will need to access superclasses to return a method accessible by the inputted class
            
            for(MapleDeadlockClass sup : c.getSuperList()) {
                MapleDeadlockFunction m = sup.getTemplateMethod(true, method, argTypes);
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
                MapleDeadlockClass parent = c.getParent();
                
                if(parent != null) {
                    MapleDeadlockFunction m = parent.getTemplateMethod(true, method, argTypes);
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
    
    private static Set<Integer> getReturnType(MapleDeadlockGraphMethod node, String method, Integer expType, List<Integer> argTypes, JavaParser.MethodCallContext methodCall) {
        Set<Integer> ret = new HashSet<>();
        MapleDeadlockClass c = getClassFromType(expType);
        
        MapleDeadlockFunction retMethod = null;
        Set<Integer> retTemplateTypes = null;
        
        Set<List<Pair<MapleDeadlockFunction, Set<Integer>>>> allMethodImplementations = new LinkedHashSet<>();
        Set<Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>>> metImpl = new HashSet<>();
        
        //System.out.println("requiring return from " + expType + " method " + method);
        
        if(c == null) {
            List<Integer> cTypes = mapleCompoundDataTypes.get(expType);
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
                
                metImpl.add(getTemplateMethodImplementations(c, method, expType, argTypes, mapleElementalDataTypes));
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
                    ret.add(mapleElementalTypes[0]);
                    return ret;
                } else if(method.contentEquals("name")) {
                    ret.add(mapleElementalTypes[3]);
                    return ret;
                } else if(method.contentEquals("equals")) {
                    ret.add(mapleElementalTypes[4]);
                    return ret;
                }
            } else if(c.isInterface()) {
                for(MapleDeadlockClass sc : mapleInheritanceTree.get(c)) {
                    metImpl.add(getMethodImplementations(sc, method, expType, argTypes, mapleElementalDataTypes));
                }
            }
            
            metImpl.add(getMethodImplementations(c, method, expType, argTypes, mapleElementalDataTypes));
        }
        
        for(Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> i : metImpl) {
            allMethodImplementations.add(new LinkedList<>(i.right));
        }
        
        //System.out.println("conflicting funct " + mdf.getName() + " on " + MapleDeadlockStorage.getCanonClassName(mdf.getSourceClass()));
        //System.out.println("adding node " + fid);
        
        /*
        if(node.getId() == 986) {
            System.out.println("DEBUG DO_gachapon");
            
            for(int i = 0; i < allMethodImplementations.size(); i++) {
                MapleDeadlockFunction mdf = allMethodImplementations.get(i);
                
                System.out.println("  " + mdf.getName() + " src " + MapleDeadlockStorage.getCanonClassName(mdf.getSourceClass()));
            }
        }
        */
        
        if(allMethodImplementations.isEmpty()) {
            System.out.println("[Warning] EMPTY method node: " + methodCall.getText() + " @ " + method + " from " + MapleDeadlockStorage.getCanonClassName(c));
        }
        
        if (!allMethodImplementations.isEmpty()) {
            for(List<Pair<MapleDeadlockFunction, Set<Integer>>> mi : allMethodImplementations) {
                MapleDeadlockGraphEntry entry = new MapleDeadlockGraphEntry();

                for(Pair<MapleDeadlockFunction, Set<Integer>> mip : mi) {
                    MapleDeadlockFunction mdf = mip.left;
                    //Set<Integer> tt = mi.right;

                    Integer fid = mapleGraphFunctionIds.get(mdf);
                    entry.addGraphEntryPoint(new MapleDeadlockGraphNodeCall(fid));
                }
                
                node.addGraphEntry(entry);
            }
        }
        
        Set<Integer> retTypes = new HashSet<>();
        for (Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> i : metImpl) {
            for (Pair<MapleDeadlockFunction, Set<Integer>> p : i.getRight()) {
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
                return mapleElementalTypes[4];
            
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
                return mapleElementalTypes[0];
            
            case "clone":
                return thisType;
            
            case "invokeFunction":
                return -2;
                
            case "add":
            case "put":
            case "clear":
                if(mapleAbstractDataTypes.get(thisType) != MapleDeadlockAbstractType.NON_ABSTRACT) {
                    return -2;
                }
                
            default:
                if(methodName.endsWith("alue")) {   // intValue, floatValue, shortValue...
                    return -2;
                }
                
                return -1;
        }
    }
    
    private static Set<Integer> getMethodReturnType(MapleDeadlockGraphMethod node, Integer classType, JavaParser.MethodCallContext methodCall, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
        Set<Integer> retTypes = new HashSet<>();
        
        if(classType == -2) {
            retTypes.add(-2);
            return retTypes;
        }
        
        //System.out.println("CALL METHODRETURNTYPE for " + classType + " methodcall " + methodCall.getText());
        List<Integer> argTypes = getArgumentTypes(node, methodCall.expressionList(), sourceMethod, sourceClass);
        String methodName = methodCall.IDENTIFIER().getText();
        
        if(!mapleReflectedClasses.containsKey(classType)) {
            MapleDeadlockAbstractType absType = mapleAbstractDataTypes.get(classType);
            if(absType != null) {
                Integer ret = evaluateAbstractFunction(node, methodName, argTypes, classType, absType);
                retTypes.add(ret);
                
                //if(ret == -1 && absType != MapleDeadlockAbstractType.LOCK) System.out.println("SOMETHING OUT OF CALL FOR " + methodCall.IDENTIFIER().getText() + " ON " + absType /*+ dataNames.get(expType)*/);
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
            Pair<Integer, Map<String, Integer>> reflectedData = mapleReflectedClasses.get(classType);
            
            if(methodName.contentEquals("toString")) {
                retTypes.add(mapleElementalTypes[3]);
            } else {
                MapleDeadlockAbstractType absType = mapleAbstractDataTypes.get(classType);
                if(absType != null) {
                    if (absType == MapleDeadlockAbstractType.LOCK || absType == MapleDeadlockAbstractType.SCRIPT) {
                        Integer ret = evaluateAbstractFunction(node, methodName, argTypes, classType, absType);
                        retTypes.add(ret);

                        //if(ret == -1 && absType != MapleDeadlockAbstractType.LOCK) System.out.println("SOMETHING OUT OF CALL FOR " + methodCall.IDENTIFIER().getText() + " ON " + absType /*+ dataNames.get(expType)*/);
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
        if(ctx.INT() != null || ctx.SHORT() != null || ctx.LONG() != null || ctx.BYTE() != null) return mapleElementalTypes[0];
        if(ctx.FLOAT() != null || ctx.DOUBLE() != null) return mapleElementalTypes[1];
        if(ctx.CHAR() != null) return mapleElementalTypes[2];
        if(ctx.BOOLEAN() != null) return mapleElementalTypes[4];
        
        return -2;
    }
    
    private static Integer getDereferencedType(String derTypeName, MapleDeadlockClass derClass) {
        Integer derType = mapleDereferencedDataTypes.get(derTypeName);
        if(derType != null) return derType;
        
        if (derClass == null) derType = mapleBasicDataTypeIds.get(derTypeName);
        else derType = mapleClassDataTypeIds.get(derClass);
        
        mapleDereferencedDataTypes.put(derTypeName, derType);
        return derType;
    }
    
    private static void skimArrayInitializer(JavaParser.ArrayInitializerContext ainiCtx, MapleDeadlockGraphMethod node, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
        // just process expressions inside and add them to the function graph, disregard return values
        
        for(JavaParser.VariableInitializerContext var : ainiCtx.variableInitializer()) {
            if(var.expression() != null) {
                parseMethodCalls(node, var.expression(), sourceMethod, sourceClass);
            } else if(var.arrayInitializer() != null) {
                skimArrayInitializer(var.arrayInitializer(), node, sourceMethod, sourceClass);
            }
        }
    }
    
    private static Set<Integer> parseMethodCalls(MapleDeadlockGraphMethod node, JavaParser.ExpressionContext call, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
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
                        retTypes.add(mapleElementalTypes[0]);
                        continue;
                    }

                    retTypes.add(mapleElementalTypes[1]);
                    continue;
                } else if(base.contentEquals("System") || base.contentEquals("Arrays") || base.contentEquals("Collections") || base.contentEquals("MapleData")) {
                    retTypes.add(-1);
                    continue;
                } else {
                    ret = getPreparedReturnType(methodName, mapleClassDataTypeIds.get(sourceClass));
                    if(ret != -1) {
                        retTypes.add(ret);
                        continue;
                    }
                }

                System.out.println("[Warning] COULD NOT DETERMINE " + call.getText() + " on src " + MapleDeadlockStorage.getCanonClassName(sourceClass) + ", ret " + ret);
            }
        }
        
        metRetTypes.remove(-1);
        retTypes.addAll(metRetTypes);
        
        return retTypes;
    }
    
    private static boolean isIgnoredRange(Integer type) {
        return type >= mapleIgnoredDataRange.left && type < mapleIgnoredDataRange.right;
    }
    
    private static boolean isIgnoredType(Integer type) {
        if(isIgnoredRange(type)) {
            return true;
        }
        
        List<Integer> cType = mapleCompoundDataTypes.get(type);
        if(cType != null) {
            for(Integer i : cType) {
                if(isIgnoredType(i)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static Integer getThisType(MapleDeadlockClass sourceClass) {
        if (sourceClass == null) return null;
        
        Integer cid = mapleClassDataTypeIds.get(sourceClass);
        if (cid != null) return cid;

        for (MapleDeadlockClass mdc : sourceClass.getSuperList()) {
            cid = getThisType(mdc);
            if (cid != null) return cid;
        }
        
        cid = getThisType(sourceClass.getParent());
        return cid;
    }
    
    private static Set<Integer> parseMethodCalls(MapleDeadlockGraphMethod node, JavaParser.ExpressionContext call, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass, boolean filter) {
        JavaParser.ExpressionContext curCtx = call;
        
        Set<Integer> ret = new HashSet<>();
        
        if(curCtx.bop != null) {
            String bopText = curCtx.bop.getText();
            
            if(bopText.contentEquals(".")) {
                JavaParser.ExpressionContext expCtx = curCtx.expression(0);
                
                Set<Integer> metRetTypes = parseMethodCalls(node, expCtx, sourceMethod, sourceClass);
                if(metRetTypes.size() > 0) {
                    for (Integer expType : metRetTypes) {
                        if(expType == null) System.out.println("null on " + expCtx.getText() + " src is " + MapleDeadlockStorage.getCanonClassName(sourceClass));
                        if (curCtx.THIS() == null) {
                            if(expType != -1) {
                                if(expType != -2) {     // expType -2 means the former expression type has been excluded from the search
                                    if(curCtx.methodCall() != null) {
                                        Set<Integer> r = getMethodReturnType(node, expType, curCtx.methodCall(), sourceMethod, sourceClass);
                                        ret.addAll(r);

                                        if(ret.contains(-1)) {
                                            MapleDeadlockClass c = getClassFromType(expType);
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

                                        MapleDeadlockClass c = getClassFromType(expType);
                                        Set<Integer> templateTypes = null;

                                        if(c == null) {
                                            List<Integer> cTypes = mapleCompoundDataTypes.get(expType);
                                            if(cTypes != null) {
                                                c = getClassFromType(cTypes.get(cTypes.size() - 1));

                                                if(c == null) {
                                                    //System.out.println("GFAILED @ " + cTypes.get(cTypes.size() - 1));
                                                } else {
                                                    templateTypes = c.getMaskedTypeSet();
                                                }
                                            }

                                            if(c == null) {
                                                String typeName = mapleBasicDataTypes.get(expType);

                                                if(typeName != null && typeName.charAt(typeName.length() - 1) == ']') {
                                                    if(curCtx.IDENTIFIER().getText().contentEquals("length")) {
                                                        ret.add(mapleElementalTypes[0]);
                                                        continue;
                                                    }
                                                }

                                                //System.out.println("FAILED @ " + expType);
                                                System.out.println("[Warning] No datatype found for " + curCtx.IDENTIFIER() + " on expression " + curCtx.getText() + " srcclass " + MapleDeadlockStorage.getCanonClassName(sourceClass) + " detected exptype " + expType);
                                                ret.add(-2);
                                                continue;
                                            }
                                        } else {
                                            if(c.isEnum()) {    // it's an identifier defining an specific item from an enum, return self-type
                                                if(curCtx.IDENTIFIER().getText().contentEquals("length")) {
                                                    ret.add(mapleElementalTypes[0]);
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
                                            MapleDeadlockClass mdc = MapleDeadlockStorage.locateInternalClass(element, c);  // element could be a private class reference
                                            if(mdc != null) {
                                                ret.add(mapleClassDataTypeIds.get(mdc));
                                                continue;
                                            }

                                            //System.out.println("SOMETHING OUT OF CALL FOR FIELD " + curCtx.IDENTIFIER().getText() + " ON " + MapleDeadlockStorage.getCanonClassName(c));
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
                        Integer expType = (ret1.equals(mapleElementalTypes[3]) || ret2.equals(mapleElementalTypes[3])) ? mapleElementalTypes[3] : (ret1 != -1 ? ret1 : ret2);
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
                ret.add(mapleElementalTypes[4]);
                return ret;
            }
        } else if(curCtx.prefix != null || curCtx.postfix != null) {
            if(curCtx.prefix != null && curCtx.prefix.getText().contentEquals("!")) {
                ret.add(mapleElementalTypes[4]);
                return ret;
            }
            
            parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
            return ret;
        } else if(curCtx.getChild(curCtx.getChildCount() - 1).getText().contentEquals("]")) {
            JavaParser.ExpressionContext outer = curCtx.expression(0);
            JavaParser.ExpressionContext inner = curCtx.expression(1);
            
            for (Integer outerType : parseMethodCalls(node, outer, sourceMethod, sourceClass)) {
                MapleDeadlockClass outerClass = mapleClassDataTypes.get(outerType);
                String outerName;
                if (outerClass != null) {
                    outerName = MapleDeadlockStorage.getClassPath(outerClass);
                } else {
                    outerName = mapleBasicDataTypes.get(outerType);
                    
                    outerClass = MapleDeadlockStorage.locateClass(outerName, sourceClass);
                    if (outerClass != null) outerType = mapleClassDataTypeIds.get(outerClass);
                }
                
                if (outerName.endsWith("]")) outerName = outerName.substring(0, outerName.lastIndexOf("["));
                
                Integer derType;
                if (outerName.endsWith("]")) {
                    derType = getDereferencedType(outerName, outerClass);
                } else {
                    MapleDeadlockClass mdc = MapleDeadlockStorage.locateClass(outerName, sourceClass);
                    if (mdc != null) {
                        derType = mapleClassDataTypeIds.get(mdc);
                    } else if (mapleBasicDataTypeIds.containsKey(outerName)) {
                        derType = mapleBasicDataTypeIds.get(outerName);
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
                    ret.add(mapleClassDataTypeIds.get(sourceClass.getSuperList().get(0)));
                    return ret;
                } else {
                    ret.add(-2);
                    return ret;
                }
            }
        } else if(curCtx.getChildCount() == 4 && curCtx.getChild(curCtx.getChildCount() - 2).getText().contentEquals(")")) {   // '(' typeType ')' expression
            parseMethodCalls(node, curCtx.expression(0), sourceMethod, sourceClass);
            String typeText = curCtx.typeType().getText();
            
            MapleDeadlockClass c = MapleDeadlockStorage.locateClass(typeText, sourceClass);
            if(c != null) {
                ret.add(mapleClassDataTypeIds.get(c));
                return ret;
            }
            
            Integer i = mapleBasicDataTypeIds.get(typeText);
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
                MapleDeadlockClass c = MapleDeadlockStorage.locateClass(idName, sourceClass);
                
                if(c != null && c.getMaskedTypeSet() == null) {     // if the creator is instancing a compound data type, let it throw a -2
                    ret.add(mapleClassDataTypeIds.get(c));
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
            ret.addAll(getMethodReturnType(node, mapleClassDataTypeIds.get(sourceClass), curCtx.methodCall(), sourceMethod, sourceClass));
            return ret;
        } else if(curCtx.expression().size() == 2) {    // expression ('<' '<' | '>' '>' '>' | '>' '>') expression
            ret.add(mapleElementalTypes[0]);
            return ret;
        }
        
        ret.add(-1);
        return ret;
    }
    
    private static void parseMethodNode(MapleDeadlockFunction method, MapleDeadlockClass sourceClass) {
        MapleDeadlockGraphMethod node = mapleGraphFunctions.get(method);
        
        for(JavaParser.ExpressionContext call : method.getMethodCalls()) {
            parseMethodCalls(node, call, method, sourceClass);
        }
    }
    
    private static void reinstanceCachedMaps(MapleDeadlockStorage metadata) {
        for(Entry<String, Integer> e : metadata.getBasicDataTypes().entrySet()) {
            Integer i = e.getValue();
            String s = e.getKey();
            
            mapleBasicDataTypes.put(i, s);
            mapleEveryDataTypes.put(i, s);
            mapleEveryDataTypeIds.put(s, i);
            
            MapleDeadlockAbstractType aType = MapleDeadlockAbstractType.getValue(s);
            if(aType != MapleDeadlockAbstractType.NON_ABSTRACT) {
                mapleAbstractDataTypes.put(i, aType);
            }
        }
        
        mapleClassDataTypeIds = metadata.getClassDataTypes();
        for(Entry<MapleDeadlockClass, Integer> e : mapleClassDataTypeIds.entrySet()) {
            Integer i = e.getValue();
            MapleDeadlockClass d = e.getKey();
            
            mapleClassDataTypes.put(i, d);
            mapleEveryDataTypes.put(i, MapleDeadlockStorage.getCanonClassName(d));
            mapleEveryDataTypeIds.put(MapleDeadlockStorage.getCanonClassName(d), i);
            
            MapleDeadlockAbstractType aType = MapleDeadlockAbstractType.getValue(d.getName());
            if(aType != MapleDeadlockAbstractType.NON_ABSTRACT) {
                mapleAbstractDataTypes.put(i, aType);
            }
        }
        
        for(Entry<List<Integer>, Integer> e : metadata.getCompoundDataTypes().entrySet()) {
            Integer i = e.getValue();
            List<Integer> d = e.getKey();
            
            mapleCompoundDataTypes.put(i, d);
            
            MapleDeadlockAbstractType absType = mapleAbstractDataTypes.get(d.get(d.size() - 1));
            if(absType != MapleDeadlockAbstractType.NON_ABSTRACT) {
                mapleAbstractDataTypes.put(i, absType);
            }
        }
        
        mapleBasicDataTypeIds = new HashMap<>();
        for (Entry<Integer, String> e : mapleBasicDataTypes.entrySet()) {
            mapleBasicDataTypeIds.put(e.getValue(), e.getKey());
        }
    }
    
    private static void generateMethodNodes(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
        for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
            for(MapleDeadlockClass c : m.values()) {
                for(MapleDeadlockFunction f : c.getMethods()) {
                    createGraphFunction(f);
                }
            }
        }
    }
    
    private static void generateSuperReferences(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
        for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
            for(MapleDeadlockClass c : m.values()) {
                Integer classId = mapleClassDataTypeIds.get(c);
                
                for(MapleDeadlockClass s : c.getSuperList()) {
                    Set<Integer> inherits = mapleSuperClasses.get(classId);
                    
                    if(inherits == null) {
                        inherits = new LinkedHashSet<>();
                        mapleSuperClasses.put(classId, inherits);
                    }
                    
                    inherits.add(mapleClassDataTypeIds.get(s));
                }
            }
        }
    }
    
    private static void parseMethodNodes(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
        for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
            for(MapleDeadlockClass c : m.values()) {
                for(MapleDeadlockFunction f : c.getMethods()) {
                    parseMethodNode(f, c);
                }
            }
        }
    }
    
    private static void parseRunnableMethodNodes() {
        for(MapleDeadlockFunction f : mapleRunnableMethods) {
            createGraphFunction(f);
            parseMethodNode(f, f.getSourceClass());
        }
    }
    
    public static Set<Integer> generateEnumReferences() {
        Set<Integer> enumIds = new HashSet<>();
        
        for(Map<String, MapleDeadlockClass> m : maplePublicClasses.values()) {
            for(MapleDeadlockClass c : m.values()) {
                if(c.isEnum()) {
                    enumIds.add(mapleClassDataTypeIds.get(c));
                }
            }
        }
        
        return enumIds;
    }
    
    private static Integer defineObjectSet() {
        Integer objectId = mapleEveryDataTypeIds.get("Object");
        Integer setId = mapleEveryDataTypeIds.get("Set");
        
        for (Entry<Integer, List<Integer>> e : mapleCompoundDataTypes.entrySet()) {
            if (e.getValue().get(0).equals(objectId) && e.getValue().get(1).equals(setId)) {
                return e.getKey();
            }
        }
        
        return -1;
    }
    
    private static void includeAllClassesInternal(Map<String, Map<String, MapleDeadlockClass>> map, boolean isPrivate) {
        if(!isPrivate) {
            for (Entry<String, Map<String, MapleDeadlockClass>> e : map.entrySet()) {
                String path = e.getKey();

                for (Entry<String, MapleDeadlockClass> f : e.getValue().entrySet()) {
                    mapleAllClasses.put(path + f.getKey(), f.getValue());
                }    
            }
        } else {
            for (Entry<String, Map<String, MapleDeadlockClass>> e : map.entrySet()) {
                String path = e.getKey();
                int idx = path.lastIndexOf('.');
                path = path.substring(0, idx + 1);

                for (Entry<String, MapleDeadlockClass> f : e.getValue().entrySet()) {
                    mapleAllClasses.put(path + f.getKey(), f.getValue());
                }    
            }
        }
    }
    
    private static void includeAllClasses() {
        includeAllClassesInternal(maplePublicClasses, false);
        includeAllClassesInternal(maplePrivateClasses, true);
    }
    
    public static MapleDeadlockGraph generateSourceGraph(MapleDeadlockStorage metadata) {
        reinstanceCachedMaps(metadata);
        objectSetId = defineObjectSet();
        
        maplePublicClasses = metadata.getPublicClasses();
        maplePrivateClasses = metadata.getPrivateClasses();
        
        mapleAllClasses = new HashMap<>();
        includeAllClasses();
        
        for (MapleDeadlockClass c : mapleAllClasses.values()) {
            setImportEnums(c);
        }
        
        mapleLocks = metadata.getLocks();
        mapleElementalDataTypes = metadata.getElementalDataTypes();
        mapleElementalTypes = metadata.getElementalTypes();
        mapleReflectedClasses = metadata.getReflectedClasses();
        mapleInheritanceTree = metadata.getInheritanceTree();
        mapleIgnoredDataRange = metadata.getIgnoredDataRange();
        
        mapleRunnableMethods = metadata.getRunnableMethods();
        
        mapleEnumDataTypes = generateEnumReferences();
        
        generateSuperReferences(maplePublicClasses);
        generateSuperReferences(maplePrivateClasses);
        
        generateMethodNodes(maplePublicClasses);
        generateMethodNodes(maplePrivateClasses);
        
        MapleDeadlockFunction.installTypeReferences(mapleElementalDataTypes, mapleCompoundDataTypes, mapleSuperClasses, mapleEnumDataTypes, mapleIgnoredDataRange, mapleElementalTypes[0]);
        
        try {
            parseMethodNodes(maplePublicClasses);
            parseMethodNodes(maplePrivateClasses);
            
            parseRunnableMethodNodes();
        } catch (Exception e) {
            e.printStackTrace();
            
            //dumpMemory();
            
            
            try {
            Thread.sleep(10000000);
            } catch(Exception ex ) {}
        }
        
        return new MapleDeadlockGraph(mapleGraphFunctionIds, mapleGraphFunctions);
    }
    
    private static List<Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>>> generateDumpEntries() {
        List<Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>>> dumpData = new LinkedList<>();
        
        for(Entry<MapleDeadlockFunction, Integer> e : mapleGraphFunctionIds.entrySet()) {
            MapleDeadlockGraphMethod g = mapleGraphFunctions.get(e.getKey());
            
            dumpData.add(new Pair<>(e.getValue(), new Pair<>(e.getKey(), g)));
        }
        
        Collections.sort(dumpData, new Comparator<Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>>>() {
            @Override
            public int compare(Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>> p1, Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>> p2) {
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
    
    public static Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> getGraphEntries() {
        return mapleGraphFunctions;
    }
    
    public static Map<String, MapleDeadlockLock> getGraphLocks() {
        return mapleLocks;
    }
    
    public static void dumpMemory() {
        System.out.println("mapleClassDataTypeIds :::::");
        for(Entry<MapleDeadlockClass, Integer> e : mapleClassDataTypeIds.entrySet()) {
            System.out.println(MapleDeadlockStorage.getCanonClassName(e.getKey()) + " " + e.getValue());
        }
        
        System.out.println("\n\nmapleClassDataTypes :::::");
        for(Entry<Integer, MapleDeadlockClass> e : mapleClassDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + MapleDeadlockStorage.getCanonClassName(e.getValue()));
        }
        
        System.out.println("\n\nmapleBasicDataTypeIds :::::");
        for(Entry<String, Integer> e : mapleBasicDataTypeIds.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        
        System.out.println("\n\nmapleBasicDataTypes :::::");
        for(Entry<Integer, String> e : mapleBasicDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        
        System.out.println("\n\nmapleCompoundDataTypeIds :::::");
        for(Entry<Integer, List<Integer>> e : mapleCompoundDataTypes.entrySet()) {
            System.out.println(translateId(mapleEveryDataTypes, e.getKey()) + " " + translateId(mapleEveryDataTypes, e.getValue()));
        }
        
        System.out.println("\n\nmapleAbstractDataTypes :::::");
        for(Entry<Integer, MapleDeadlockAbstractType> e : mapleAbstractDataTypes.entrySet()) {
            System.out.println(translateId(mapleEveryDataTypes, e.getKey()) + " " + e.getValue());
        }
        
        System.out.println("\n\nmapleDereferencedDataTypes :::::");
        for(Entry<String, Integer> e : mapleDereferencedDataTypes.entrySet()) {
            System.out.println(e.getKey() + " " + translateId(mapleEveryDataTypes, e.getValue()));
        }
    }
    
    public static void dumpGraph() {
        List<Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>>> dumpData = generateDumpEntries();
        
        for(Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>> e : dumpData) {
            List<MapleDeadlockGraphEntry> list = e.right.right.getEntryList();
            
            if(list.size() > 0) {
                System.out.println(MapleDeadlockStorage.getCanonClassName(e.right.left.getSourceClass()) + " >> " + e.right.left.getName() + " id: " + e.left);
                for(MapleDeadlockGraphEntry n : list) {
                    System.out.print(n.getGraphEntryPoints() + " ");
                }
                System.out.println("\n");
            }
        }
    }
    
}
