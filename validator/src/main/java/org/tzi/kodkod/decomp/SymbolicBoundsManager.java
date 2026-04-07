/*
 * Custom Decomposed Solving for USE Validator
 */
package org.tzi.kodkod.decomp;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kodkod.ast.Relation;
import kodkod.instance.Bounds;
import kodkod.instance.TupleSet;

/**
 * Manages symbolic bounds for all relations in a model.
 * 
 * <p>
 * This class serves as a central registry for symbolic bounds, providing:
 * <ul>
 * <li>Registration of symbolic bounds for relations</li>
 * <li>Building dependency graph from symbolic bounds</li>
 * <li>Resolving symbolic bounds to concrete TupleSets</li>
 * <li>Tracking which relations have symbolic vs constant bounds</li>
 * </ul>
 * 
 * @author Custom implementation for thesis
 */
public class SymbolicBoundsManager {

    /** Map from Relation to its SymbolicBound */
    private final Map<Relation, SymbolicBound> symbolicBounds;

    /** Set of relations with constant (already resolved) bounds */
    private final Set<Relation> constantRelations;

    /** Structural dependencies (from IModelElement.getDependencies()) */
    private final Map<Relation, Set<Relation>> structuralDependencies;

    private final Set<Relation> modelRelations;

    /** The dependency graph built from symbolic bounds */
    private DependencyGraph dependencyGraph;

    /**
     * Creates a new empty SymbolicBoundsManager.
     */
    public SymbolicBoundsManager() {
        this.symbolicBounds = new HashMap<>();
        this.constantRelations = new HashSet<>();
        this.structuralDependencies = new HashMap<>();
        this.modelRelations = new HashSet<>();
        this.dependencyGraph = null;
    }

    /**
     * Registers a symbolic bound for a relation.
     * 
     * @param bound The symbolic bound to register
     * @throws IllegalArgumentException if a bound for this relation already exists
     */
    public void registerSymbolicBound(SymbolicBound bound) {
        Relation relation = bound.getRelation();
        if (symbolicBounds.containsKey(relation)) {
            throw new IllegalArgumentException("Symbolic bound already registered for: " + relation.name());
        }
        symbolicBounds.put(relation, bound);

        // Invalidate cached dependency graph
        dependencyGraph = null;
    }

    /**
     * Registers a relation as having constant (non-symbolic) bounds.
     * Constant relations have fixed TupleSet bounds, not expression-based.
     * 
     * @param relation The relation with constant bounds
     */
    public void registerConstantRelation(Relation relation) {
        constantRelations.add(relation);
    }

    /**
     * Checks if a relation has symbolic bounds.
     */
    public boolean hasSymbolicBound(Relation relation) {
        return symbolicBounds.containsKey(relation);
    }

    /**
     * Checks if a relation has constant bounds.
     */
    public boolean isConstant(Relation relation) {
        return constantRelations.contains(relation);
    }

    /**
     * Gets the symbolic bound for a relation.
     * 
     * @param relation The relation
     * @return The symbolic bound, or null if not found
     */
    public SymbolicBound getSymbolicBound(Relation relation) {
        return symbolicBounds.get(relation);
    }

    /**
     * @return All relations with symbolic bounds
     */
    public Set<Relation> getSymbolicRelations() {
        return new HashSet<>(symbolicBounds.keySet());
    }

    /**
     * @return All constant relations
     */
    public Set<Relation> getConstantRelations() {
        return new HashSet<>(constantRelations);
    }

    /**
     * @return All registered symbolic bounds
     */
    public Collection<SymbolicBound> getAllSymbolicBounds() {
        return symbolicBounds.values();
    }

    /**
     * Builds and returns the dependency graph from all registered symbolic bounds.
     * The graph is cached and rebuilt only when bounds change.
     * 
     * @return The dependency graph
     */
    public DependencyGraph getDependencyGraph() {
        if (dependencyGraph == null) {
            dependencyGraph = buildDependencyGraph();
        }
        return dependencyGraph;
    }

    /**
     * Builds the dependency graph from symbolic bounds.
     */
    private DependencyGraph buildDependencyGraph() {
        DependencyGraph graph = new DependencyGraph();

        // Add all relations (both symbolic and constant)
        for (Relation r : symbolicBounds.keySet()) {
            graph.addRelation(r);
        }
        for (Relation r : constantRelations) {
            graph.addRelation(r);
        }
        for (Relation r : structuralDependencies.keySet()) {
            graph.addRelation(r);
        }

        // Add dependencies from symbolic bounds
        for (SymbolicBound bound : symbolicBounds.values()) {
            Relation target = bound.getRelation();
            for (Relation dependency : bound.getDependencies()) {
                graph.addDependency(target, dependency);
            }
        }

        // Add structural dependencies (from getDependencies())
        for (Map.Entry<Relation, Set<Relation>> entry : structuralDependencies.entrySet()) {
            Relation target = entry.getKey();
            for (Relation dependency : entry.getValue()) {
                graph.addDependency(target, dependency);
            }
        }

        return graph;
    }

    /**
     * Registers a structural dependency between two relations.
     * Used by BoundsVisitor to capture dependencies from
     * IModelElement.getDependencies().
     * 
     * @param target     The relation that depends on another
     * @param dependency The relation that target depends on
     */
    public void registerDependency(Relation target, Relation dependency) {
        structuralDependencies
                .computeIfAbsent(target, k -> new HashSet<>())
                .add(dependency);

        // Invalidate cached dependency graph
        dependencyGraph = null;
    }

    /**
     * Registers a model relation (class/attribute/association).
     * Must be called for EVERY model relation in BoundsVisitor, even those
     * with no dependencies. This allows correct partitioning in the decomposed
     * solver.
     *
     * @param relation The model relation to register
     */
    public void registerModelRelation(Relation relation) {
        modelRelations.add(relation);
    }

    /**
     * Returns the set of ALL model relations registered via
     * registerModelRelation().
     * This is the authoritative set of relations that should be partitioned
     * into Rp/Rr by the decomposed solver.
     *
     * @return Unmodifiable set of all model relations
     */
    public Set<Relation> getAllModelRelations() {
        return java.util.Collections.unmodifiableSet(modelRelations);
    }

    /**
     * Gets all structural dependencies.
     * 
     * @return Map from relation to its dependencies
     */
    public Map<Relation, Set<Relation>> getAllDependencies() {
        Map<Relation, Set<Relation>> result = new HashMap<>();

        // Add from symbolic bounds
        for (SymbolicBound bound : symbolicBounds.values()) {
            result.put(bound.getRelation(), bound.getDependencies());
        }

        // Add structural dependencies
        for (Map.Entry<Relation, Set<Relation>> entry : structuralDependencies.entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                Set<Relation> merged = new HashSet<>(v1);
                merged.addAll(v2);
                return merged;
            });
        }

        // Ensure all model relations appear (even those with 0 dependencies)
        for (Relation r : modelRelations) {
            result.computeIfAbsent(r, k -> new HashSet<>());
        }

        return result;
    }

    /**
     * Resolves all symbolic bounds using the given concrete bounds.
     * This converts expression-based bounds to TupleSet bounds.
     * 
     * @param concreteBounds The bounds with concrete TupleSets for resolved
     *                       relations
     * @return A new Bounds object with all symbolic bounds resolved
     */
    public Bounds resolveAll(Bounds concreteBounds) {
        // Clone the concrete bounds
        Bounds resolved = concreteBounds.clone();

        // Resolve in dependency order (dependencies first)
        for (SymbolicBound bound : symbolicBounds.values()) {
            if (!bound.isResolved()) {
                TupleSet lower = bound.resolveLowerBound(resolved);
                TupleSet upper = bound.resolveUpperBound(resolved);

                if (lower != null && upper != null) {
                    resolved.bound(bound.getRelation(), lower, upper);
                    bound.setResolved(lower, upper);
                }
            }
        }

        return resolved;
    }

    /**
     * Marks a relation's symbolic bound as resolved with concrete TupleSets.
     * 
     * @param relation The relation
     * @param lower    The resolved lower bound
     * @param upper    The resolved upper bound
     */
    public void markResolved(Relation relation, TupleSet lower, TupleSet upper) {
        SymbolicBound bound = symbolicBounds.get(relation);
        if (bound != null) {
            bound.setResolved(lower, upper);
        }
    }

    /**
     * Resets all resolved bounds (for backtracking).
     */
    public void resetResolutions() {
        for (SymbolicBound bound : symbolicBounds.values()) {
            bound.setResolvedLower(null);
            bound.setResolvedUpper(null);
        }
    }

    /**
     * @return Number of symbolic bounds registered
     */
    public int symbolicCount() {
        return symbolicBounds.size();
    }

    /**
     * @return Number of constant relations registered
     */
    public int constantCount() {
        return constantRelations.size();
    }

    /**
     * @return Total number of relations managed
     */
    public int totalCount() {
        return symbolicBounds.size() + constantRelations.size();
    }

    @Override
    public String toString() {
        return String.format("SymbolicBoundsManager[symbolic=%d, constant=%d]",
                symbolicCount(), constantCount());
    }
}
