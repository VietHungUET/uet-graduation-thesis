package org.tzi.kodkod.ocl;

import java.util.HashMap;
import java.util.Map;

import kodkod.ast.Expression;
import kodkod.ast.Relation;

import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.kodkod.model.type.TypeFactory;

/**
 * Abstract base class for all classes with transformation methods.
 * 
 * @author Hendrik Reitmann
 * 
 */
public abstract class OCLOperationGroup {

	protected TypeFactory typeFactory;
	protected Relation undefined;
	protected Relation undefined_Set;
	protected Expression booleanTrue;
	protected Expression booleanFalse;
	protected Map<String, String> symbolOperationMapping;

	public OCLOperationGroup(TypeFactory typeFactory) {
		this.typeFactory = typeFactory;

		undefined = typeFactory.undefinedType().relation();
		undefined_Set = typeFactory.undefinedSetType().relation();

		Map<String, Expression> typeLiterals = typeFactory.booleanType().typeLiterals();
		booleanTrue = typeLiterals.get(TypeConstants.BOOLEAN_TRUE);
		booleanFalse = typeLiterals.get(TypeConstants.BOOLEAN_FALSE);

		symbolOperationMapping = new HashMap<String, String>();
	}

	/**
	 * True if the class contains transformation methods for set operations.
	 * 
	 * @return
	 */
	public boolean isSetOperationGroup() {
		return false;
	}

	/**
	 * True if the class contains transformation methods for sequence operations.
	 * 
	 * @return
	 */
	public boolean isSequenceOperationGroup() {
		return false;
	}

	/**
	 * Gets the collection type of this operation group.
	 * 
	 * @return the collection type constant (OBJECT=0, SET=1, SEQUENCE=2)
	 */
	public int getCollectionType() {
		if (isSequenceOperationGroup()) {
			return CollectionType.SEQUENCE;
		} else if (isSetOperationGroup()) {
			return CollectionType.SET;
		} else {
			return CollectionType.OBJECT;
		}
	}

	/**
	 * Does the transformation of the operation results in a set.
	 * 
	 * @param opName
	 * @return
	 */
	public boolean returnsSet(String opName) {
		return false;
	}

	/**
	 * Does the transformation of the operation results in a sequence.
	 * 
	 * @param opName
	 * @return
	 */
	public boolean returnsSequence(String opName) {
		return false;
	}

	/**
	 * Returns the collection type of the result after applying the operation.
	 * Default implementation: SEQUENCE > SET > OBJECT priority.
	 * 
	 * @param opName operation name
	 * @return collection type (OBJECT=0, SET=1, SEQUENCE=2)
	 */
	public int getResultCollectionType(String opName) {
		if (returnsSequence(opName)) {
			return CollectionType.SEQUENCE;
		} else if (returnsSet(opName)) {
			return CollectionType.SET;
		} else {
			return CollectionType.OBJECT;
		}
	}

	/**
	 * Returns the mapping of symbol to the names of the transformation methods.
	 * 
	 * @return
	 */
	public Map<String, String> getSymbolOperationMapping() {
		return symbolOperationMapping;
	}
}
