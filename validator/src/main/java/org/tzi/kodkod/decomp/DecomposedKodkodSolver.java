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

        // Check if decomposition is worthwhile
        if (partialRelations.isEmpty() || remainderRelations.isEmpty()) {
            System.out.println("Decomposition NOT applicable (trivial partition)");
            System.out.println("Falling back to standard solver...\n");
            // No benefit from decomposition - solve directly
            Solution fallbackSolution = solver1.solve(formula, bounds);
            return fallbackSolution;
        }

        // Step 3: Slice formula
        SliceResult slice = FormulaSlicer.slice(formula, partialRelations, remainderRelations);
        Formula partialFormula = slice.getPartialFormula();


        // Relation Pulling Mechanism: Ensure Rp has at least one constraint
        if (slice.getPartialConjuncts().isEmpty() && !slice.getRemainderConjuncts().isEmpty()) {

            // 1. Gather candidates (fv of each remainder conjunct)
            java.util.List<java.util.Set<Relation>> candidates = new java.util.ArrayList<>();
            for (Formula f : slice.getRemainderConjuncts()) {
                java.util.Set<Relation> fv = org.tzi.kodkod.decomp.FormulaSlicer.collectRelations(f);
                if (!fv.isEmpty()) {
                    candidates.add(fv);
                }
            }

            // 2. Rule 1: Filter arity >= 3
            java.util.List<java.util.Set<Relation>> filtered = new java.util.ArrayList<>();
            for (java.util.Set<Relation> c : candidates) {
                boolean hasHighArity = false;
                for (Relation r : c) {
                    if (r.arity() >= 3) {
                        hasHighArity = true;
                        break;
                    }
                }
                if (!hasHighArity) {
                    filtered.add(c);
                }
            }

            if (filtered.isEmpty() && !candidates.isEmpty()) {
                System.out.println(
                        "[PullingMechanism] All candidates have arity >= 3. Reverting filter (Rule 1 fallback).");
                filtered = candidates;
            }

            // 3. Calculate Cost and apply Rule 2: Min Cost
            int minCost = Integer.MAX_VALUE;
            for (java.util.Set<Relation> c : filtered) {
                int cost = 0;
                for (Relation r : c) {
                    if (!partialRelations.contains(r)) {
                        cost++;
                    }
                }
                if (cost > 0 && cost < minCost) {
                    minCost = cost;
                }
            }

            java.util.List<java.util.Set<Relation>> minCostCandidates = new java.util.ArrayList<>();
            for (java.util.Set<Relation> c : filtered) {
                int cost = 0;
                for (Relation r : c) {
                    if (!partialRelations.contains(r)) {
                        cost++;
                    }
                }
                if (cost == minCost) {
                    minCostCandidates.add(c);
                }
            }

            // 4. Rule 3: Min Size
            java.util.Set<Relation> bestCandidate = null;
            int minSize = Integer.MAX_VALUE;
            for (java.util.Set<Relation> c : minCostCandidates) {
                if (c.size() < minSize) {
                    minSize = c.size();
                    bestCandidate = c;
                }
            }

            if (bestCandidate != null) {

                // 5. Pull relations and dependencies
                java.util.Set<Relation> toPull = new java.util.HashSet<>();
                for (Relation r : bestCandidate) {
                    if (!partialRelations.contains(r)) {
                        toPull.add(r);
                        toPull.addAll(depGraph.getTransitiveDependencies(r));
                    }
                }

                for (Relation r : toPull) {
                    if (remainderRelations.remove(r)) {
                        partialRelations.add(r);
                    }
                }

                // Re-slice with updated Rp
                slice = FormulaSlicer.slice(formula, partialRelations, remainderRelations);
                partialFormula = slice.getPartialFormula();
            }
        }

        // Step 4: Create partial bounds (only Rp relations)
        Bounds partialBounds = createPartialBounds(bounds, partialRelations);

        // Step 5: Get iterator for all Phase 1 solutions (configurations)
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
                break;
            }

            phase1Solutions++;
           
            // Step 7: Create integrated bounds using partial solution
            Instance partialInstance = phase1Solution.instance();

            Bounds integratedBounds = createIntegratedBounds(
                    partialInstance, bounds, partialRelations, remainderRelations);

            // Step 8: Solve Phase 2 (Integrated Problem)
         
            long startPhase2 = System.currentTimeMillis();
            Solution phase2Solution = solver2.solve(formula, integratedBounds);
            phase2Time = System.currentTimeMillis() - startPhase2;
            System.out.println("[CNF] Clauses (Phase 2): " + phase2Solution.stats().clauses()
                    + "  [" + phase2Solution.outcome() + "]");

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
            System.out.println("Phase 2: UNSAT (backtracking...)");
            if (phase2Solution.outcome() == kodkod.engine.Solution.Outcome.TRIVIALLY_UNSATISFIABLE) {
                System.out.println("  Reason: Bounds conflict with constraints (e.g., cardinality constraints) or formula evaluates to false statically.");
                // Print any obviously invalid bounds in this config
                for (kodkod.ast.Relation r : integratedBounds.relations()) {
                    kodkod.instance.TupleSet lb = integratedBounds.lowerBound(r);
                    kodkod.instance.TupleSet ub = integratedBounds.upperBound(r);
                    if (lb != null && ub != null && lb.size() > ub.size()) {
                        System.out.println("  -> Bounds error on " + r.name() + ": lb size (" + lb.size() + ") > ub size (" + ub.size() + ")");
                    }
                }
            }
            if (phase2Solution.proof() != null) {
                System.out.println("  [Proof Core]: " + phase2Solution.proof().highLevelCore());
            } else if (phase2Solution.outcome() != kodkod.engine.Solution.Outcome.TRIVIALLY_UNSATISFIABLE) {
                System.out.println("  [Proof]: null (Translation logging might be disabled in options)");
            }
            System.out.println();
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

        if (symbolicManager != null) {
            Set<Relation> modelRels = symbolicManager.getAllModelRelations();

            for (Relation r : modelRels) {
                kodkod.instance.TupleSet upper = bounds.upperBound(r);
                if (upper != null && !upper.isEmpty()) {
                    graph.addRelation(r);
                }
            }

            Map<Relation, Set<Relation>> allDeps = symbolicManager.getAllDependencies();
            for (Map.Entry<Relation, Set<Relation>> entry : allDeps.entrySet()) {
                Relation target = entry.getKey();
                if (!modelRels.contains(target))
                    continue;
                for (Relation dependency : entry.getValue()) {
                    if (modelRels.contains(dependency)) {
                        graph.addDependency(target, dependency);
                        System.out.println("  " + target.name() + " -> " + dependency.name());
                    }
                    // Bo qua phu thuoc den type relations (String, Boolean, Undefined...)
                }
            }

            Relation fakeIndexRel = Relation.unary("Index");
            boolean indexUsed = false;
            for (Relation r : modelRels) {
                kodkod.instance.TupleSet ub = bounds.upperBound(r);
                if (ub != null) {
                    boolean usesIndex = false;
                    java.util.Iterator<kodkod.instance.Tuple> it = ub.iterator();
                    while (it.hasNext() && !usesIndex) {
                        kodkod.instance.Tuple t = it.next();
                        for (int i = 0; i < t.arity(); i++) {
                            Object atom = t.atom(i);
                            // Kiem tra gia tri so nguyen (dai dien cho index cua Sequence)
                            if (atom instanceof Integer
                                    || (atom instanceof String && ((String) atom).matches("-?\\d+"))) {
                                usesIndex = true;
                                break;
                            }
                        }
                    }
                    if (usesIndex && r.arity() >= 3) {
                        indexUsed = true;
                        graph.addDependency(r, fakeIndexRel);
                    }
                }
            }
            if (indexUsed) {
                graph.addRelation(fakeIndexRel);
            }
        } else {
            // Fallback khi khong co SymbolicBoundsManager:
            // Them tat ca relations tu bounds (fallback an toan)
            for (Relation r : bounds.relations()) {
                graph.addRelation(r);
            }
        }

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

        // Copy constant relations (type literals: String_Reading, Boolean_True,
        // ToStringMap...)
        // Phai loai tru TAT CA model relations (ca Rp lan Rr), khong chi Rp,
        // de tranh Person_hobbies, Person_name... bi copy vao Phase 1 voi empty bounds.
        Set<Relation> allModelRels = symbolicManager != null
                ? symbolicManager.getAllModelRelations()
                : partialRelations;
        copyConstantRelationBounds(original, partial, allModelRels);

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
        int fixedCount = 0, missingCount = 0;
        for (Relation r : partialRelations) {
            if (partialInstance.contains(r)) {
                integrated.boundExactly(r, partialInstance.tuples(r));
                fixedCount++;
            } else {
                // Relation is in Rp but NOT in Phase 1 instance!
                // Must still bound it, otherwise Phase 2 has no bounds for this relation.
                // Fall back to original bounds.
                if (original.lowerBound(r) != null && original.upperBound(r) != null) {
                    integrated.bound(r, original.lowerBound(r), original.upperBound(r));
                    System.out.println(
                            "[IntegratedBounds] WARN: Rp relation not in instance, using original bounds: " + r.name());
                    missingCount++;
                }
            }
        }
        if (missingCount > 0) {
            System.out.println(
                    "[IntegratedBounds] Fixed: " + fixedCount + ", Missing (fallback to original): " + missingCount);
        }

        // Add remainder relations with original bounds
        for (Relation r : remainderRelations) {
            if (original.lowerBound(r) != null && original.upperBound(r) != null) {
                integrated.bound(r, original.lowerBound(r), original.upperBound(r));
            }
        }

        // Copy tat ca constant relations (type literals: String_Reading, Boolean_True,
        // ToStringMap...)
        // Ca hai pha deu can chung vi formula tham chieu chung
        Set<Relation> allModelRels = new java.util.HashSet<>(partialRelations);
        allModelRels.addAll(remainderRelations);
        copyConstantRelationBounds(original, integrated, allModelRels);

        // Copy integer bounds
        copyIntBounds(original, integrated);

        return integrated;
    }

    /**
     * Copy tat ca relations trong original ma KHONG phai model relation (Rp hoac
     * Rr)
     * sang target bounds dang boundExactly.
     *
     * Day la cac type literal singletons (String_Reading, Boolean_True,
     * ToStringMap...)
     * duoc Kodkod tham chieu trong formula nhung khong can giai (gia tri da co
     * dinh).
     *
     * @param original       Bounds goc
     * @param target         Bounds nhan
     * @param modelRelations Tap hop cac model relations (se bi loai khoi viec copy
     *                       nay)
     */
    private void copyConstantRelationBounds(Bounds original, Bounds target, Set<Relation> modelRelations) {
        for (Relation r : original.relations()) {
            if (!modelRelations.contains(r)) {
                // Day la constant relation - copy dang boundExactly (lower == upper)
                kodkod.instance.TupleSet lo = original.lowerBound(r);
                if (lo != null) {
                    target.boundExactly(r, lo);
                }
            }
        }
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
