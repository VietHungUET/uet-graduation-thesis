package org.tzi.kodkod.ocl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.Variable;

import org.apache.log4j.Logger;

/**
 * Loads the transformation method for an ocl operation using Reflection.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class OCLOperationLoader {

	protected static final Logger LOG = Logger.getLogger(OCLOperationLoader.class);

	private String operatorName;
	private boolean needVariableArray = false;
	private boolean needExpressionArray = false;
	private OCLOperationGroup oclOperationGroup;
	private List<Integer> variableIndexes;
	private List<Integer> expressionIndexes;
	private OCLGroupRegistry registry;

	public OCLOperationLoader() {
		registry = OCLGroupRegistry.INSTANCE;
	}

	// /**
	// * Returns the transformation method for the given ocl operation.
	// *
	// * @param opName
	// * @param arguments
	// * @param setOperation
	// * @return
	// */
	// public Method getOperationMethod(String opName, List<Object> arguments,
	// boolean setOperation) {
	// return getOperationMethod(opName, arguments, setOperation ?
	// CollectionType.SET : CollectionType.OBJECT);
	// }

	/**
	 * Returns the transformation method for the given ocl operation.
	 * 
	 * @param opName
	 * @param arguments
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @return
	 */
	public Method getOperationMethod(String opName, List<Object> arguments, int collectionType) {
		operatorName = opName;

		Class<?>[] parameterTypes = extractParameterTypes(arguments);

		if (registry.getSymbolOperationMapping().containsKey(opName)) {
			operatorName = registry.getSymbolOperationMapping().get(opName);
		}

		System.out.println("\n*** OCLOperationLoader.getOperationMethod ***");
		System.out.println("Operation name: " + operatorName);
		System.out.println("Collection type: " + CollectionType.toString(collectionType));
		System.out.println("Arguments count: " + arguments.size());
		System.out.print("Parameter types: ");

		LOG.debug("Search: " + operatorName + " - collection type: " + CollectionType.toString(collectionType)
				+ " - args: " + arguments.size());

		Method method = searchMethod(operatorName, parameterTypes, collectionType);

		if (method != null) {
			System.out.println("✅ Found method: " + method);
			System.out.println("   In group: " + oclOperationGroup.getClass().getSimpleName());
		} else {
			System.out.println("❌ Method not found, trying research...");
		}
		if (method == null) {
			method = research(opName, collectionType, parameterTypes);
			if (method != null) {
				System.out.println("✅ Found via research: " + method);
			} else {
				System.out.println("❌ Still not found after research!");
			}
		}
		System.out.println("*******************************************\n");

		return method;
	}

	/**
	 * Extracts the classes of the arguments.
	 * 
	 * @param arguments
	 * @return
	 */
	private Class<?>[] extractParameterTypes(List<Object> arguments) {
		variableIndexes = new ArrayList<Integer>();
		expressionIndexes = new ArrayList<Integer>();
		Class<?>[] parameterTypes = new Class[arguments.size()];

		Object currentArgument;
		for (int i = 0; i < parameterTypes.length; i++) {
			currentArgument = arguments.get(i);

			if (currentArgument instanceof Variable) {
				parameterTypes[i] = Variable.class;
				variableIndexes.add(i);
				expressionIndexes.add(i);
			} else if (currentArgument instanceof Expression) {
				parameterTypes[i] = Expression.class;
				expressionIndexes.add(i);
			} else if (currentArgument instanceof Formula) {
				parameterTypes[i] = Formula.class;
			} else {
				parameterTypes[i] = currentArgument.getClass();
			}
		}
		return parameterTypes;
	}

	/**
	 * Search the transformation method.
	 * 
	 * @param opName
	 * @param parameterTypes
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @return
	 */
	private Method searchMethod(String opName, Class<?>[] parameterTypes, int collectionType) {
		Method method = null;

		System.out.println("\n>>> searchMethod: " + opName);
		System.out.println("    Looking for collection type: " + CollectionType.toString(collectionType));

		for (OCLOperationGroup operation : registry.getOperationGroups()) {
			try {
				method = operation.getClass().getMethod(opName, parameterTypes);

				if (method != null) {
					oclOperationGroup = operation;
					int groupType = oclOperationGroup.getCollectionType();
					System.out.println("    Found in: " + operation.getClass().getSimpleName());
					System.out.println("    Group type: " + CollectionType.toString(groupType));
					System.out.println("    Match? " + (groupType == collectionType));

					if (oclOperationGroup.getCollectionType() == collectionType) {
						LOG.debug("Find: " + oclOperationGroup.getClass().getSimpleName() + " - " + method.getName()
								+ " (type: " + CollectionType.toString(collectionType) + ")");
						break;
					} else {
						method = null;
					}
				}
			} catch (NoSuchMethodException e) {
			}
		}

		return method;
	}

	/**
	 * Search the transformation method with a different approach.
	 * 
	 * @param opName
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @param parameterTypes
	 * @return
	 */
	private Method research(String opName, int collectionType, Class<?>[] parameterTypes) {
		Method method = null;
		if (variableIndexes.size() > 0) {
			method = researchWithExpression(operatorName, collectionType, parameterTypes);
		}
		if (method == null && expressionIndexes.size() > 0) {
			method = researchWithArray(opName, collectionType, parameterTypes, Expression[].class,
					expressionIndexes.get(0));
			if (method != null) {
				needExpressionArray = true;
			}
		}
		return method;
	}

	/**
	 * A kodkod variable is a subclass of a kodkod expression. Search with
	 * expressen parametey types instead of variables.
	 * 
	 * @param opName
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @param parameterTypes
	 * @return
	 */
	private Method researchWithExpression(String opName, int collectionType, Class<?>[] parameterTypes) {
		for (int i = 0; i < variableIndexes.size(); i++) {
			parameterTypes[variableIndexes.get(i)] = Expression.class;
		}

		Method method = searchMethod(opName, parameterTypes, collectionType);
		if (method == null) {
			method = researchWithLastVariable(opName, collectionType, parameterTypes, 1);
			if (method == null) {
				method = researchWithLastVariable(opName, collectionType, parameterTypes, 2);
			}
		}

		if (method == null) {
			method = researchWithArray(opName, collectionType, parameterTypes, Variable[].class,
					variableIndexes.get(0));
			if (method != null) {
				needVariableArray = true;
			}
		}

		return method;
	}

	/**
	 * Search the transformation method with expression parameter types for
	 * variables except the last 'lastVariables'.
	 * 
	 * @param opName
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @param parameterTypes
	 * @param lastVariables
	 * @return
	 */
	private Method researchWithLastVariable(String opName, int collectionType, Class<?>[] parameterTypes,
			int lastVariables) {
		if (variableIndexes.size() - lastVariables >= 0) {
			for (int i = variableIndexes.size() - 1; i >= variableIndexes.size() - lastVariables; i--) {
				parameterTypes[variableIndexes.get(i)] = Variable.class;
			}

			return searchMethod(opName, parameterTypes, collectionType);
		}
		return null;
	}

	/**
	 * Replaces all variable parameter types with the Expression.class
	 * 
	 * @param parameterTypes
	 */
	public void replaceAllVariables(Class<?>[] parameterTypes) {
		for (Integer index : variableIndexes) {
			parameterTypes[index] = Expression.class;
		}
	}

	/**
	 * Search the transformation method with an array of variables instead of
	 * single variables.
	 * 
	 * @param opName
	 * @param collectionType the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @param parameterTypes
	 * @param arrayClass
	 * @param arrayIndex
	 * @return
	 */
	private Method researchWithArray(String opName, int collectionType, Class<?>[] parameterTypes, Class<?> arrayClass,
			int arrayIndex) {
		int firstVariableIndex = arrayIndex;
		Class<?>[] newParameterTypes = new Class<?>[firstVariableIndex + 1];
		for (int i = 0; i < firstVariableIndex; i++) {
			newParameterTypes[i] = parameterTypes[i];
		}

		newParameterTypes[firstVariableIndex] = arrayClass;

		return searchMethod(opName, newParameterTypes, collectionType);
	}

	/**
	 * Returns the group where the transformation method was found, null
	 * otherwise.
	 * 
	 * @return
	 */
	public OCLOperationGroup getOperationClass() {
		return oclOperationGroup;
	}

	/**
	 * Returns true if the transformation method has to be called with an array
	 * of variables.
	 * 
	 * @return
	 */
	public boolean needVariableArray() {
		return needVariableArray;
	}

	/**
	 * Returns true if the transformation method has to be called with an array
	 * of expressions.
	 * 
	 * @return
	 */
	public boolean needExpressionArray() {
		return needExpressionArray;
	}

	public int getFirstArrayIndex() {
		if (needVariableArray) {
			return variableIndexes.get(0);
		} else if (needExpressionArray) {
			return expressionIndexes.get(0);
		}
		throw new NoSuchElementException("No array.");
	}

	/**
	 * Returns true if the result translated operation returns a set.
	 * 
	 * @return
	 */
	public boolean returnsSet() {
		LOG.debug(
				"Method for operator " + operatorName + " returns set: " + oclOperationGroup.returnsSet(operatorName));
		return oclOperationGroup.returnsSet(operatorName);
	}

	/**
	 * Returns the collection type of the result after applying the operation.
	 * 
	 * @return collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 */
	public int getResultCollectionType() {
		int resultType = oclOperationGroup.getResultCollectionType(operatorName);
		LOG.debug("Method for operator " + operatorName + " returns collection type: "
				+ CollectionType.toString(resultType));
		return resultType;
	}
}
