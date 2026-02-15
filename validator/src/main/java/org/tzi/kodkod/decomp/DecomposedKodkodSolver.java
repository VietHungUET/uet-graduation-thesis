/*
 * Custom Decomposed Solving for USE Validator
 */
package org.tzi.kodkod.decomp;

import java.util.Map;
import java.util.Set;

import kodkod.ast.Formula;
import kodkod.ast.Relation;
import kodkod.engine.Solution;
import kodkod.engine.Solver;
import kodkod.engine.config.Options;
import kodkod.instance.Bounds;
import kodkod.instance.Instance;

import org.tzi.kodkod.decomp.FormulaSlicer.SliceResult;
import org.tzi.kodkod.decomp.RelationPartitioner.PartitionResult;

/**
 * Main decomposed solver for the USE validator.
 * 
 * <p>
 * This solver implements a two-phase decomposition strategy:
 * <ol>
 * <li><b>Phase 1 (Partial Problem)</b>: Solve for "core" relations (Rp) with
 * reduced formula (F1)</li>
 * <li><b>Phase 2 (Integrated Problem)</b>: Fix Rp values from Phase 1, solve
 * remaining relations with full formula</li>
 * </ol>
 * 
 * <p>
 * The key insight is that Phase 1 typically has fewer variables and
 * constraints,
 * making it faster to solve. Phase 2 then builds on that solution.
 * 
 * <p>
 * Workflow:
 * 
 * <pre>
 * 1. Build dependency graph from model relations
 * 2. Partition relations: Rp (core) + Rr (remainder)
 * 3. Slice formula: F1 (only Rp) + F2 (uses Rr)
 * 4. Solve Phase 1: F1 with bounds for Rp only
 * 5. If SAT: Use solution to create integrated bounds
 * 6. Solve Phase 2: Full formula with integrated bounds
 * </pre>
 * 
 * @author Custom implementation for thesis
 */
public class DecomposedKodkodSolver {

    private final DecomposedSolverConfig config;
    private final Solver solver1; // Partial solver
    private final Solver solver2; // Integrated solver
    private final SymbolicBoundsManager symbolicManager; // Symbolic bounds manager

    // Statistics
    private long phase1Time;
    private long phase2Time;
    private int phase1Solutions;

    /**
     * Creates a decomposed solver with the given configuration and symbolic bounds
     * manager.
     * 
     * @param config          Decomposition configuration
     * @param options         Kodkod solver options
     * @param symbolicManager Symbolic bounds manager for dependency tracking
     */
    public DecomposedKodkodSolver(DecomposedSolverConfig config, Options options,
            SymbolicBoundsManager symbolicManager) {
        this.config = config;
        this.solver1 = new Solver(options);
        this.solver2 = new Solver(options);
        this.symbolicManager = symbolicManager;
        resetStats();
    }

    /**
     * Creates a decomposed solver with the given configuration (without symbolic
     * bounds).
     * 
     * @param config  Decomposition configuration
     * @param options Kodkod solver options
     */
    public DecomposedKodkodSolver(DecomposedSolverConfig config, Options options) {
        this(config, options, null);
    }

    /**
     * Creates a decomposed solver with default configuration.
     * 
     * @param options Kodkod solver options
     */
    public DecomposedKodkodSolver(Options options) {
        this(new DecomposedSolverConfig(), options);
    }

    /**
     * Solves the given formula with bounds using decomposition strategy with
     * backtracking.
     * 
     * <p>
     * This method implements a backtracking loop that tries multiple partial
     * solutions
     * (configurations) until finding one that can be extended to a full solution.
     * 
     * <p>
     * Algorithm:
     * 
     * <pre>
     * 1. Partition relations into Rp (partial) and Rr (remainder)
     * 2. Slice formula into F1 (over Rp) and F2 (over Rr)
     * 3. For each configuration p satisfying F1:
     *    a. Integrate p into bounds (fix Rp values)
     *    b. Try to solve full formula with integrated bounds
     *    c. If SAT: return solution
     *    d. If UNSAT: try next configuration (implicit backtracking via iterator)
     * 4. If all configurations exhausted: return UNSAT
     * </pre>
     * 
     * @param formula The complete formula to solve
     * @param bounds  The bounds for all relations
     * @return Solution (SAT with instance, or UNSAT/TRIVIALLY_SAT/etc)
     */
    public Solution solve(Formula formula, Bounds bounds) {
        resetStats();

        // Step 1: Build dependency graph from bounds relations
        DependencyGraph depGraph = buildDependencyGraph(bounds);

        // Step 2: Partition relations
        RelationPartitioner partitioner = new RelationPartitioner(config.getThreshold());
        PartitionResult partition = partitioner.partition(depGraph);

        Set<Relation> partialRelations = partition.getPartialRelations();
        Set<Relation> remainderRelations = partition.getRemainderRelations();

        System.out.println("\n=== Relation Partitioning ===");
        System.out.println("Total relations: " + bounds.relations().size());
        System.out.println("Rp (partial, outdegree ≤ " + config.getThreshold() + "): " + partialRelations.size());
        System.out.println("Rr (remainder): " + remainderRelations.size());
        System.out.println("\nRp relations:");
        for (Relation r : partialRelations) {
            System.out.println("  - " + r.name());
        }
        System.out.println("\nRr relations:");
        for (Relation r : remainderRelations) {
            System.out.println("  - " + r.name());
        }
        System.out.println("==============================\n");

        // Check if decomposition is worthwhile
        if (partialRelations.isEmpty() || remainderRelations.isEmpty()) {
            System.out.println("⚠️  Decomposition NOT applicable (trivial partition)");
            System.out.println("   Falling back to standard solver...\n");
            // No benefit from decomposition - solve directly
            return solver1.solve(formula, bounds);
        }

        System.out.println("✓ Decomposition applicable! Starting 2-phase solving...\n");

        // Step 3: Slice formula
        SliceResult slice = FormulaSlicer.slice(formula, partialRelations);
        Formula partialFormula = slice.getPartialFormula();

        // Step 4: Create partial bounds (only Rp relations)
        Bounds partialBounds = createPartialBounds(bounds, partialRelations);

        // LOG: Show Phase 1 bounds
        System.out.println("\n=== PHASE 1 BOUNDS (Rp only) ===");
        System.out.println("Relations in Phase 1 bounds: " + partialBounds.relations().size());
        for (Relation r : partialBounds.relations()) {
            System.out.println("  " + r.name() + ":");
            System.out.println("    Lower: " + partialBounds.lowerBound(r));
            System.out.println("    Upper: " + partialBounds.upperBound(r));
        }
        System.out.println("=================================\n");

        // Step 5: Get iterator for all Phase 1 solutions (configurations)
        System.out.println("=== PHASE 1: Solving Rp ===");
        long startPhase1 = System.currentTimeMillis();
        java.util.Iterator<Solution> phase1Iterator = solver1.solveAll(partialFormula, partialBounds);

        // Step 6: Backtracking loop - try each configuration
        Solution lastUnsatSolution = null;
        int configCount = 0;
        while (phase1Iterator.hasNext()) {
            Solution phase1Solution = phase1Iterator.next();
            configCount++;

            // Update Phase 1 time (cumulative for all configs)
            phase1Time = System.currentTimeMillis() - startPhase1;

            if (!phase1Solution.sat()) {
                // No more configurations available
                lastUnsatSolution = phase1Solution;
                System.out.println("Phase 1: UNSAT\n");
                break;
            }

            phase1Solutions++;
            System.out.println("Phase 1: SAT (config #" + configCount + ")");

            // LOG: Show Phase 1 solution
            Instance partialInstance = phase1Solution.instance();
            System.out.println("\n--- Phase 1 Solution (Rp values) ---");
            for (Relation r : partialRelations) {
                if (partialInstance.contains(r)) {
                    System.out.println("  " + r.name() + " = " + partialInstance.tuples(r));
                }
            }
            System.out.println("-------------------------------------\n");

            // Step 7: Create integrated bounds using partial solution
            Bounds integratedBounds = createIntegratedBounds(
                    partialInstance, bounds, partialRelations, remainderRelations);

            // LOG: Show Phase 2 integrated bounds
            System.out.println("\n=== PHASE 2 INTEGRATED BOUNDS ===");
            System.out.println("Relations in Phase 2 bounds: " + integratedBounds.relations().size());
            System.out.println("\n[FIXED from Phase 1] Rp relations (exact bounds):");
            for (Relation r : partialRelations) {
                if (integratedBounds.lowerBound(r) != null) {
                    System.out.println("  " + r.name() + ":");
                    System.out.println("    Exact: " + integratedBounds.lowerBound(r));
                }
            }
            System.out.println("\n[VARIABLE] Rr relations (original bounds):");
            for (Relation r : remainderRelations) {
                if (integratedBounds.lowerBound(r) != null) {
                    System.out.println("  " + r.name() + ":");
                    System.out.println("    Lower: " + integratedBounds.lowerBound(r));
                    System.out.println("    Upper: " + integratedBounds.upperBound(r));
                }
            }
            System.out.println("==================================\n");

            // Step 8: Solve Phase 2 (Integrated Problem)
            System.out.println("=== PHASE 2: Solving full problem ===");
            long startPhase2 = System.currentTimeMillis();
            Solution phase2Solution = solver2.solve(formula, integratedBounds);
            phase2Time = System.currentTimeMillis() - startPhase2;

            if (phase2Solution.sat()) {
                // SUCCESS! Found valid complete solution
                System.out.println("Phase 2: SAT ✓\n");
                System.out.println("🎉 DECOMPOSED SOLVING SUCCESS!");
                System.out.println("   Configs tried: " + configCount);
                System.out.println("===================================\n");
                return phase2Solution;
            }

            // Phase 2 UNSAT: This configuration cannot be extended
            // Continue loop to try next configuration (iterator automatically adds ¬p)
            System.out.println("Phase 2: UNSAT (backtracking...)\n");
            lastUnsatSolution = phase2Solution;
        }

        // Exhausted all configurations without finding SAT solution
        // Return the last UNSAT solution (either from phase1 or phase2)
        return lastUnsatSolution != null ? lastUnsatSolution : solver1.solve(partialFormula, partialBounds);
    }

    /**
     * Builds dependency graph from bounds relations and symbolic/structural
     * dependencies.
     * If SymbolicBoundsManager is available, dependencies are extracted from:
     * 1. Symbolic bounds (expression-based)
     * 2. Structural dependencies (from IModelElement.getDependencies())
     * Otherwise, uses simple heuristic (all relations have no dependencies).
     */
    private DependencyGraph buildDependencyGraph(Bounds bounds) {
        DependencyGraph graph = new DependencyGraph();

        // Add all relations to the graph
        for (Relation r : bounds.relations()) {
            graph.addRelation(r);
        }

        // If we have a symbolic bounds manager, use it to populate dependencies
        if (symbolicManager != null) {
            System.out.println("\n=== Building Dependency Graph ===");

            // Use getAllDependencies() which includes both symbolic AND structural
            // dependencies
            Map<Relation, Set<Relation>> allDeps = symbolicManager.getAllDependencies();

            System.out.println("Total relations with dependencies: " + allDeps.size());

            for (Map.Entry<Relation, Set<Relation>> entry : allDeps.entrySet()) {
                Relation target = entry.getKey();
                Set<Relation> dependencies = entry.getValue();

                System.out.println("  " + target.name() + " → " + dependencies.size() + " dependencies");

                for (Relation dependency : dependencies) {
                    graph.addDependency(target, dependency);
                    System.out.println("    depends on: " + dependency.name());
                }
            }

            System.out.println("=================================\n");
        }
        // Otherwise, no dependencies are added (all relations have outdegree = 0)
        // This is the simple case when symbolic bounds are not used

        return graph;
    }

    /**
     * Creates bounds containing only the partial relations.
     */
    private Bounds createPartialBounds(Bounds original, Set<Relation> partialRelations) {
        Bounds partial = new Bounds(original.universe());

        for (Relation r : partialRelations) {
            if (original.lowerBound(r) != null && original.upperBound(r) != null) {
                partial.bound(r, original.lowerBound(r), original.upperBound(r));
            }
        }

        // Copy integer bounds
        copyIntBounds(original, partial);

        return partial;
    }

    /**
     * Creates integrated bounds by fixing partial solution and adding remainder.
     */
    private Bounds createIntegratedBounds(
            Instance partialInstance,
            Bounds original,
            Set<Relation> partialRelations,
            Set<Relation> remainderRelations) {

        Bounds integrated = new Bounds(original.universe());

        // Fix partial relations to their solution values (exact bounds)
        for (Relation r : partialRelations) {
            if (partialInstance.contains(r)) {
                integrated.boundExactly(r, partialInstance.tuples(r));
            }
        }

        // Add remainder relations with original bounds
        for (Relation r : remainderRelations) {
            if (original.lowerBound(r) != null && original.upperBound(r) != null) {
                integrated.bound(r, original.lowerBound(r), original.upperBound(r));
            }
        }

        // Copy integer bounds
        copyIntBounds(original, integrated);

        return integrated;
    }

    /**
     * Copies integer bounds from source to target.
     */
    /**
     * Copies integer bounds from source to target.
     */
    private void copyIntBounds(Bounds source, Bounds target) {
        kodkod.util.ints.IntSet ints = source.ints();
        kodkod.util.ints.IntIterator it = ints.iterator();
        while (it.hasNext()) {
            int i = it.next();
            target.boundExactly(i, source.exactBound(i));
        }
    }

    /**
     * Resets statistics.
     */
    private void resetStats() {
        phase1Time = 0;
        phase2Time = 0;
        phase1Solutions = 0;
    }

    /**
     * @return Time spent in Phase 1 (milliseconds)
     */
    public long getPhase1Time() {
        return phase1Time;
    }

    /**
     * @return Time spent in Phase 2 (milliseconds)
     */
    public long getPhase2Time() {
        return phase2Time;
    }

    /**
     * @return Total solving time (milliseconds)
     */
    public long getTotalTime() {
        return phase1Time + phase2Time;
    }

    /**
     * @return Number of Phase 1 solutions found
     */
    public int getPhase1Solutions() {
        return phase1Solutions;
    }

    /**
     * @return The configuration
     */
    public DecomposedSolverConfig getConfig() {
        return config;
    }
}
