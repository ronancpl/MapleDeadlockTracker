/*
    This file is part of the MapleQuestAdvisor planning tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.containers;

import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javaparser.JavaParser;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockFunction {
    static Map<Integer, List<Integer>> compoundTypes;
    static Map<Integer, Integer> elementalTypes;
    static Map<Integer, Set<Integer>> superTypes;
    static Set<Integer> enumTypes;
    static Pair<Integer, Integer> ignoredRange;
    static Integer intType;
    
    String name;
    boolean isAbstract;
    boolean isEllipsis = false;
    
    MapleDeadlockClass source;
    MapleDeadlockFunction parent;
    
    Set<MapleDeadlockLock> locks;
    
    Map<Long, List<Integer>> volatileLocalVars = new HashMap<>();
    Map<Long, Integer> localVars = new HashMap();
    Map<Long, String> localVarNames = new HashMap();
    Map<Long, Integer> paramVars;
    
    List<JavaParser.ExpressionContext> methodCalls = new LinkedList<>();   // starts off as a string representing the call, after the source readings it will be parsed
    
    List<Integer> paramTypes;
    Integer returnType;
    
    public MapleDeadlockFunction(String functName, MapleDeadlockClass mdc, MapleDeadlockFunction par, boolean abstracted) {
        name = functName;
        source = mdc;
        parent = par;
        isAbstract = abstracted;
        
        locks = new HashSet();
    }
    
    public void setMethodMetadata(Integer rType, List<Integer> pTypes, Map<Long, Integer> pVars) {
        returnType = rType;
        paramTypes = pTypes;
        paramVars = pVars;
    }
    
    public MapleDeadlockClass getSourceClass() {
        return source;
    }
    
    public MapleDeadlockFunction getParent() {
        return parent;
    }
    
    public boolean isAbstract() {
        return isAbstract;
    }
    
    public void setEllipsis(boolean e) {
        this.isEllipsis = e;
    }
    
    public boolean isEllipsis() {
        return this.isEllipsis;
    }
    
    public void addLockEntry(MapleDeadlockLock lock) {
        locks.add(lock);
    }
    
    public Long addLocalVariable(Integer type, String name) {
        Long hash = MapleDeadlockStorage.hash64(name);
        
        
        List<Integer> list = volatileLocalVars.get(hash);
        if(list == null) {
            list = new LinkedList<>();
            
            localVarNames.put(hash, name);
            volatileLocalVars.put(hash, list);
        }
        
        list.add(type);
        return hash;
    }
    
    public Map<Long, List<Integer>> getVolatileLocalVariables() {
        return volatileLocalVars;
    }
    
    public void clearVolatileLocalVariables() {
        volatileLocalVars.clear();
    }
    
    public void updateLocalVariable(Long name, Integer type) {
        localVars.put(name, type);
    }
    
    public String getLocalVariableName(Long hash) {
        return localVarNames.get(hash);
    }
    
    public Map<Long, Integer> getLocalVariables() {
        return localVars;
    }
    
    public void updateParameterVariable(Long name, Integer type) {
        paramVars.put(name, type);
    }
    
    public Map<Long, Integer> getParameterVariables() {
        return paramVars;
    }
    
    public void addMethodCall(JavaParser.ExpressionContext methodCall) {
        methodCalls.add(methodCall);
    }
    
    public void setSynchronizedModifier(JavaParser.ExpressionContext lockCall, JavaParser.ExpressionContext unlockCall) {
        methodCalls.add(unlockCall);
        methodCalls.add(0, lockCall);
    }
    
    public List<JavaParser.ExpressionContext> getMethodCalls() {
        return methodCalls;
    }
    
    public List<Integer> getParameters() {
        return new LinkedList(paramTypes);
    }
    
    public void updateParameter(Integer index, Integer type) {
        paramTypes.set(index, type);
    }
    
    public Integer getReturn() {
        return returnType;
    }
    
    public void setReturn(Integer rType) {
        returnType = rType;
    }
    
    public String getName() {
        return name;
    }
    
    private static Integer getElementalTypeOf(Integer type) {
        Integer ret = elementalTypes.get(type);
        
        if(ret != null) {
            return ret;
        }
        
        return enumTypes.contains(type) ? intType : type;
    }
    
    private static boolean hasTypeInheritance(Integer targetParam, Integer testParam) {
        if(targetParam.equals(testParam)) {
            return true;
        }
        
        Set<Integer> inheritTypes = superTypes.get(testParam);
        if(inheritTypes != null) {
            for(Integer t : inheritTypes) {
                if(hasTypeInheritance(targetParam, t)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static boolean isIgnoredRange(Integer type) {
        return type >= ignoredRange.left && type < ignoredRange.right;
    }
    
    private static boolean isIgnoredType(Integer type) {
        if(isIgnoredRange(type)) {
            return true;
        }
        
        List<Integer> cType = compoundTypes.get(type);
        if(cType != null) {
            for(Integer i : cType) {
                if(isIgnoredType(i)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public byte hasExactHeading(String functName, List<Integer> params) {
        if(name.contentEquals(functName) && params.size() == paramTypes.size()) {
            byte strongRef = 1;
            for(int i = 0; i < params.size(); i++) {
                Integer parType = paramTypes.get(i);
                if(isIgnoredType(parType)) continue;
                
                Integer param = params.get(i);
                if(param == -2) continue;
                
                if(!hasTypeInheritance(parType, param)) {
                    strongRef = 0;
                    
                    parType = getElementalTypeOf(parType);
                    param = getElementalTypeOf(params.get(i));
                    
                    if(!parType.equals(param)) {
                        //System.out.println(params + " " + paramTypes);
                        return -1;
                    }
                } else if(!param.equals(parType)) {
                    strongRef = 0;
                }
            }
            
            return strongRef;   // was the heading EXACT-MATCHED or it has used inheritance?
        }
        
        return -1;
    }
    
    public boolean hasSimilarHeading(String functName, List<Integer> params) {
        if(name.contentEquals(functName) && (params.size() == paramTypes.size() || (this.isEllipsis() && params.size() >= paramTypes.size()))) {
            Set<Integer> mTypes = source.getMaskedTypeSet();
            
            for(int i = 0; i < params.size(); i++) {
                Integer parType = paramTypes.get(i);
                if(isIgnoredType(parType)) continue;
                
                Integer param = params.get(i);
                
                if(!mTypes.contains(parType)) {
                    if(!hasTypeInheritance(parType, param)) {
                        parType = getElementalTypeOf(parType);
                        param = getElementalTypeOf(params.get(i));
                        
                        if(!parType.equals(elementalTypes.get(param)) && param != -2) {
                            //System.out.println(params + " " + paramTypes);
                            return false;
                        }
                    }
                }
            }
            
            return true;
        }
        
        return false;
    }
    
    public static void installTypeReferences(Map<Integer, Integer> elementalTypes, Map<Integer, List<Integer>> compoundTypes, Map<Integer, Set<Integer>> superTypes, Set<Integer> enumTypes, Pair<Integer, Integer> ignoredDataRange, Integer intType) {
        MapleDeadlockFunction.elementalTypes = elementalTypes;
        MapleDeadlockFunction.superTypes = superTypes;
        MapleDeadlockFunction.enumTypes = enumTypes;
        MapleDeadlockFunction.ignoredRange = ignoredDataRange;
        MapleDeadlockFunction.intType = intType;
        MapleDeadlockFunction.compoundTypes = compoundTypes;
    }
    
    @Override
    public String toString() {
        String s = "\t\t" + name + " : ";
        
        /*
        for(Integer i: paramTypes) {
            s += (e + ", ");
        }
        
        s += "\n\t\t\t";
        */
        
        for(JavaParser.ExpressionContext e : methodCalls) {
            s += (e.getText() + ", ");
        }
        
        s += "\n";
        
        return s;
    }
}
