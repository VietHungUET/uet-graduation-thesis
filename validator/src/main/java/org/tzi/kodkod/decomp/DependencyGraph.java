package org.tzi.kodkod.decomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import kodkod.ast.Relation;

/**
 * Dependency graph between relations based on symbolic bounds.
 * 
 * If relation A's symbolic bound references relation B, then A depends on B.
 * This graph is used to:
 * 1. Determine resolution order (topological sort)
 * 2. Partition relations for decomposed solving
 * 
 * Based on Pardinus PardinusBounds.SymbolicStructures.deps concept.
 * 
 * @author Custom implementation for thesis
 */
public class DependencyGraph {

    /**
     * Adjacency list: relation -> set of relations it depends on.
     * If A depends on B: adjacency.get(A).contains(B)
     */
    private final Map<Relation, Set<Relation>> adjacency;

    /**
     * All relations in the graph (including those with no dependencies).
     */
    private final Set<Relation> allRelations;

    /**
     * Creates an empty dependency graph.
     */
    public DependencyGraph() {
        this.adjacency = new HashMap<>();
        this.allRelations = new HashSet<>();
    }

    /**
     * Adds a relation to the graph (even if it has no dependencies).
     */
    public void addRelation(Relation relation) {
        allRelations.add(relation);
        if (!adjacency.containsKey(relation)) {
            adjacency.put(relation, new HashSet<>());
        }
    }

    /**
     * Adds a dependency: 'from' depends on 'to'.
     * 
     * @param from The relation with the symbolic bound
     * @param to   The relation referenced in the bound expression
     */
    public void addDependency(Relation from, Relation to) {
        addRelation(from);
        addRelation(to);
        adjacency.get(from).add(to);
    }

    /**
     * Gets direct dependencies of a relation.
     * 
     * @param relation The relation to query
     * @return Set of relations that 'relation' directly depends on
     */
    public Set<Relation> getDirectDependencies(Relation relation) {
        Set<Relation> deps = adjacency.get(relation);
        if (deps == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(deps);
    }

    /**
     * Gets transitive dependencies (reflexive-transitive closure).
     * Includes all relations reachable from 'relation'.
     * 
     * @param relation The starting relation
     * @return All relations that 'relation' transitively depends on
     */
    public Set<Relation> getTransitiveDependencies(Relation relation) {
        Set<Relation> result = new HashSet<>();
        collectTransitiveDeps(relation, result);
        return result;
    }

    private void collectTransitiveDeps(Relation relation, Set<Relation> visited) {
        Set<Relation> directDeps = adjacency.get(relation);
        if (directDeps == null) {
            return;
        }

        for (Relation dep : directDeps) {
            if (!visited.contains(dep)) {
                visited.add(dep);
                collectTransitiveDeps(dep, visited);
            }
        }
    }

    /**
     * Gets the out-degree of a relation (number of dependencies).
     * Used for partition threshold check.
     * 
     * @param relation The relation to query
     * @return Number of direct dependencies
     */
    public int getOutDegree(Relation relation) {
        Set<Relation> deps = adjacency.get(relation);
        return deps == null ? 0 : deps.size();
    }

    /**
     * Gets the in-degree of a relation (number of dependents).
     * 
     * @param relation The relation to query
     * @return Number of relations that depend on this relation
     */
    public int getInDegree(Relation relation) {
        int count = 0;
        for (Set<Relation> deps : adjacency.values()) {
            if (deps.contains(relation)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets all relations with out-degree <= threshold.
     * These are candidates for the partial problem (Rp).
     * 
     * @param threshold Maximum out-degree
     * @return Set of relations with low out-degree
     */
    public Set<Relation> getRelationsWithMaxOutDegree(int threshold) {
        Set<Relation> result = new HashSet<>();
        for (Relation r : allRelations) {
            if (getOutDegree(r) <= threshold) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * Returns topological sort of relations.
     * Relations with no dependencies come first.
     * Used for resolving symbolic bounds in correct order.
     * 
     * @return List of relations in topological order
     * @throws IllegalStateException if graph has cycles
     */
    public List<Relation> topologicalSort() {
        // Kahn's algorithm
        Map<Relation, Integer> inDegree = new HashMap<>();
        for (Relation r : allRelations) {
            inDegree.put(r, 0);
        }

        // Calculate in-degrees
        for (Relation r : allRelations) {
            for (Relation dep : getDirectDependencies(r)) {
                inDegree.put(dep, inDegree.getOrDefault(dep, 0) + 1);
            }
        }

        // Start with relations that have no dependents
        Queue<Relation> queue = new LinkedList<>();
        for (Relation r : allRelations) {
            if (inDegree.get(r) == 0) {
                queue.add(r);
            }
        }

        List<Relation> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Relation r = queue.poll();
            result.add(r);

            for (Relation dep : getDirectDependencies(r)) {
                int newDegree = inDegree.get(dep) - 1;
                inDegree.put(dep, newDegree);
                if (newDegree == 0) {
                    queue.add(dep);
                }
            }
        }

        if (result.size() != allRelations.size()) {
            throw new IllegalStateException("Dependency graph has cycles!");
        }

        // Reverse to get correct resolution order (dependencies first)
        Collections.reverse(result);
        return result;
    }

    /**
     * Checks if the graph has any cycles.
     * Symbolic bounds with cycles are invalid.
     */
    public boolean hasCycles() {
        try {
            topologicalSort();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * Gets all relations in the graph.
     */
    public Set<Relation> getAllRelations() {
        return Collections.unmodifiableSet(allRelations);
    }

    /**
     * Gets the number of relations in the graph.
     */
    public int size() {
        return allRelations.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DependencyGraph {\n");
        for (Relation r : allRelations) {
            sb.append("  ").append(r.name());
            Set<Relation> deps = getDirectDependencies(r);
            if (!deps.isEmpty()) {
                sb.append(" -> {");
                boolean first = true;
                for (Relation dep : deps) {
                    if (!first)
                        sb.append(", ");
                    sb.append(dep.name());
                    first = false;
                }
                sb.append("}");
            }
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
