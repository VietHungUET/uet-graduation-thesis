package org.tzi.kodkod.model.type;

import kodkod.ast.Expression;
import kodkod.ast.Relation;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

import org.tzi.kodkod.model.visitor.Visitor;

/**
 * Represents a sequence type.
 * 
 * <p>
 * At the Type level, SequenceType is similar to SetType - it delegates bounds
 * and expressions to its element type. The actual sequence encoding as ternary
 * relation (object, index, value) is handled at the Attribute level, not here.
 * </p>
 * 
 * <p>
 * This design keeps the Type hierarchy simple while allowing Attributes to
 * implement the appropriate storage structure for sequences.
 * </p>
 * 
 * @author SME Lab
 */
public class SequenceType extends Type {

    private Type elemType;
    private IntegerType indexType;

    SequenceType(Type elemType, IntegerType indexType) {
        this.elemType = elemType;
        this.indexType = indexType;
    }

    public Type elemType() {
        return elemType;
    }

    public IntegerType indexType() {
        return indexType;
    }

    @Override
    public Expression expression() {

        return elemType.expression();
    }

    @Override
    protected Relation createRelation() {
        return elemType.relation();
    }

    @Override
    public TupleSet lowerBound(TupleFactory tupleFactory) {

        return elemType.lowerBound(tupleFactory);
    }

    @Override
    public TupleSet upperBound(TupleFactory tupleFactory) {

        return elemType.upperBound(tupleFactory);
    }

    @Override
    public boolean isSequence() {
        return true;
    }

    @Override
    public boolean isCollection() {
        return true;
    }

    @Override
    public boolean isIntegerCollection() {
        return elemType.isInteger();
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSequenceType(this);
    }

    @Override
    public String toString() {
        return "Sequence(" + elemType.toString() + ")";
    }
}
