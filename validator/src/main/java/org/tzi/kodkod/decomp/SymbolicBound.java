package org.tzi.kodkod.decomp;

import java.util.HashSet;
import java.util.Set;

import kodkod.ast.BinaryExpression;
import kodkod.ast.Expression;
import kodkod.ast.NaryExpression;
import kodkod.ast.Relation;
import kodkod.ast.UnaryExpression;
import kodkod.instance.Bounds;
import kodkod.instance.TupleSet;
import kodkod.engine.Evaluator;
import kodkod.instance.Instance;

/**
 * Represents a symbolic bound for a relation.
 * 
 * In standard Kodkod, bounds are TupleSet (constant sets of tuples).
 * Symbolic bounds use Expressions that may reference other relations,
 * allowing dependency analysis for decomposition.
 * 
 * Example:
 * - Constant bound: Employee = {(e1), (e2), (e3)}
 * - Symbolic bound: Employee ⊆ Person (upperExpr = Person relation)
 * 
 * @author Custom implementation for thesis
 */
public class SymbolicBound {

    /** The relation this bound applies to */
    private final Relation relation;

    /**
     * Lower bound expression.
     * null means Expression.NONE (empty set)
     */
    private final Expression lowerExpr;

    /**
     * Upper bound expression.
     * This is the key - may reference other relations!
     */
    private final Expression upperExpr;

    /**
     * Cached set of relations that this bound depends on.
     * Extracted from lowerExpr and upperExpr.
     */
    private Set<Relation> dependencies;

    /** Resolved constant lower bound (after resolution) */
    private TupleSet resolvedLower;

    /** Resolved constant upper bound (after resolution) */
    private TupleSet resolvedUpper;

    /**
     * Creates a symbolic bound with both lower and upper expressions.
     * 
     * @param relation  The relation to bound
     * @param lowerExpr Lower bound expression (null for NONE)
     * @param upperExpr Upper bound expression (required)
     */
    public SymbolicBound(Relation relation, Expression lowerExpr, Expression upperExpr) {
        if (relation == null) {
            throw new IllegalArgumentException("Relation cannot be null");
        }
        if (upperExpr == null) {
            throw new IllegalArgumentException("Upper expression cannot be null");
        }

        this.relation = relation;
        this.lowerExpr = lowerExpr;
        this.upperExpr = upperExpr;
        this.dependencies = null; // Lazy computed
    }

    /**
     * Creates a symbolic bound with only upper expression (lower = NONE).
     */
    public SymbolicBound(Relation relation, Expression upperExpr) {
        this(relation, null, upperExpr);
    }

    /**
     * Gets the relation this bound applies to.
     */
    public Relation getRelation() {
        return relation;
    }

    /**
     * Gets the lower bound expression.
     * 
     * @return Lower expression, or null if NONE
     */
    public Expression getLowerExpr() {
        return lowerExpr;
    }

    /**
     * Alias for getLowerExpr() for compatibility.
     */
    public Expression getLowerBoundExpr() {
        return lowerExpr;
    }

    /**
     * Gets the upper bound expression.
     */
    public Expression getUpperExpr() {
        return upperExpr;
    }

    /**
     * Alias for getUpperExpr() for compatibility.
     */
    public Expression getUpperBoundExpr() {
        return upperExpr;
    }

    /**
     * Gets all relations that this bound depends on.
     * Dependencies are relations referenced in lowerExpr or upperExpr.
     * 
     * This is crucial for building the dependency graph!
     * 
     * @return Set of dependent relations (never includes self)
     */
    public Set<Relation> getDependencies() {
        if (dependencies == null) {
            dependencies = collectDependencies();
        }
        return dependencies;
    }

    /**
     * Collects relations from both bound expressions.
     */
    private Set<Relation> collectDependencies() {
        Set<Relation> deps = new HashSet<>();

        if (lowerExpr != null) {
            collectRelationsFromExpr(lowerExpr, deps);
        }
        collectRelationsFromExpr(upperExpr, deps);

        // Don't include self as dependency
        deps.remove(relation);

        return deps;
    }

    /**
     * Recursively collects all Relation nodes from an expression.
     */
    private void collectRelationsFromExpr(Expression expr, Set<Relation> result) {
        if (expr instanceof Relation) {
            result.add((Relation) expr);
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            collectRelationsFromExpr(bin.left(), result);
            collectRelationsFromExpr(bin.right(), result);
        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            collectRelationsFromExpr(unary.expression(), result);
        } else if (expr instanceof NaryExpression) {
            NaryExpression nary = (NaryExpression) expr;
            for (int i = 0; i < nary.size(); i++) {
                collectRelationsFromExpr(nary.child(i), result);
            }
        }
        // ConstantExpression and other types don't contain relations
    }

    /**
     * Checks if this bound is exact (lower == upper).
     * Exact bounds have no "search space" - relation value is fixed.
     */
    public boolean isExact() {
        if (lowerExpr == null) {
            return false;
        }
        return lowerExpr.equals(upperExpr);
    }

    /**
     * Checks if this bound has been resolved to constant TupleSets.
     */
    public boolean isResolved() {
        return resolvedLower != null && resolvedUpper != null;
    }

    /**
     * Sets the resolved constant bounds.
     * Called by BoundResolver after evaluating expressions.
     */
    public void setResolved(TupleSet lower, TupleSet upper) {
        this.resolvedLower = lower;
        this.resolvedUpper = upper;
    }

    /**
     * Sets the resolved lower bound.
     */
    public void setResolvedLower(TupleSet lower) {
        this.resolvedLower = lower;
    }

    /**
     * Sets the resolved upper bound.
     */
    public void setResolvedUpper(TupleSet upper) {
        this.resolvedUpper = upper;
    }

    /**
     * Gets resolved lower bound.
     * 
     * @throws IllegalStateException if not resolved yet
     */
    public TupleSet getResolvedLower() {
        if (resolvedLower == null) {
            throw new IllegalStateException("Bound not resolved yet: " + relation.name());
        }
        return resolvedLower;
    }

    /**
     * Gets resolved upper bound.
     * 
     * @throws IllegalStateException if not resolved yet
     */
    public TupleSet getResolvedUpper() {
        if (resolvedUpper == null) {
            throw new IllegalStateException("Bound not resolved yet: " + relation.name());
        }
        return resolvedUpper;
    }

    /**
     * Resolves the lower bound expression using given bounds.
     * 
     * @param bounds The bounds containing concrete TupleSets for dependencies
     * @return The resolved TupleSet, or null if cannot resolve
     */
    public TupleSet resolveLowerBound(Bounds bounds) {
        if (resolvedLower != null) {
            return resolvedLower;
        }
        if (lowerExpr == null) {
            // Create empty TupleSet
            return bounds.universe().factory().noneOf(relation.arity());
        }
        try {
            Instance tempInstance = createInstanceFromBounds(bounds);
            Evaluator eval = new Evaluator(tempInstance);
            return eval.evaluate(lowerExpr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves the upper bound expression using given bounds.
     * 
     * @param bounds The bounds containing concrete TupleSets for dependencies
     * @return The resolved TupleSet, or null if cannot resolve
     */
    public TupleSet resolveUpperBound(Bounds bounds) {
        if (resolvedUpper != null) {
            return resolvedUpper;
        }
        try {
            Instance tempInstance = createInstanceFromBounds(bounds);
            Evaluator eval = new Evaluator(tempInstance);
            return eval.evaluate(upperExpr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a temporary Instance from Bounds for evaluation.
     * Uses upper bounds as relation values.
     */
    private Instance createInstanceFromBounds(Bounds bounds) {
        Instance instance = new Instance(bounds.universe());
        for (Relation r : bounds.relations()) {
            TupleSet upper = bounds.upperBound(r);
            if (upper != null) {
                instance.add(r, upper);
            }
        }
        return instance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SymbolicBound[").append(relation.name());
        sb.append(": ");
        if (lowerExpr != null) {
            sb.append(lowerExpr);
        } else {
            sb.append("∅");
        }
        sb.append(" ⊆ ").append(relation.name()).append(" ⊆ ");
        sb.append(upperExpr);
        if (isResolved()) {
            sb.append(" (resolved)");
        }
        sb.append("]");
        return sb.toString();
    }
}
