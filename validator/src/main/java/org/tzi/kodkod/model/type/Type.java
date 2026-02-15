package org.tzi.kodkod.model.type;

import kodkod.ast.Expression;
import kodkod.ast.Relation;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

import org.tzi.kodkod.model.iface.IElement;
import org.tzi.kodkod.model.visitor.Visitor;

/**
 * Abstract base class of all types.
 * 
 * @author Hendrik Reitmann
 * 
 */
public abstract class Type implements IElement {

	protected Relation relation;

	/**
	 * Returns true if this is the boolean type.
	 * 
	 * @return
	 */
	public boolean isBoolean() {
		return false;
	}

	/**
	 * Returns true if this is the string type.
	 * 
	 * @return
	 */
	public boolean isString() {
		return false;
	}

	/**
	 * Returns true if this is the integer type.
	 * 
	 * @return
	 */
	public boolean isInteger() {
		return false;
	}

	/**
	 * Returns true if this is the undefined type.
	 * 
	 * @return
	 */
	public boolean isUndefined() {
		return false;
	}

	/**
	 * Returns true if this is the undefined set type.
	 * 
	 * @return
	 */
	public boolean isUndefinedSet() {
		return false;
	}

	/**
	 * Returns true if this is an enum type.
	 * 
	 * @return
	 */
	public boolean isEnum() {
		return false;
	}

	/**
	 * Returns true if this is a collection type
	 * 
	 * @return
	 */
	public boolean isCollection() {
		return false;
	}

	/**
	 * Returns true if this is a bag type.
	 * 
	 * @return
	 */
	public boolean isBag() {
		return false;
	}

	/**
	 * Returns true if this is an ordered set type.
	 * 
	 * @return
	 */
	public boolean isOrderedSet() {
		return false;
	}

	/**
	 * Returns true if this is a sequence type.
	 * 
	 * @return
	 */
	public boolean isSequence() {
		return false;
	}

	/**
	 * Returns true if this is a set type.
	 * 
	 * @return
	 */
	public boolean isSet() {
		return false;
	}

	/**
	 * Returns true if this is an integer collection.
	 * 
	 * @return
	 */
	public boolean isIntegerCollection() {
		return false;
	}

	/**
	 * Returns true if this is an object type.
	 * 
	 * @return
	 */
	public boolean isObjectType() {
		return false;
	}

	/**
	 * Returns true if this is the ocl any type.
	 * 
	 * @return
	 */
	public boolean isAny() {
		return false;
	}

	/**
	 * Returns true if this is the real type.
	 * 
	 * @return
	 */
	public boolean isReal() {
		return false;
	}

	/**
	 * Returns the expression for the type.
	 * 
	 * @return
	 */
	public Expression expression() {
		if (isObjectType()) {
			return ((ObjectType) this).clazz().inheritanceOrRegularRelation();
		} else {
			return relation();
		}
	}

	/**
	 * Returns the relation for this type.
	 * 
	 * @return
	 */
	public Relation relation() {
		if (relation == null) {
			relation = createRelation();
		}
		return relation;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visitType(this);
	}

	/**
	 * Creates the relation for the type.
	 * 
	 * @return
	 */
	protected abstract Relation createRelation();

	/**
	 * Returns the lower bound for the type relation.
	 * 
	 * @param tupleFactory
	 * @return
	 */
	public abstract TupleSet lowerBound(TupleFactory tupleFactory);

	/**
	 * Returns the upper bound for the type relation.
	 * 
	 * @param tupleFactory
	 * @return
	 */
	public abstract TupleSet upperBound(TupleFactory tupleFactory);

	/**
	 * Returns structural dependencies for this type.
	 * Used for decomposed solving dependency detection.
	 * 
	 * Default: empty set (for basic types like Integer, String, Boolean)
	 * Override in ObjectType to return the class dependency
	 * 
	 * @return Set of relations this type depends on
	 */
	public java.util.Set<Relation> dependencies() {
		return java.util.Collections.emptySet();
	}
}
