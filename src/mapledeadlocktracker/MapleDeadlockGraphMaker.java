/*
    This file is part of the DeadlockTracker detection tool
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
import mapledeadlocktracker.source.CSharpReader;
import mapledeadlocktracker.strings.MapleLinkedTypes;

import language.java.JavaParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 *
 * @author RonanLana
 */
public abstract class MapleDeadlockGraphMaker {
	protected Map<String, Map<String, MapleDeadlockClass>> maplePublicClasses;
	protected Map<String, Map<String, MapleDeadlockClass>> maplePrivateClasses;
	protected Map<String, MapleDeadlockClass> mapleAllClasses;
	protected Map<String, MapleDeadlockLock> mapleLocks;

	protected Integer objectSetId;
	protected Map<Integer, Integer> mapleElementalDataTypes;
	protected Integer[] mapleElementalTypes;
	protected Set<Integer> mapleEnumDataTypes;

	protected Map<MapleDeadlockClass, Integer> mapleClassDataTypeIds;
	protected Map<String, Integer> mapleBasicDataTypeIds;

	protected Map<Integer, Pair<Integer, Map<String, Integer>>> mapleReflectedClasses;
	protected Map<MapleDeadlockClass, List<MapleDeadlockClass>> mapleInheritanceTree;
	protected Pair<Integer, Integer> mapleIgnoredDataRange;

	protected Set<MapleDeadlockFunction> mapleRunnableMethods;

	protected Map<Integer, List<Integer>> mapleCompoundDataTypes = new HashMap<>();
	protected Map<Integer, MapleDeadlockClass> mapleClassDataTypes = new HashMap<>();
	protected Map<Integer, MapleDeadlockAbstractType> mapleAbstractDataTypes = new HashMap<>();
	protected Map<Integer, String> mapleBasicDataTypes = new HashMap<>();
	protected Map<String, Integer> mapleDereferencedDataTypes = new HashMap<>();

	protected Map<Integer, String> mapleEveryDataTypes = new HashMap<>();
	protected Map<String, Integer> mapleEveryDataTypeIds = new HashMap<>();

	private Map<Integer, Set<Integer>> mapleSuperClasses = new HashMap<>();
	private Map<Integer, Integer> mapleDataWrapper = new HashMap<>();

	private Map<MapleDeadlockFunction, Integer> mapleGraphFunctionIds = new HashMap<>();
	private Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> mapleGraphFunctions = new HashMap<>();
        
        protected MapleDeadlockClass refClass = null;

	private Integer runningFid = 0;
	private Integer lockId;

	public abstract void parseSourceFile(String fileName, ParseTreeListener listener);
	public abstract Integer getLiteralType(ParserRuleContext ctx);
	public abstract List<ParserRuleContext> getArgumentList(ParserRuleContext ctx);
	public abstract Set<Integer> getMethodReturnType(MapleDeadlockGraphMethod node, Integer classType, ParserRuleContext methodCall, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass);
	public abstract Set<Integer> parseMethodCalls(MapleDeadlockGraphMethod node, ParserRuleContext callCtx, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass, boolean filter);	
	public abstract String parseMethodName(ParserRuleContext callCtx);
        public abstract ParserRuleContext generateExpression(String expressionText);
        public abstract boolean isUnlockMethodCall(String expressionText);
        
        protected List<Integer> getArgumentTypes(MapleDeadlockGraphMethod node, ParserRuleContext expList, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
		List<Integer> ret = new LinkedList<>();
		for(ParserRuleContext exp : getArgumentList(expList)) {
			for (Integer argType : parseMethodCalls(node, exp, sourceMethod, sourceClass)) {
				ret.add((argType != -1 && !argType.equals(mapleElementalTypes[7])) ? argType : -2);  // make accept any non-determined argument-type
			}
		}

		return ret;
	}

	private Integer getWrappedValue(Integer dataType) {
		Integer ret = mapleDataWrapper.get(dataType);

		if(ret == null) {
			List<Integer> cdt = mapleCompoundDataTypes.get(dataType);

			ret = cdt.get(cdt.size() - 2);
			mapleDataWrapper.put(dataType, ret);
		}

		return ret;
	}

	private Integer evaluateLockFunction(String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockGraphMethod node) {
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

	private Integer evaluateScriptFunction(String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockGraphMethod node) {
		MapleDeadlockGraphEntry entry = new MapleDeadlockGraphEntry();    
		entry.addGraphEntryPoint(new MapleDeadlockGraphNodeScript());

		node.addGraphEntry(entry);
		return -2;
	}

	protected Integer evaluateAbstractFunction(MapleDeadlockGraphMethod node, String methodName, List<Integer> argTypes, Integer dataType, MapleDeadlockAbstractType absType) {
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

	protected MapleDeadlockClass getClassFromType(Integer type) {
		return mapleClassDataTypes.get(type);
	}

	private void createGraphFunction(MapleDeadlockFunction mdf) {
		mapleGraphFunctionIds.put(mdf, runningFid);
		mapleGraphFunctions.put(mdf, new MapleDeadlockGraphMethod(runningFid, MapleDeadlockStorage.getCanonClassName(mdf.getSourceClass()) + " >> " + mdf.getName()));
		mdf.setId(runningFid);

		runningFid++;
	}

	private Integer getLockId(String identifier, MapleDeadlockClass sourceClass) {
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

	private boolean isImportEnum(String name, MapleDeadlockClass sourceClass) {
		String names[] = name.split("\\.");
		if (names.length == 0) names = new String[]{name};

		return sourceClass.getEnums().contains(names[names.length - 1]);
	}

	private void setImportEnums(MapleDeadlockClass sourceClass) {
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

	protected Integer getPrimaryTypeOnFieldVars(String name, MapleDeadlockClass sourceClass) {
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

	private Integer getPrimaryTypeFromLocalVars(String name, MapleDeadlockFunction sourceMethod) {
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

	protected Integer getPrimaryType(String name, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
		//System.out.println("trying " + name + " on " + DeadlockStorage.getCanonClassName(sourceClass));
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
			//System.out.println("FAILED TO FIND '" + name + "' ON " + DeadlockStorage.getCanonClassName(sourceClass) + ", call was " + curCall);
			return -1;
		}
                
		if(t.equals(mapleElementalTypes[5]) || t == 0) {
			lockId = getLockId(name, sourceClass);
		}

		return t;
	}

	protected Integer getRelevantType(Integer retType, Set<Integer> templateTypes, MapleDeadlockClass c, Integer expType) {
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
        
        protected Integer getTypeId(String name, MapleDeadlockClass sourceClass) {
                MapleDeadlockClass mdc = MapleDeadlockStorage.locateClass(name, sourceClass);
                if (mdc != null) {
                        return mapleClassDataTypeIds.get(mdc);
                } else if (mapleBasicDataTypeIds.containsKey(name)) {
                        return mapleBasicDataTypeIds.get(name);
                } else {
                        return -2;
                }
        }
        
        protected Integer getTypeFromFieldVariable(int expType, MapleDeadlockClass c, String idName, MapleDeadlockFunction sourceMethod) {
                Set<Integer> templateTypes = null;

                if(c == null) {
                        List<Integer> cTypes = mapleCompoundDataTypes.get(expType);
                        if(cTypes != null) {
                                c = getClassFromType(cTypes.get(cTypes.size() - 1));

                                if(c == null) {
                                        //System.out.println("Compound FAILED @ " + cTypes.get(cTypes.size() - 1));
                                } else {
                                        templateTypes = c.getMaskedTypeSet();
                                }
                        }

                        if(c == null) {
                                String typeName = mapleBasicDataTypes.get(expType);

                                if(typeName != null && typeName.charAt(typeName.length() - 1) == ']') {
                                        if(idName.contentEquals("length")) {
                                                return mapleElementalTypes[0];
                                        }
                                }

                                //System.out.println("FAILED @ " + expType);
                                return -2;
                        }
                } else {
                        if(c.isEnum()) {    // it's an identifier defining an specific item from an enum, return self-type
                                if(idName.contentEquals("length")) {
                                        return mapleElementalTypes[0];
                                }

                                return expType;
                        }

                        templateTypes = c.getMaskedTypeSet();
                }

                Integer type = getPrimaryType(idName, sourceMethod, c);
                if(type == null) {
                        MapleDeadlockClass mdc = MapleDeadlockStorage.locateInternalClass(idName, c);  // element could be a private class reference
                        if(mdc != null) {
                                return mapleClassDataTypeIds.get(mdc);
                        }

                        //System.out.println("SOMETHING OUT OF CALL FOR FIELD " + curCtx.IDENTIFIER().getText() + " ON " + DeadlockStorage.getCanonClassName(c));
                        return -1;
                }

                return getRelevantType(type, templateTypes, c, expType);
        }
        
        protected Integer getTypeFromIdentifier(int expType, String idName, MapleDeadlockFunction sourceMethod) {
                if(isIgnoredType(expType)) {
                        return -2;
                }

                MapleDeadlockClass c = getClassFromType(expType);
                Integer ret = getTypeFromFieldVariable(expType, c, idName, sourceMethod);
                
                return ret;
        }

	private Pair<MapleDeadlockFunction, Set<Integer>> getMethodDefinitionFromClass(MapleDeadlockClass c, String method, List<Integer> argTypes) {
		MapleDeadlockFunction mdf = c.getMethod(false, method, argTypes);
		if(mdf != null) {
			Set<Integer> templateTypes = c.getMaskedTypeSet();
			return new Pair<>(mdf, templateTypes);
		}

		return null;
	}

	private void getMethodImplementationsFromSubclasses(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions) {
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

	private Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> getMethodImplementations(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
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

	private Pair<MapleDeadlockFunction, Set<Integer>> getTemplateMethodDefinitionFromClass(MapleDeadlockClass c, String method, List<Integer> argTypes) {
		MapleDeadlockFunction mdf = c.getTemplateMethod(false, method, argTypes);
		if(mdf != null) {
			Set<Integer> templateTypes = c.getMaskedTypeSet();
			return new Pair<>(mdf, templateTypes);
		}

		return null;
	}

	private void getTemplateMethodImplementationsFromSubclasses(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes, Set<Pair<MapleDeadlockFunction, Set<Integer>>> implementedFunctions) {
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

	private Pair<Pair<MapleDeadlockFunction, Set<Integer>>, Set<Pair<MapleDeadlockFunction, Set<Integer>>>> getTemplateMethodImplementations(MapleDeadlockClass c, String method, Integer expType, List<Integer> argTypes, Map<Integer, Integer> elementalTypes) {
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

	protected Set<Integer> getReturnType(MapleDeadlockGraphMethod node, String method, Integer expType, List<Integer> argTypes, ParserRuleContext methodCall) {
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
                                String methodName = method.toLowerCase();
				if(methodName.contentEquals("values")) {    // this will return a Collection of enums, since Collection is being ignored, so this is
					ret.add(-2);
					return ret;
				} else if(methodName.contentEquals("ordinal")) {
					ret.add(mapleElementalTypes[0]);
					return ret;
				} else if(methodName.contentEquals("name")) {
					ret.add(mapleElementalTypes[3]);
					return ret;
				} else if(methodName.contentEquals("equals")) {
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
                
                if (!retTypes.isEmpty()) {
                        ret.addAll(retTypes);
                } else {
                        ret.add(-3);
                }

		return ret;
	}

	protected Integer getPreparedReturnType(String methodName, Integer thisType) {
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

		case "newInstance":
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

	protected Integer getDereferencedType(String derTypeName, MapleDeadlockClass derClass) {
		Integer derType = mapleDereferencedDataTypes.get(derTypeName);
		if(derType != null) return derType;

		if (derClass == null) derType = mapleBasicDataTypeIds.get(derTypeName);
		else derType = mapleClassDataTypeIds.get(derClass);

		mapleDereferencedDataTypes.put(derTypeName, derType);
		return derType;
	}

	protected void skimArrayInitializer(JavaParser.ArrayInitializerContext ainiCtx, MapleDeadlockGraphMethod node, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
		// just process expressions inside and add them to the function graph, disregard return values

		for(JavaParser.VariableInitializerContext var : ainiCtx.variableInitializer()) {
			if(var.expression() != null) {
				parseMethodCalls(node, var.expression(), sourceMethod, sourceClass);
			} else if(var.arrayInitializer() != null) {
				skimArrayInitializer(var.arrayInitializer(), node, sourceMethod, sourceClass);
			}
		}
	}
        
        private String getLockFieldName(String resourceName) {
                String[] sp = resourceName.split("_");
                if (sp.length > 1) {
                        String lockFieldName = "";
                        for (int i = 0; i < sp.length - 2; i++) {
                                lockFieldName += sp[i] + "_";
                        }
                        lockFieldName = lockFieldName.substring(0, lockFieldName.length() - 1);

                        return lockFieldName;
                } else {
                        return resourceName;
                }
        }

        private Pair<Integer, String> fetchLockField(String expressionText, boolean isLock, MapleDeadlockGraphMethod node, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
                ParserRuleContext ctx = generateExpression(expressionText);
                parseMethodCalls(node, ctx, sourceMethod, sourceClass, true);
                int typeId = mapleClassDataTypeIds.get(refClass);

                int idx = Integer.MAX_VALUE;

                int idx1 = ctx.getText().lastIndexOf('.');  // upper field : '.'
                if (idx1 > -1) {
                        idx = Math.min(idx, idx1);
                }

                int idx2 = ctx.getText().lastIndexOf('>');  // upper field : '->'
                if (idx2 > -1) {
                        idx = Math.min(idx, idx2 - 1);
                }

                if (idx < Integer.MAX_VALUE) {
                        expressionText = ctx.getText().substring(0, idx);
                }

                
                return new Pair<>(isLock ? typeId : -typeId, expressionText);
        }
        
	protected Set<Integer> parseMethodCalls(MapleDeadlockGraphMethod node, ParserRuleContext call, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
                String callText = call.getText();
            
                Set<Integer> metRetTypes = parseMethodCalls(node, call, sourceMethod, sourceClass, false);

		Set<Integer> retTypes = new HashSet<>();
		for (Integer ret : metRetTypes) {
			if (metRetTypes.size() == 1) {
                                if(ret == -1) {
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

                                        String methodName = parseMethodName(call);
                                        methodName = methodName.toLowerCase();

                                        if(base.contentEquals("Math")) {
                                                if(methodName.contentEquals("floor") || methodName.contentEquals("ceil")) {
                                                        retTypes.add(mapleElementalTypes[0]);
                                                        continue;
                                                }

                                                retTypes.add(mapleElementalTypes[1]);
                                                continue;
                                        } else if(base.contentEquals("System") || base.contentEquals("Arrays") || base.contentEquals("Collections") || base.contentEquals("Data")) {
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
                                } else if (ret == -3) {
                                        String prefixLockName = MapleDeadlockGraphMaker.getSyncLockName();
                                        
                                        if (callText.startsWith(prefixLockName)) {
                                                String fieldExpr = getLockFieldName(callText.substring(prefixLockName.length()));
                                                Pair<Integer, String> lockField = fetchLockField(fieldExpr, !isUnlockMethodCall(callText), node, sourceMethod, sourceClass);
                                                if (lockField != null) {
                                                        MapleDeadlockClass c = getClassFromType(Math.abs(lockField.getLeft()));
                                                        if(c != null) {
                                                                String synchLockName = MapleDeadlockGraphMaker.getSyncLockName(lockField.getRight(), 0);
                                                                Integer field = c.getFieldVariable(synchLockName);
                                                                if (field == null) {
                                                                        c.addFieldVariable(0, synchLockName);
                                                                        CSharpReader.processLock(c, "SynchLock", synchLockName, synchLockName);   // create a lock representation of the synchronized modifier
                                                                }
                                                                
                                                                getPrimaryType(synchLockName, sourceMethod, c);     // to retrieve lockId

                                                                if (lockField.getLeft() > 0) {
                                                                        evaluateLockFunction("lock", Collections.emptyList(), lockField.getLeft(), node);
                                                                } else {
                                                                        evaluateLockFunction("unlock", Collections.emptyList(), lockField.getLeft(), node);
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
		}

		metRetTypes.remove(-1);
		retTypes.addAll(metRetTypes);

		return retTypes;
	}

	private boolean isIgnoredRange(Integer type) {
		return type >= mapleIgnoredDataRange.left && type < mapleIgnoredDataRange.right;
	}

	protected boolean isIgnoredType(Integer type) {
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

	protected Integer getThisType(MapleDeadlockClass sourceClass) {
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
        
        private void parseMethodNode(MapleDeadlockFunction method, MapleDeadlockClass sourceClass) {
		MapleDeadlockGraphMethod node = mapleGraphFunctions.get(method);
                
		for(ParserRuleContext call : method.getMethodCalls()) {
                        parseMethodCalls(node, call, method, sourceClass);
		}
	}

	private void reinstanceCachedMaps(MapleDeadlockStorage metadata) {
                mapleBasicDataTypes.put(0, "SynchLock");
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

	private void generateMethodNodes(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
		for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
			for(MapleDeadlockClass c : m.values()) {
				for(MapleDeadlockFunction f : c.getMethods()) {
					createGraphFunction(f);
				}
			}
		}
	}

	private void generateSuperReferences(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
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

	private void parseMethodNodes(Map<String, Map<String, MapleDeadlockClass>> packageClasses) {
		for(Map<String, MapleDeadlockClass> m : packageClasses.values()) {
			for(MapleDeadlockClass c : m.values()) {
				for(MapleDeadlockFunction f : c.getMethods()) {
					parseMethodNode(f, c);
				}
			}
		}
	}

	private void parseRunnableMethodNodes() {
		for(MapleDeadlockFunction f : mapleRunnableMethods) {
			createGraphFunction(f);
			parseMethodNode(f, f.getSourceClass());
		}
	}
        
        public static String getSyncLockName() {
		return "synchLock_";
	}
        
        public static String getSyncLockName(String itemName, int methodId) {
                return "synchLock_" + itemName + "_" + methodId;
	}

	public Set<Integer> generateEnumReferences() {
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

	private Integer defineObjectSet() {
		Integer objectId = mapleEveryDataTypeIds.get("Object");
		Integer setId = mapleEveryDataTypeIds.get("Set");

		for (Entry<Integer, List<Integer>> e : mapleCompoundDataTypes.entrySet()) {
			if (e.getValue().get(0).equals(objectId) && e.getValue().get(1).equals(setId)) {
				return e.getKey();
			}
		}

		return -1;
	}

	private void includemapleAllClassesInternal(Map<String, Map<String, MapleDeadlockClass>> map, boolean isPrivate) {
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

	private void includemapleAllClasses() {
		includemapleAllClassesInternal(maplePublicClasses, false);
		includemapleAllClassesInternal(maplePrivateClasses, true);
	}

	public MapleDeadlockGraph generateSourceGraph(MapleDeadlockStorage metadata) {
		reinstanceCachedMaps(metadata);
		objectSetId = defineObjectSet();

		maplePublicClasses = metadata.getPublicClasses();
		maplePrivateClasses = metadata.getPrivateClasses();

		mapleAllClasses = new HashMap<>();
		includemapleAllClasses();

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
			//e.printStackTrace();

			//dumpMemory();
                        throw e;
		}

		return new MapleDeadlockGraph(mapleGraphFunctionIds, mapleGraphFunctions);
	}

	private List<Pair<Integer, Pair<MapleDeadlockFunction, MapleDeadlockGraphMethod>>> generateDumpEntries() {
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

	public String translateId(Map<Integer, String> map, Integer i) {
		if(map.get(i) == null) return "(" + i + ")";
		return map.get(i);
	}

	public String translateId(Map<Integer, String> map, List<Integer> list) {
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

	public Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> getGraphEntries() {
		return mapleGraphFunctions;
	}

	public Map<String, MapleDeadlockLock> getGraphLocks() {
		return mapleLocks;
	}

	public void dumpMemory() {
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

		System.out.println("\n\nCompoundDataTypeIds :::::");
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

	public void dumpGraph() {
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
