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

        // Relations with outdegree == max → Rr; all others → Rp
        for (Relation r : graph.getAllRelations()) {
            if (graph.getOutDegree(r) == maxOutDegree) {
                remainderRelations.add(r);
            } else {
                partialRelations.add(r);
            }
        }

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
