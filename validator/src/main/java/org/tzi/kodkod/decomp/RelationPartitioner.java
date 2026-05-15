package org.tzi.kodkod.decomp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import kodkod.ast.Relation;

/**
 * Partitions relations into partial set (Rp) and remainder set (R \ Rp).
 * 
 * The partial set Rp contains relations that will be solved first in P1.
 * Selection is based on out-degree in the dependency graph:
 * - Relations with outdegree <= threshold go to Rp
 * - Relations with outdegree > threshold go to remainder
 * 
 * Based on Pardinus relation partitioning strategy.
 * 
 * @author Custom implementation for thesis
 */
public class RelationPartitioner {

    private final int threshold;

    /**
     * Creates a partitioner with the specified threshold.
     * 
     * @param threshold Maximum out-degree for partial relations (default: 2)
     */
    public RelationPartitioner(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold must be >= 0");
        }
        this.threshold = threshold;
    }

    /**
     * Creates a partitioner with default threshold (2).
     */
    public RelationPartitioner() {
        this(2);
    }

    /**
     * Partitions relations based on the dependency graph.
     * Relations with the <b>maximum out-degree</b> are placed in Rr (remainder).
     * All other relations go to Rp (partial).
     *
     * @param graph The dependency graph
     * @return PartitionResult containing Rp and remainder sets
     */
    public PartitionResult partition(DependencyGraph graph) {
        Set<Relation> partialRelations = new HashSet<>();
        Set<Relation> remainderRelations = new HashSet<>();

        if (graph.getAllRelations().isEmpty()) {
            return new PartitionResult(partialRelations, remainderRelations, 0);
        }

        // Find the maximum out-degree in the graph
        int maxOutDegree = 0;
        for (Relation r : graph.getAllRelations()) {
            int deg = graph.getOutDegree(r);
            if (deg > maxOutDegree) {
                maxOutDegree = deg;
            }
        }

        // 1. Identify removed nodes: outDegree == maxOutDegree OR arity == 3
        Set<Relation> removedNodes = new HashSet<>();
        Set<Relation> subgraphNodes = new HashSet<>();
        for (Relation r : graph.getAllRelations()) {
            if (graph.getOutDegree(r) == maxOutDegree || r.arity() == 3) {
                removedNodes.add(r);
            } else {
                subgraphNodes.add(r);
            }
        }

        // 2. Build undirected adjacency list for subgraphNodes
        java.util.Map<Relation, Set<Relation>> undirectedAdj = new java.util.HashMap<>();
        for (Relation r : subgraphNodes) {
            undirectedAdj.put(r, new HashSet<>());
        }

        for (Relation r : subgraphNodes) {
            for (Relation dep : graph.getDirectDependencies(r)) {
                if (subgraphNodes.contains(dep)) {
                    // Add undirected edge between r and dep
                    undirectedAdj.get(r).add(dep);
                    undirectedAdj.get(dep).add(r);
                }
            }
        }

        // 3. Find connected components
        java.util.List<Set<Relation>> components = new java.util.ArrayList<>();
        Set<Relation> visited = new HashSet<>();

        for (Relation r : subgraphNodes) {
            if (!visited.contains(r)) {
                Set<Relation> component = new HashSet<>();
                java.util.Queue<Relation> queue = new java.util.LinkedList<>();
                queue.add(r);
                visited.add(r);
                component.add(r);

                while (!queue.isEmpty()) {
                    Relation current = queue.poll();
                    for (Relation neighbor : undirectedAdj.get(current)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            component.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }

        // 4. Find the largest component (prefer non-primitive when all size == 1)
        Set<Relation> largestComponent = new HashSet<>();

        // Check if all components have size == 1
        boolean allSizeOne = components.stream().allMatch(c -> c.size() == 1);

        if (allSizeOne) {
            // Prefer a non-primitive component (e.g. Room, Card, Guest)
            // Primitive types are already hardcoded into Rp, no need to pick them here
            Set<String> primitiveNames = new HashSet<>(java.util.Arrays.asList(
                "String", "Boolean", "Undefined", "Undefined_Set", "Real", "Integer"
            ));
            for (Set<Relation> component : components) {
                Relation r = component.iterator().next();
                if (!primitiveNames.contains(r.name())) {
                    largestComponent = component;
                    break;
                }
            }
            // Fallback: if only primitives remain (shouldn't happen), pick first
            if (largestComponent.isEmpty() && !components.isEmpty()) {
                largestComponent = components.get(0);
            }
        } else {
            // Normal case: pick the genuinely largest component
            for (Set<Relation> component : components) {
                if (component.size() > largestComponent.size()) {
                    largestComponent = component;
                }
            }
        }

        // 5. Assign to Rp and Rr
        partialRelations.addAll(largestComponent);
        
        // Bắt buộc đưa các Type Relations cơ bản vào Tập cơ sở (Rp)
        // Nếu không, Slicer sẽ đẩy mọi constraint có String/Boolean sang Pha 2
        for (Relation r : graph.getAllRelations()) {
            String name = r.name();
            if (name.equals("String") || name.equals("Boolean") || 
                name.equals("Undefined") || name.equals("Undefined_Set") || 
                name.equals("Real") || name.equals("Integer")) {
                partialRelations.add(r);
            }
        }

        remainderRelations.addAll(graph.getAllRelations());
        remainderRelations.removeAll(partialRelations);

        System.out.println("[RelationPartitioner] Removed nodes (maxDeg=" + maxOutDegree + " or arity=3): " + removedNodes.size());
        System.out.println("[RelationPartitioner] Removed: " + removedNodes.stream().map(Relation::name).sorted().collect(java.util.stream.Collectors.joining(", ")));
        System.out.println("[RelationPartitioner] Subgraph nodes (" + subgraphNodes.size() + "): " + subgraphNodes.stream().map(Relation::name).sorted().collect(java.util.stream.Collectors.joining(", ")));
        System.out.println("[RelationPartitioner] Connected components found: " + components.size());
        for (int i = 0; i < components.size(); i++) {
            String names = components.get(i).stream().map(Relation::name).sorted().collect(java.util.stream.Collectors.joining(", "));
            System.out.println("  - Component " + (i+1) + " size: " + components.get(i).size() + " -> [" + names + "]");
        }
        System.out.println("[RelationPartitioner] Largest component selected: " + largestComponent.stream().map(Relation::name).sorted().collect(java.util.stream.Collectors.joining(", ")));
        System.out.println("[RelationPartitioner] Final Rp: " + partialRelations.stream().map(Relation::name).sorted().collect(java.util.stream.Collectors.joining(", ")));

        return new PartitionResult(partialRelations, remainderRelations, maxOutDegree);
    }

    /**
     * Result of relation partitioning.
     */
    public static class PartitionResult {

        /** Relations for partial problem P1 */
        private final Set<Relation> partialRelations;

        /** Remaining relations */
        private final Set<Relation> remainderRelations;

        /** Threshold used for partitioning */
        private final int threshold;

        public PartitionResult(Set<Relation> partialRelations,
                Set<Relation> remainderRelations,
                int threshold) {
            this.partialRelations = Collections.unmodifiableSet(partialRelations);
            this.remainderRelations = Collections.unmodifiableSet(remainderRelations);
            this.threshold = threshold;
        }

        /**
         * Gets relations for partial problem (Rp).
         * These will be solved first in P1.
         */
        public Set<Relation> getPartialRelations() {
            return partialRelations;
        }

        /**
         * Gets remaining relations (R \ Rp).
         * These will be solved in the integrated problem.
         */
        public Set<Relation> getRemainderRelations() {
            return remainderRelations;
        }

        /**
         * Gets the threshold used for partitioning.
         */
        public int getThreshold() {
            return threshold;
        }

        /**
         * Checks if a relation is in the partial set.
         */
        public boolean isPartial(Relation r) {
            return partialRelations.contains(r);
        }

        /**
         * Checks if partitioning is trivial (all or none in partial).
         */
        public boolean isTrivial() {
            return partialRelations.isEmpty() || remainderRelations.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PartitionResult[threshold=").append(threshold);
            sb.append(", partial=").append(partialRelations.size());
            sb.append(", remainder=").append(remainderRelations.size());
            sb.append("]\n");

            sb.append("  Rp (partial): {");
            boolean first = true;
            for (Relation r : partialRelations) {
                if (!first)
                    sb.append(", ");
                sb.append(r.name());
                first = false;
            }
            sb.append("}\n");

            sb.append("  R\\Rp (remainder): {");
            first = true;
            for (Relation r : remainderRelations) {
                if (!first)
                    sb.append(", ");
                sb.append(r.name());
                first = false;
            }
            sb.append("}");

            return sb.toString();
        }
    }

    /**
     * Gets the threshold value.
     */
    public int getThreshold() {
        return threshold;
    }
}
