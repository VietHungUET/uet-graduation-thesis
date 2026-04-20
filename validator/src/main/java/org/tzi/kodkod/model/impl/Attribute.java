package org.tzi.kodkod.model.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.PrintHelper;
import org.tzi.kodkod.model.config.IConfigurator;
import org.tzi.kodkod.model.config.impl.AttributeConfigurator;
import org.tzi.kodkod.model.iface.IAttribute;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.Type;
import org.tzi.kodkod.model.visitor.Visitor;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.Relation;
import kodkod.ast.Variable;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

/**
 * Implementation of IAttribute.
 * 
 * @author Hendrik Reitmann
 */
public class Attribute extends ModelElement implements IAttribute {

	private static final Logger LOG = Logger.getLogger(Attribute.class);

	protected Type type;
	protected IClass owner;
	protected IConfigurator<IAttribute> configurator;

	Attribute(IModel model, String name, Type type, IClass owner) {
		super(model, name);
		this.type = type;
		this.owner = owner;

		// For Sequence attributes, use ternary relation: (object, index, value)
		if (type.isSequence()) {
			relation = Relation.ternary(owner.name() + "_" + name);
		} else {
			relation = Relation.binary(owner.name() + "_" + name);
		}
	}

	@Override
	public TupleSet lowerBound(TupleFactory tupleFactory) {
		int arity = type.isSequence() ? 3 : 2;
		return configurator.lowerBound(this, arity, tupleFactory);
	}

	@Override
	public TupleSet upperBound(TupleFactory tupleFactory) {
		int arity = type.isSequence() ? 3 : 2;
		return configurator.upperBound(this, arity, tupleFactory);
	}

	@Override
	public Formula constraints() {
		Formula formula = Formula.and(domainDefinition(), typeDefinition(), multiplicityDefinition());
		return formula.and(configurator.constraints(this));
	}

	/**
	 * Creates the formula for the domain definition.
	 */
	private Formula domainDefinition() {
		System.out.println("\n### domainDefinition() for " + name() + " ###");
		System.out.println("Type: " + type);
		System.out.println("isSequence: " + type.isSequence());

		Formula formula;
		if (type.isSequence()) {
			// For ternary relation (object, index, value): first column must be owner
			// objects
			Expression projection = relation.join(Expression.UNIV).join(Expression.UNIV);
			System.out.println("Projection: " + projection);
			System.out.println("Projection arity: " + projection.arity());
			System.out.println("Owner relation: " + getOwnerRelation());

			formula = projection.in(getOwnerRelation());
		} else {
			formula = relation.join(Expression.UNIV).in(getOwnerRelation());
		}

		System.out.println("Domain formula: " + formula);
		System.out.println("######################################\n");
		LOG.debug("Domain of " + name() + ": " + PrintHelper.prettyKodkod(formula));
		return formula;
	}

	private Formula typeDefinition() {
		Relation undefined = model.typeFactory().undefinedType().relation();
		Relation undefinedSet = model.typeFactory().undefinedSetType().relation();

		System.out.println("\n### typeDefinition() for " + name() + " ###");
		System.out.println("Type: " + type);
		System.out.println("isSequence: " + type.isSequence());
		System.out.println("isSet: " + type.isSet());

		Formula formula;
		if (type.isSet()) {
			formula = Expression.UNIV.join(relation).in(type.expression().union(undefined).union(undefinedSet));
		} else if (type.isSequence()) {
			// For Sequence: ternary relation (object, index, value)
			// Indices must be integers, values must be of element type
			org.tzi.kodkod.model.type.SequenceType seqType = (org.tzi.kodkod.model.type.SequenceType) type;
			Expression valueExpression = seqType.elemType().expression();

			System.out.println("valueExpression: " + valueExpression);
			System.out.println("valueExpression arity: " + valueExpression.arity());

			// UNIV.join(relation) gives (index, value) with arity 2
			Expression indexValuePairs = Expression.UNIV.join(relation);
			System.out.println("indexValuePairs: " + indexValuePairs);
			System.out.println("indexValuePairs arity: " + indexValuePairs.arity());

			// For arity 2 expressions, we need undefinedSet_2 for comparison
			Expression undefinedSet_2 = undefinedSet.product(Expression.UNIV);
			System.out.println("undefinedSet_2: " + undefinedSet_2);
			System.out.println("undefinedSet_2 arity: " + undefinedSet_2.arity());

			// Project to index column: (index, value).join(UNIV) gives index
			Formula indexFormula = indexValuePairs.join(Expression.UNIV).in(Expression.INTS);
			System.out.println("indexFormula: " + indexFormula);

			// Project to value column: UNIV.join((index, value)) gives value
			Expression valueColumn = Expression.UNIV.join(indexValuePairs);
			System.out.println("valueColumn: " + valueColumn);
			System.out.println("valueColumn arity: " + valueColumn.arity());

			Formula valueFormula = valueColumn.in(valueExpression.union(undefined));
			System.out.println("valueFormula: " + valueFormula);

			// The whole expression can be undefined (arity 2)
			Formula notUndefinedFormula = indexValuePairs.eq(undefinedSet_2).not();
			System.out.println("notUndefinedFormula: " + notUndefinedFormula);

			formula = notUndefinedFormula.implies(indexFormula.and(valueFormula));
			System.out.println("Final formula: " + formula);
		} else {
			formula = Expression.UNIV.join(relation).in(type.expression().union(undefined));
		}

		System.out.println("######################################\n");
		LOG.debug("Type of " + name() + ": " + PrintHelper.prettyKodkod(formula));
		return formula;
	}

	/**
	 * Creates the formula for the multiplicity definition of an attribute.
	 */
	private Formula multiplicityDefinition() {
		final Variable c = Variable.unary("c");
		Relation ownerRelation = getOwnerRelation();

		Formula formula;
		if (type.isSet()) {
			Relation undefinedSet = model.typeFactory().undefinedSetType().relation();
			formula = undefinedSet.in(c.join(relation)).implies(c.join(relation).one()).forAll(c.oneOf(ownerRelation));
		} else if (type.isSequence()) {
			Expression slice = c.join(relation); // binary (Index, Value) for object c
			Formula functional = slice.transpose().join(slice).in(Expression.IDEN);
			formula = functional.forAll(c.oneOf(ownerRelation));
		} else {
			formula = c.join(relation).one().forAll(c.oneOf(ownerRelation));
		}

		LOG.debug("Mult for " + name() + ": " + PrintHelper.prettyKodkod(formula));
		return formula;
	}

	/**
	 * Returns the relation of the owner.
	 */
	private Relation getOwnerRelation() {
		return owner.inheritanceOrRegularRelation();
	}

	@Override
	public IClass owner() {
		return owner;
	}

	@Override
	public Type type() {
		return type;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visitAttribute(this);
	}

	@Override
	public void setConfigurator(IConfigurator<IAttribute> configurator) {
		this.configurator = configurator;
	}

	@Override
	public IConfigurator<IAttribute> getConfigurator() {
		return configurator;
	}

	@Override
	public void resetConfigurator() {
		configurator = new AttributeConfigurator(this);
	}

	@Override
	public Set<Relation> getDependencies() {
		Set<Relation> deps = new java.util.LinkedHashSet<>();

		// Attribute dependencies: Attribute depends on its owner class
		// Example: Person_name → depends on Person
		// Example: Book_title → depends on Book
		if (owner != null) {
			deps.add(owner.inheritanceOrRegularRelation());
		}

		// Type dependencies (for complex types)
		// Note: For simple types (Integer, String, Boolean), no additional dependencies
		// For object types (class references), those would be captured here
		deps.addAll(type.dependencies());

		return deps;
	}
}
