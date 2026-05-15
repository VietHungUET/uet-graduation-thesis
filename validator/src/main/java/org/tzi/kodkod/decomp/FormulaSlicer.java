/*
 * Custom Decomposed Solving for USE Validator
 * Inspired by Pardinus DecompFormulaSlicer
 */
package org.tzi.kodkod.decomp;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import kodkod.ast.BinaryExpression;
import kodkod.ast.BinaryFormula;
import kodkod.ast.BinaryIntExpression;
import kodkod.ast.ComparisonFormula;
import kodkod.ast.Comprehension;
import kodkod.ast.ConstantExpression;
import kodkod.ast.ConstantFormula;
import kodkod.ast.Decl;
import kodkod.ast.Decls;
import kodkod.ast.ExprToIntCast;
import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IfExpression;
import kodkod.ast.IfIntExpression;
import kodkod.ast.IntComparisonFormula;
import kodkod.ast.IntConstant;
import kodkod.ast.IntExpression;
import kodkod.ast.IntToExprCast;
import kodkod.ast.MultiplicityFormula;
import kodkod.ast.NaryExpression;
import kodkod.ast.NaryFormula;
import kodkod.ast.NaryIntExpression;
import kodkod.ast.Node;
import kodkod.ast.NotFormula;
import kodkod.ast.ProjectExpression;
import kodkod.ast.QuantifiedFormula;
import kodkod.ast.Relation;
import kodkod.ast.RelationPredicate;
import kodkod.ast.SumExpression;
import kodkod.ast.UnaryExpression;
import kodkod.ast.UnaryIntExpression;
import kodkod.ast.Variable;
import kodkod.ast.visitor.AbstractVoidVisitor;

/**
 * Slices a first-order logic formula into two conjuncts based on a set of
 * "partial" relations.
 * 
 * <p>
 * Given a formula F and a set of partial relations Rp, this slicer divides F
 * into:
 * <ul>
 * <li><b>F1 (partial formula)</b>: Conjuncts that ONLY reference relations in
 * Rp</li>
 * <li><b>F2 (remainder formula)</b>: Conjuncts that reference at least one
 * relation NOT in Rp</li>
 * </ul>
 * 
 * <p>
 * The slicing is based on analyzing which relations each conjunct references.
 * A conjunct goes to F1 only if ALL its relations are in the partial set.
 * 
 * <p>
 * This is inspired by Pardinus's DecompFormulaSlicer but adapted for the USE
 * validator. Uses a custom RelationCollector since Kodkod's internal classes
 * are not accessible.
 * 
 * @author Custom implementation for thesis
 */
public class FormulaSlicer {

    /**
     * Result of formula slicing containing both partial and remainder formulas.
     */
    public static class SliceResult {
        private final Formula partialFormula;
        private final Formula remainderFormula;
        private final List<Formula> partialConjuncts;
        private final List<Formula> remainderConjuncts;

        public SliceResult(Formula partialFormula, Formula remainderFormula, 
                           List<Formula> partialConjuncts, List<Formula> remainderConjuncts) {
            this.partialFormula = partialFormula;
            this.remainderFormula = remainderFormula;
            this.partialConjuncts = partialConjuncts;
            this.remainderConjuncts = remainderConjuncts;
        }

        public Formula getPartialFormula() {
            return partialFormula;
        }

        public Formula getRemainderFormula() {
            return remainderFormula;
        }

        public List<Formula> getPartialConjuncts() {
            return partialConjuncts;
        }

        public List<Formula> getRemainderConjuncts() {
            return remainderConjuncts;
        }

        /**
         * @return Entry with partial formula as key, remainder as value (for
         *         compatibility)
         */
        public Entry<Formula, Formula> asEntry() {
            return new SimpleEntry<>(partialFormula, remainderFormula);
        }
    }

    /**
     * Slices the given formula based on the partial relations set.
     * 
     * @param formula            The formula to slice (typically model.constraints())
     * @param partialRelations   Set of relations belonging to the partial problem
     * @param remainderRelations Set of relations belonging to the remainder problem
     * @return SliceResult containing both partial and remainder formulas
     */
    public static SliceResult slice(Formula formula, Set<Relation> partialRelations, Set<Relation> remainderRelations) {
        if (formula == null) {
            throw new IllegalArgumentException("Formula cannot be null");
        }
        if (partialRelations == null || partialRelations.isEmpty()) {
            // If no partial relations, everything goes to remainder
            return new SliceResult(Formula.TRUE, formula, java.util.Collections.emptyList(), FormulaFlattener.flatten(formula));
        }

        // Step 1: Flatten to NNF and break nested ANDs into a flat conjunct list.
        // This is the key step: without flattening, deeply-nested AND trees appear as
        // a single conjunct and cannot be sliced. FormulaFlattener converts:
        // (A ∧ B) ∧ (C ∧ D) → [A, B, C, D]
        // so each clause can be individually tested for Rp-containment.
        List<Formula> flatConjuncts = FormulaFlattener.flatten(formula);

        List<Formula> f1 = new ArrayList<>(); // partial conjuncts (only Rp relations)
        List<Formula> f2 = new ArrayList<>(); // remainder conjuncts (use at least one Rr)

        // Step 2: classify each flat conjunct
        for (Formula f : flatConjuncts) {
            Set<Relation> rs = collectRelations(f);
            boolean usesRemainder = false;
            for (Relation r : rs) {
                if (remainderRelations != null && remainderRelations.contains(r)) {
                    usesRemainder = true;
                    break;
                }
            }
            if (!usesRemainder) {
                f1.add(f);
            } else {
                f2.add(f);
            }
        }

        System.out.println("[FormulaSlicer] Flat conjuncts: " + flatConjuncts.size()
                + "  -> f1 (Rp-only): " + f1.size()
                + ",  f2 (uses Rr): " + f2.size());

        return new SliceResult(Formula.and(f1), Formula.and(f2), f1, f2);
    }

    /**
     * Convenience method that returns Entry for compatibility with Pardinus-style
     * code.
     */
    public static Entry<Formula, Formula> sliceAsEntry(Formula formula, Set<Relation> partialRelations, Set<Relation> remainderRelations) {
        return slice(formula, partialRelations, remainderRelations).asEntry();
    }

    /**
     * Collects all relations referenced by a node (Formula or Expression).
     * 
     * @param node The AST node to analyze
     * @return Set of all relations referenced
     */
    public static Set<Relation> collectRelations(Node node) {
        RelationCollectorVisitor collector = new RelationCollectorVisitor();
        node.accept(collector);
        return collector.getRelations();
    }

    /**
     * Custom visitor that collects all Relation nodes from an AST.
     * Implements a complete visitor pattern for Kodkod AST.
     */
    private static class RelationCollectorVisitor extends AbstractVoidVisitor {
        private final Set<Relation> relations = new HashSet<>();
        private final Set<Node> visited = new HashSet<>();

        public Set<Relation> getRelations() {
            return relations;
        }

        @Override
        protected boolean visited(Node n) {
            return !visited.add(n);
        }

        // === Relations ===
        @Override
        public void visit(Relation relation) {
            relations.add(relation);
        }

        // === Other expression types - just traverse ===
        @Override
        public void visit(Variable variable) {
        }

        @Override
        public void visit(ConstantExpression constExpr) {
        }

        @Override
        public void visit(UnaryExpression unaryExpr) {
            unaryExpr.expression().accept(this);
        }

        @Override
        public void visit(BinaryExpression binExpr) {
            binExpr.left().accept(this);
            binExpr.right().accept(this);
        }

        @Override
        public void visit(NaryExpression naryExpr) {
            for (int i = 0; i < naryExpr.size(); i++) {
                naryExpr.child(i).accept(this);
            }
        }

        @Override
        public void visit(Comprehension comprehension) {
            comprehension.decls().accept(this);
            comprehension.formula().accept(this);
        }

        @Override
        public void visit(IfExpression ifExpr) {
            ifExpr.condition().accept(this);
            ifExpr.thenExpr().accept(this);
            ifExpr.elseExpr().accept(this);
        }

        @Override
        public void visit(ProjectExpression project) {
            project.expression().accept(this);
        }

        @Override
        public void visit(IntToExprCast castExpr) {
            castExpr.intExpr().accept(this);
        }

        // === Declarations ===
        @Override
        public void visit(Decls decls) {
            for (Decl d : decls) {
                d.accept(this);
            }
        }

        @Override
        public void visit(Decl decl) {
            decl.expression().accept(this);
        }

        // === Formulas ===
        @Override
        public void visit(QuantifiedFormula quantFormula) {
            quantFormula.decls().accept(this);
            quantFormula.formula().accept(this);
        }

        @Override
        public void visit(NaryFormula naryFormula) {
            for (Formula f : naryFormula) {
                f.accept(this);
            }
        }

        @Override
        public void visit(BinaryFormula binFormula) {
            binFormula.left().accept(this);
            binFormula.right().accept(this);
        }

        @Override
        public void visit(NotFormula not) {
            not.formula().accept(this);
        }

        @Override
        public void visit(ConstantFormula constant) {
        }

        @Override
        public void visit(ComparisonFormula compFormula) {
            compFormula.left().accept(this);
            compFormula.right().accept(this);
        }

        @Override
        public void visit(MultiplicityFormula multFormula) {
            multFormula.expression().accept(this);
        }

        @Override
        public void visit(RelationPredicate predicate) {
            predicate.relation().accept(this);
        }

        // === Int expressions ===
        @Override
        public void visit(IntConstant intConst) {
        }

        @Override
        public void visit(ExprToIntCast exprToInt) {
            exprToInt.expression().accept(this);
        }

        @Override
        public void visit(IfIntExpression ifIntExpr) {
            ifIntExpr.condition().accept(this);
            ifIntExpr.thenExpr().accept(this);
            ifIntExpr.elseExpr().accept(this);
        }

        @Override
        public void visit(NaryIntExpression naryIntExpr) {
            for (int i = 0; i < naryIntExpr.size(); i++) {
                naryIntExpr.child(i).accept(this);
            }
        }

        @Override
        public void visit(BinaryIntExpression binIntExpr) {
            binIntExpr.left().accept(this);
            binIntExpr.right().accept(this);
        }

        @Override
        public void visit(UnaryIntExpression unaryIntExpr) {
            unaryIntExpr.intExpr().accept(this);
        }

        @Override
        public void visit(SumExpression sumExpr) {
            sumExpr.decls().accept(this);
            sumExpr.intExpr().accept(this);
        }

        @Override
        public void visit(IntComparisonFormula intCompFormula) {
            intCompFormula.left().accept(this);
            intCompFormula.right().accept(this);
        }
    }
}
