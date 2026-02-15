package org.tzi.kodkod.model.iface;

import java.util.Collections;
import java.util.Set;

import kodkod.ast.Formula;
import kodkod.ast.Relation;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

/**
 * Instances of the type IModelElement represent a model element (Class,
 * Association, Attribute) in a model.
 * 
 * @author Hendrik Reitmann
 * 
 */
public interface IModelElement extends IConfigurableElement {

	/**
	 * Returns the name of this element.
	 * 
	 * @return
	 */
	public String name();

	/**
	 * Returns the relation for this element.
	 * 
	 * @return
	 */
	public Relation relation();

	/**
	 * Returns the lower bound for the relation.
	 * 
	 * @param tupleFactory
	 * @return
	 */
	public TupleSet lowerBound(TupleFactory tupleFactory);

	/**
	 * Returns the upper bound for the relation.
	 * 
	 * @param tupleFactory
	 * @return
	 */
	public TupleSet upperBound(TupleFactory tupleFactory);

	/**
	 * Returns the model in which the element is contained
	 * 
	 * @return
	 */
	public IModel model();

	/**
	 * Returns the formula for this element.
	 * 
	 * @return
	 */
	public Formula constraints();

	/**
	 * Returns the structural dependencies of this element.
	 * Used for decomposed solving to build dependency graph.
	 * 
	 * Default: empty set (no dependencies)
	 * Override in subclasses to provide specific dependencies:
	 * - Class: dependencies from inheritance (parent classes)
	 * - Association: dependencies from association ends (participating classes)
	 * - Attribute: dependencies from owner class
	 * 
	 * @return Set of relations this element depends on
	 */
	default Set<Relation> getDependencies() {
		return Collections.emptySet();
	}
}
