/*
 * Custom Decomposed Solving for USE Validator
 */
package org.tzi.kodkod.decomp;

import java.util.Set;

import kodkod.ast.Expression;
import kodkod.ast.Relation;
import kodkod.engine.Evaluator;
import kodkod.instance.Bounds;
import kodkod.instance.Instance;
import kodkod.instance.TupleSet;
import kodkod.instance.Universe;

/**
 * Resolves symbolic bounds by evaluating expressions against a partial
 * solution.
 * 
 * <p>
 * When a partial solution is found, relations in the remainder set may have
 * symbolic bounds that depend on the partial solution. This class evaluates
 * those expression-based bounds to produce concrete TupleSets.
 * 
 * <p>
 * Example:
 * 
 * <pre>
 * // If R2's upper bound is defined as: R1.product(Atom)
 * // And partial solution gives R1 = {(a,b), (c,d)}
 * // Then resolved upper bound for R2 = {(a,b,x), (c,d,x)} for all atoms x
 * </pre>
 * 
 * @author Custom implementation for thesis
 */
public class BoundResolver {

    private final SymbolicBoundsManager manager;

    /**
     * Creates a resolver for the given symbolic bounds manager.
     * 
     * @param manager The symbolic bounds manager
     */
    public BoundResolver(SymbolicBoundsManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("Manager cannot be null");
        }
        this.manager = manager;
    }

    /**
     * Resolves symbolic bounds for remainder relations using a partial solution.
     * 
     * @param partialInstance    The instance from the partial solution
     * @param originalBounds     The original bounds (with symbolic expressions)
     * @param remainderRelations The relations whose bounds need resolution
     * @return New bounds with resolved TupleSets for remainder relations
     */
    public Bounds resolveRemainderBounds(
            Instance partialInstance,
            Bounds originalBounds,
            Set<Relation> remainderRelations) {

        if (partialInstance == null) {
            throw new IllegalArgumentException("Partial instance cannot be null");
        }

        Universe universe = originalBounds.universe();
        Bounds resolvedBounds = new Bounds(universe);

        // Create evaluator for the partial solution
        Evaluator evaluator = new Evaluator(partialInstance);

        // Copy bounds for partial relations (fixed from solution)
        for (Relation r : partialInstance.relations()) {
            TupleSet tuples = partialInstance.tuples(r);
            resolvedBounds.boundExactly(r, tuples);
        }

        // Resolve symbolic bounds for remainder relations
        for (Relation r : remainderRelations) {
            if (manager.hasSymbolicBound(r)) {
                SymbolicBound symBound = manager.getSymbolicBound(r);

                TupleSet lower = resolveBound(symBound.getLowerBoundExpr(), evaluator, originalBounds, r);
                TupleSet upper = resolveBound(symBound.getUpperBoundExpr(), evaluator, originalBounds, r);

                if (lower != null && upper != null) {
                    resolvedBounds.bound(r, lower, upper);
                } else {
                    // Fall back to original bounds
                    copyOriginalBound(r, originalBounds, resolvedBounds);
                }
            } else {
                // No symbolic bound - copy from original
                copyOriginalBound(r, originalBounds, resolvedBounds);
            }
        }

        return resolvedBounds;
    }

    /**
     * Resolves a single expression bound using the evaluator.
     * 
     * @param expr           The expression to evaluate (may be null for constant
     *                       bound)
     * @param evaluator      The evaluator with partial solution
     * @param originalBounds Fallback bounds
     * @param relation       The relation (for fallback)
     * @return The resolved TupleSet
     */
    private TupleSet resolveBound(
            Expression expr,
            Evaluator evaluator,
            Bounds originalBounds,
            Relation relation) {

        if (expr == null) {
            // No expression - use original bound
            return null;
        }

        try {
            // Evaluate the expression against the partial solution
            TupleSet result = evaluator.evaluate(expr);
            return result;
        } catch (Exception e) {
            // Evaluation failed - return null to trigger fallback
            return null;
        }
    }

    /**
     * Copies the bound for a relation from original to target bounds.
     */
    private void copyOriginalBound(Relation r, Bounds original, Bounds target) {
        TupleSet lower = original.lowerBound(r);
        TupleSet upper = original.upperBound(r);

        if (lower != null && upper != null) {
            target.bound(r, lower, upper);
        }
    }

    /**
     * Creates an integrated bounds object that combines:
     * - Fixed values from partial solution (for partial relations)
     * - Resolved bounds for remainder relations
     * 
     * @param partialInstance    The partial solution instance
     * @param originalBounds     The original bounds
     * @param partialRelations   Relations solved in partial problem
     * @param remainderRelations Relations for integrated problem
     * @return Integrated bounds ready for the integrated solver
     */
    public Bounds createIntegratedBounds(
            Instance partialInstance,
            Bounds originalBounds,
            Set<Relation> partialRelations,
            Set<Relation> remainderRelations) {

        Universe universe = originalBounds.universe();
        Bounds integratedBounds = new Bounds(universe);
        Evaluator evaluator = new Evaluator(partialInstance);

        // Fix partial relations to their solution values
        for (Relation r : partialRelations) {
            if (partialInstance.contains(r)) {
                TupleSet tuples = partialInstance.tuples(r);
                integratedBounds.boundExactly(r, tuples);
            }
        }

        // Resolve and add remainder relations
        for (Relation r : remainderRelations) {
            if (manager.hasSymbolicBound(r)) {
                SymbolicBound symBound = manager.getSymbolicBound(r);

                TupleSet lower = resolveBound(symBound.getLowerBoundExpr(), evaluator, originalBounds, r);
                TupleSet upper = resolveBound(symBound.getUpperBoundExpr(), evaluator, originalBounds, r);

                if (lower != null && upper != null) {
                    integratedBounds.bound(r, lower, upper);
                } else {
                    copyOriginalBound(r, originalBounds, integratedBounds);
                }
            } else {
                copyOriginalBound(r, originalBounds, integratedBounds);
            }
        }

        // Copy int bounds
        for (int i = originalBounds.ints().min(); i <= originalBounds.ints().max(); i++) {
            if (originalBounds.ints().contains(i)) {
                integratedBounds.boundExactly(i, originalBounds.exactBound(i));
            }
        }

        return integratedBounds;
    }
}
