package org.tzi.kodkod.ocl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.tzi.use.kodkod.transform.TransformationException;

import kodkod.ast.Expression;
import kodkod.ast.Node;
import kodkod.ast.Variable;

/**
 * Invoker of the transformation method.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class OCLMethodInvoker {

	private int collectionType;
	private Node object;

	/**
	 * Search the operation method using the OCLOperationLoader and calls the
	 * transformation method.
	 * 
	 * @param opName          operation name
	 * @param arguments       method arguments
	 * @param collectionType  the collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 * @param object_type_nav true if navigation to object type
	 */
	public void invoke(String opName, List<Object> arguments, int collectionType, boolean object_type_nav) {
		OCLOperationLoader operationLoader = new OCLOperationLoader();
		Method method = operationLoader.getOperationMethod(opName, arguments, collectionType);

		if (method == null) {
			arguments.add(Boolean.valueOf(object_type_nav));
			method = operationLoader.getOperationMethod(opName, arguments, collectionType);

			if (method == null) {
				throw new TransformationException("OCL operation " + opName + " is not supported.");
			}
		}

		try {
			if (operationLoader.needVariableArray()) {
				replaceSublistWithVariableArray(arguments, operationLoader.getFirstArrayIndex());
			} else if (operationLoader.needExpressionArray()) {
				replaceSublistWithExpressionArray(arguments, operationLoader.getFirstArrayIndex());
			}

			object = (Node) method.invoke(operationLoader.getOperationClass(), arguments.toArray());
			this.collectionType = operationLoader.getResultCollectionType();

		} catch (Exception e) {
			throw new TransformationException("Error while invoking method for operation " + opName + ".", e);
		}
	}

	/**
	 * Replaces a part of the arguments with an array of variables.
	 * 
	 * @param arguments
	 * @param fromIndex
	 */
	private void replaceSublistWithVariableArray(List<Object> arguments, int fromIndex) {
		List<Variable> variables = new ArrayList<Variable>();
		for (Object object : arguments.subList(fromIndex, arguments.size())) {
			if (object instanceof Variable) {
				variables.add((Variable) object);
			}
		}
		arguments.removeAll(variables);
		arguments.add(variables.toArray(new Variable[variables.size()]));
	}

	/**
	 * Replaces a part of the arguments with an array of expression.
	 * 
	 * @param arguments
	 * @param fromIndex
	 */
	private void replaceSublistWithExpressionArray(List<Object> arguments, int fromIndex) {
		List<Expression> expressions = new ArrayList<Expression>();
		for (Object object : arguments.subList(fromIndex, arguments.size())) {
			if (object instanceof Expression) {
				expressions.add((Expression) object);
			}
		}
		arguments.removeAll(expressions);
		arguments.add(expressions.toArray(new Expression[expressions.size()]));
	}

	/**
	 * Returns the resulting object.
	 * 
	 * @return
	 */
	public Node getObject() {
		return object;
	}

	/**
	 * Returns the collection type of the result.
	 * 
	 * @return collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 */
	public int getCollectionType() {
		return collectionType;
	}
}
