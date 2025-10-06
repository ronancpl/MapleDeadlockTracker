/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
 */
package mapledeadlocktracker.graph.maker;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import mapledeadlocktracker.MapleDeadlockGraphMaker;
import mapledeadlocktracker.containers.MapleDeadlockClass;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.containers.Pair;
import mapledeadlocktracker.graph.MapleDeadlockAbstractType;
import mapledeadlocktracker.graph.MapleDeadlockGraphMethod;
import language.java.JavaLexer;
import language.java.JavaParser;

/**
 *
 * @author RonanLana
 */
public class JavaGraph extends MapleDeadlockGraphMaker {

	@Override
	public void parseSourceFile(String fileName, ParseTreeListener listener) {
		try {
			JavaLexer lexer = new JavaLexer(CharStreams.fromFileName(fileName));
			CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
			JavaParser parser = new JavaParser(commonTokenStream);
			ParseTree tree = parser.compilationUnit();

			ParseTreeWalker walker = new ParseTreeWalker();
			walker.walk(listener, tree);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Integer getLiteralType(ParserRuleContext ctx) {
		JavaParser.LiteralContext elemCtx = (JavaParser.LiteralContext) ctx;

		if(elemCtx.integerLiteral() != null) return mapleElementalTypes[0];
		if(elemCtx.floatLiteral() != null) return mapleElementalTypes[1];
		if(elemCtx.CHAR_LITERAL() != null) return mapleElementalTypes[2];
		if(elemCtx.STRING_LITERAL() != null) return mapleElementalTypes[3];
		if(elemCtx.BOOL_LITERAL() != null) return mapleElementalTypes[4];
		if(elemCtx.NULL_LITERAL() != null) return mapleElementalTypes[7];

		return -1;
	}

	@Override
	public List<ParserRuleContext> getArgumentList(ParserRuleContext ctx) {
		JavaParser.ExpressionListContext expList = (JavaParser.ExpressionListContext) ctx;

		List<ParserRuleContext> ret = new LinkedList<>();
		if(expList != null) {
			for(ParserRuleContext exp : expList.expression()) {
				ret.add(exp);
			}
		}

		return ret;
	}

	private Integer getPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
		if(ctx.INT() != null || ctx.SHORT() != null || ctx.LONG() != null || ctx.BYTE() != null) return mapleElementalTypes[0];
		if(ctx.FLOAT() != null || ctx.DOUBLE() != null) return mapleElementalTypes[1];
		if(ctx.CHAR() != null) return mapleElementalTypes[2];
		if(ctx.BOOLEAN() != null) return mapleElementalTypes[4];

		return -2;
	}

	@Override
	public Set<Integer> getMethodReturnType(MapleDeadlockGraphMethod node, Integer classType, ParserRuleContext methodCallCtx, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass) {
		Set<Integer> retTypes = new HashSet<>();

		if(classType == -2) {
			retTypes.add(-2);
			return retTypes;
		}

		JavaParser.MethodCallContext methodCall = (JavaParser.MethodCallContext) methodCallCtx;

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
        
        @Override
	public Set<Integer> parseMethodCalls(MapleDeadlockGraphMethod node, ParserRuleContext callCtx, MapleDeadlockFunction sourceMethod, MapleDeadlockClass sourceClass, boolean filter) {
		if (filter) {
                        refClass = sourceClass;
                }
                
                JavaParser.ExpressionContext call = (JavaParser.ExpressionContext) callCtx;
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
                                                                                Integer idType = getTypeFromIdentifier(expType, curCtx.IDENTIFIER().getText(), sourceMethod);
                                                                                if (idType == -2) {
                                                                                        //String typeName = mapleEveryDataTypes.get(expType);

                                                                                        System.out.println("[Warning] No datatype found for " + curCtx.IDENTIFIER() + " on expression " + curCtx.getText() + " srcclass " + MapleDeadlockStorage.getCanonClassName(sourceClass) + " detected exptype " + expType);
                                                                                }
                                                                                ret.add(idType);
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
					derType = getTypeId(outerName, outerClass);
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
				ret.add(getTypeId(idName, sourceClass));
                                return ret;
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

	@Override
	public String parseMethodName(ParserRuleContext callCtx) {
		JavaParser.ExpressionContext call = (JavaParser.ExpressionContext) callCtx;

		String methodName = "";
		if(call.bop != null && call.methodCall() != null) {
			methodName = call.methodCall().IDENTIFIER().getText();
		}

		return methodName;
	}
        
        @Override
        public ParserRuleContext generateExpression(String expressionText) {
		JavaLexer lexer = new JavaLexer(CharStreams.fromString(expressionText));
		CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(commonTokenStream);

		return parser.expression();
	}
        
        @Override
        public boolean isUnlockMethodCall(String expressionText) {
                return expressionText.endsWith("unlock();");
        }

}
