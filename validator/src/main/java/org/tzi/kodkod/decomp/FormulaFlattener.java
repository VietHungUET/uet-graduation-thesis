/*
 * Custom implementation for USE Validator thesis.
 * Inspired by Pardinus kodkod.engine.fol2sat.FormulaFlattener
 * but reimplemented independently without importing Pardinus classes.
 */
package org.tzi.kodkod.decomp;

import java.util.ArrayList;
import java.util.List;

import kodkod.ast.BinaryFormula;
import kodkod.ast.BinaryIntExpression;
import kodkod.ast.ComparisonFormula;
import kodkod.ast.ConstantFormula;
import kodkod.ast.Decl;
import kodkod.ast.Decls;
import kodkod.ast.ExprToIntCast;
import kodkod.ast.Formula;
import kodkod.ast.IfIntExpression;
import kodkod.ast.IntComparisonFormula;
import kodkod.ast.IntConstant;
import kodkod.ast.IntToExprCast;
import kodkod.ast.MultiplicityFormula;
import kodkod.ast.NaryFormula;
import kodkod.ast.NaryIntExpression;
import kodkod.ast.Node;
import kodkod.ast.NotFormula;
import kodkod.ast.QuantifiedFormula;
import kodkod.ast.RelationPredicate;
import kodkod.ast.SumExpression;
import kodkod.ast.UnaryIntExpression;
import kodkod.ast.operator.FormulaOperator;
import kodkod.ast.visitor.AbstractVoidVisitor;

/**
 * Flattens a FOL formula into a list of conjuncts by:
 * <ol>
 * <li>Converting to Negation Normal Form (NNF) — pushing negations inward</li>
 * <li>Flattening nested AND structures into a flat list of conjuncts</li>
 * </ol>
 *
 * <p>
 * This is a prerequisite for effective formula slicing in decomposed solving.
 * Without flattening, deeply nested AND trees appear as a single conjunct and
 * cannot be separated by the slicer.
 *
 * <p>
 * Inspired by Pardinus {@code kodkod.engine.fol2sat.FormulaFlattener}.
 *
 * @author Custom implementation for thesis
 */
public class FormulaFlattener extends AbstractVoidVisitor {

    /** Accumulated flat list of conjuncts after flattening */
    private final List<Formula> conjuncts = new ArrayList<>();

    /** Whether the current traversal is under a negation */
    private boolean negated = false;

    /**
     * Flattens the given formula into a list of conjuncts (NNF + AND-flattening).
     *
     * @param formula The formula to flatten
     * @return Flat list of conjuncts; their conjunction is equivalent to the
     *         original formula
     */
    public static List<Formula> flatten(Formula formula) {
        FormulaFlattener flattener = new FormulaFlattener();
        formula.accept(flattener);
        return flattener.conjuncts;
    }

    // -------------------------------------------------------------------------
    // Visitor infrastructure
    // -------------------------------------------------------------------------

    /**
     * No shared-node deduplication here (simpler than Pardinus version).
     * Returns false so every node is visited.
     */
    @Override
    protected boolean visited(Node n) {
        return false;
    }

    /**
     * Adds a conjunct to the result, applying negation if under a NOT scope.
     */
    private void addConjunct(Formula f) {
        conjuncts.add(negated ? f.not() : f);
    }

    // -------------------------------------------------------------------------
    // Formula visitors
    // -------------------------------------------------------------------------

    /**
     * Handles NOT: flip negated flag, recurse, flip back.
     * If the inner formula breaks into multiple conjuncts → all added.
     * If not → add the NOT formula as a single conjunct.
     */
    @Override
    public void visit(NotFormula nf) {
        int sizeBefore = conjuncts.size();
        negated = !negated;
        nf.formula().accept(this);
        negated = !negated;
        // If nothing was added (e.g., inner was a NOT of a NOT that was skipped),
        // add as literal
        if (conjuncts.size() == sizeBefore) {
            addConjunct(nf.formula().not());
        }
    }

    /**
     * Handles AND / OR / IMPLIES / IFF binary formulas.
     * <ul>
     * <li>AND (not negated): recurse into both children (flatten)</li>
     * <li>NOT-AND (negated): cannot flatten further, add as atom</li>
     * <li>IFF: cannot flatten, add as atom</li>
     * <li>IMPLIES (not negated): cannot flatten (OR shape), add as atom</li>
     * <li>NOT-IMPLIES (negated): !(a⇒b) = a∧¬b → recurse with flipped flag for
     * right</li>
     * <li>OR (not negated): cannot flatten conjunctively, add as atom</li>
     * </ul>
     */
    @Override
    public void visit(BinaryFormula bf) {
        final FormulaOperator op = bf.op();

        if (op == FormulaOperator.IFF) {
            // a⟺b is not a conjunction, add as atom
            addConjunct(bf);
        } else if (op == FormulaOperator.AND && !negated) {
            // Flatten: recurse into both children
            bf.left().accept(this);
            bf.right().accept(this);
        } else if (op == FormulaOperator.AND && negated) {
            // !(a∧b) = !a∨!b → OR, not conjunctively splittable
            addConjunct(bf);
        } else if (op == FormulaOperator.IMPLIES && negated) {
            // !(a⇒b) = !(¬a∨b) = a∧¬b → CAN flatten
            negated = false;
            bf.left().accept(this); // a (positive)
            negated = true;
            bf.right().accept(this); // ¬b
            negated = false;
        } else {
            // OR (not negated), IMPLIES (not negated): cannot split conjunctively
            addConjunct(bf);
        }
    }

    /**
     * Handles N-ary AND / OR.
     * AND (not negated): recurse into each child.
     * Everything else: add as atom.
     */
    @Override
    public void visit(NaryFormula nf) {
        final FormulaOperator op = nf.op();
        if (op == FormulaOperator.AND && !negated) {
            for (Formula f : nf) {
                f.accept(this);
            }
        } else {
            addConjunct(nf);
        }
    }

    /**
     * Quantified formulas are leaves — add as atom.
     * (Pardinus optionally breaks universals; we keep it simple here.)
     */
    @Override
    public void visit(QuantifiedFormula qf) {
        addConjunct(qf);
    }

    /** Leaf: comparison formula */
    @Override
    public void visit(ComparisonFormula cf) {
        addConjunct(cf);
    }

    /** Leaf: integer comparison formula */
    @Override
    public void visit(IntComparisonFormula cf) {
        addConjunct(cf);
    }

    /** Leaf: multiplicity formula (one/lone/some/no) */
    @Override
    public void visit(MultiplicityFormula mf) {
        addConjunct(mf);
    }

    /** Leaf: constant TRUE/FALSE */
    @Override
    public void visit(ConstantFormula c) {
        addConjunct(c);
    }

    /** Leaf: relation predicate (function/total-order etc.) */
    @Override
    public void visit(RelationPredicate p) {
        addConjunct(p);
    }

    // -------------------------------------------------------------------------
    // Expression / IntExpression visitors — not formula nodes, just no-ops
    // -------------------------------------------------------------------------

    @Override
    public void visit(kodkod.ast.Relation relation) {
    }

    @Override
    public void visit(kodkod.ast.Variable variable) {
    }

    @Override
    public void visit(kodkod.ast.ConstantExpression constExpr) {
    }

    @Override
    public void visit(kodkod.ast.UnaryExpression unaryExpr) {
        unaryExpr.expression().accept(this);
    }

    @Override
    public void visit(kodkod.ast.BinaryExpression binExpr) {
        binExpr.left().accept(this);
        binExpr.right().accept(this);
    }

    @Override
    public void visit(kodkod.ast.NaryExpression naryExpr) {
        for (int i = 0; i < naryExpr.size(); i++)
            naryExpr.child(i).accept(this);
    }

    @Override
    public void visit(kodkod.ast.Comprehension c) {
        c.decls().accept(this);
        c.formula().accept(this);
    }

    @Override
    public void visit(kodkod.ast.IfExpression i) {
        i.condition().accept(this);
        i.thenExpr().accept(this);
        i.elseExpr().accept(this);
    }

    @Override
    public void visit(kodkod.ast.ProjectExpression p) {
        p.expression().accept(this);
    }

    @Override
    public void visit(IntToExprCast c) {
        c.intExpr().accept(this);
    }

    @Override
    public void visit(Decls decls) {
        for (Decl d : decls)
            d.accept(this);
    }

    @Override
    public void visit(Decl decl) {
        decl.expression().accept(this);
    }

    @Override
    public void visit(IntConstant intConst) {
    }

    @Override
    public void visit(ExprToIntCast c) {
        c.expression().accept(this);
    }

    @Override
    public void visit(IfIntExpression i) {
        i.condition().accept(this);
        i.thenExpr().accept(this);
        i.elseExpr().accept(this);
    }

    @Override
    public void visit(NaryIntExpression n) {
        for (int i = 0; i < n.size(); i++)
            n.child(i).accept(this);
    }

    @Override
    public void visit(BinaryIntExpression b) {
        b.left().accept(this);
        b.right().accept(this);
    }

    @Override
    public void visit(UnaryIntExpression u) {
        u.intExpr().accept(this);
    }

    @Override
    public void visit(SumExpression s) {
        s.decls().accept(this);
        s.intExpr().accept(this);
    }
}
