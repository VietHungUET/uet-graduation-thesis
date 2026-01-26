package org.tzi.kodkod.ocl.operation;

import java.util.ArrayList;
import java.util.List;

import kodkod.ast.BinaryExpression;
import kodkod.ast.Decl;
import kodkod.ast.Decls;
import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntConstant;
import kodkod.ast.Variable;

import org.jruby.RubyProcess.Sys;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLOperationGroup;

/**
 * Transformation methods for sequence operations.
 * 
 * <p>
 * Sequence is encoded as ternary relation (object, index, value) where:
 * - object: the object that owns the sequence attribute
 * - index: the position in the sequence (1-based)
 * - value: the element at that position
 * </p>
 * 
 * @author SME Lab
 */
public class SequenceOperationGroup extends OCLOperationGroup {

    private List<String> operationsReturningSequence;

    public SequenceOperationGroup(TypeFactory typeFactory) {
        super(typeFactory);

        operationsReturningSequence = new ArrayList<String>();
        operationsReturningSequence.add("asSequence");
        operationsReturningSequence.add("append");
        operationsReturningSequence.add("prepend");
        operationsReturningSequence.add("subSequence");
        operationsReturningSequence.add("union");
        operationsReturningSequence.add("intersection");
        operationsReturningSequence.add("excluding");
        operationsReturningSequence.add("including");
        operationsReturningSequence.add("flatten");
        operationsReturningSequence.add("collect");
        operationsReturningSequence.add("select");
        operationsReturningSequence.add("reject");
        operationsReturningSequence.add("first");
    }

    @Override
    public boolean isSequenceOperationGroup() {
        return true;
    }

    @Override
    public boolean returnsSequence(String opName) {
        return operationsReturningSequence.contains(opName);
    }

    // OCL: srcExpr->asSequence()
    public final Expression asSequence(Expression src) {
        return src;
    }

    // OCL: srcExpr->size()
    public final Expression size(Expression src) {

        try {
            // Sequence has arity 2, so we need undefined with arity 2
            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);
            // For size(), we need to return an integer, so use undefined for result
            Expression result = src.eq(undefined_Set_2).thenElse(undefined, src.count().toExpression());
            return result;
        } catch (Exception e) {
            System.out.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->isEmpty()
    public final Formula isEmpty(Expression src) {
        Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);
        return src.eq(undefined_Set_2).or(src.no());
    }

    // OCL: srcExpr->notEmpty()
    public final Formula notEmpty(Expression src) {
        Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);
        return src.eq(undefined_Set_2).not().and(src.some());
    }

    // OCL: srcExpr->min()
    public final Expression min(Expression src) {
        try {
            System.out.println("\n### SequenceOperationGroup.min() DEBUG ###");
            System.out.println("src: " + src);
            System.out.println("src arity: " + src.arity());

            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

            // collect() trả về set (arity 1)
            // Nếu src arity 1 thì đó là set các giá trị
            // Nếu src arity 2 thì đó là sequence (index, value)
            Expression values;
            if (src.arity() == 1) {
                // src là set (kết quả từ collect)
                values = src;
                System.out.println("src is a set (arity 1), using directly as values");
            } else if (src.arity() == 2) {
                // src là sequence (index, value), extract values
                values = Expression.UNIV.join(src);
                System.out.println("src is a sequence (arity 2), extracting values with UNIV.join(src)");
            } else {
                throw new RuntimeException("Unexpected src arity in min(): " + src.arity());
            }

            System.out.println("values: " + values);
            System.out.println("values arity: " + values.arity());

            // Tạo biến cho giá trị cần tìm min
            Variable v = Variable.unary("v");
            Variable w = Variable.unary("w");

            // Điều kiện: v <= w với mọi w thuộc values
            Formula minCondition = v.sum().lte(w.sum()).forAll(w.oneOf(values));
            Expression minValue = minCondition.comprehension(v.oneOf(values));

            System.out.println("minCondition: " + minCondition);
            System.out.println("minValue: " + minValue);
            System.out.println("minValue arity: " + minValue.arity());
            System.out.println("##############################################\n");

            // Trả về undefined nếu src là undefined hoặc rỗng
            return src.eq(undefined_Set).or(values.no())
                    .thenElse(undefined, minValue);
        } catch (Exception e) {
            System.out.println("❌ ERROR in min(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->max()
    public final Expression max(Expression src) {
        try {
            System.out.println("\n### SequenceOperationGroup.max() DEBUG ###");
            System.out.println("src: " + src);
            System.out.println("src arity: " + src.arity());

            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

            // collect() trả về set (arity 1)
            // Nếu src arity 1 thì đó là set các giá trị
            // Nếu src arity 2 thì đó là sequence (index, value)
            Expression values;
            if (src.arity() == 1) {
                // src là set (kết quả từ collect)
                values = src;
                System.out.println("src is a set (arity 1), using directly as values");
            } else if (src.arity() == 2) {
                // src là sequence (index, value), extract values
                values = Expression.UNIV.join(src);
                System.out.println("src is a sequence (arity 2), extracting values with UNIV.join(src)");
            } else {
                throw new RuntimeException("Unexpected src arity in max(): " + src.arity());
            }

            System.out.println("values: " + values);
            System.out.println("values arity: " + values.arity());

            // Tạo biến cho giá trị cần tìm max
            Variable v = Variable.unary("v");
            Variable w = Variable.unary("w");

            // Điều kiện: v >= w với mọi w thuộc values (khác với min là <=)
            Formula maxCondition = v.sum().gte(w.sum()).forAll(w.oneOf(values));
            Expression maxValue = maxCondition.comprehension(v.oneOf(values));

            System.out.println("maxCondition: " + maxCondition);
            System.out.println("maxValue: " + maxValue);
            System.out.println("maxValue arity: " + maxValue.arity());
            System.out.println("##############################################\n");

            // Trả về undefined nếu src là undefined hoặc rỗng
            return src.eq(undefined_Set).or(values.no())
                    .thenElse(undefined, maxValue);
        } catch (Exception e) {
            System.out.println("❌ ERROR in max(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->first()
    public final Expression first(Expression src) {
        try {
            System.out.println("\n### SequenceOperationGroup.first() DEBUG ###");
            System.out.println("src: " + src);
            System.out.println("src arity: " + src.arity());

            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

            // src is binary (index, value), we want value where index = 0
            Expression idx0 = IntConstant.constant(0).toExpression();

            // Filter: tuples with index = 0
            // For binary (index, value), filter is idx0.product(UNIV)
            Expression filterExpr = idx0.product(Expression.UNIV);
            System.out.println("filterExpr arity: " + filterExpr.arity());

            Expression firstTuples = src.intersection(filterExpr);
            System.out.println("firstTuples: " + firstTuples);
            System.out.println("firstTuples arity: " + firstTuples.arity());

            // Project to value column: join from right with UNIV to get value
            Expression firstValue = Expression.UNIV.join(firstTuples);
            System.out.println("firstValue: " + firstValue);
            System.out.println("firstValue arity: " + firstValue.arity());
            System.out.println("##############################################\n");

            return src.eq(undefined_Set_2).or(src.no())
                    .thenElse(undefined, firstValue);
        } catch (Exception e) {
            System.out.println("❌ ERROR in first(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->last()
    public final Expression last(Expression src) {
        try {
            System.out.println("\n### SequenceOperationGroup.last() DEBUG ###");
            System.out.println("src: " + src);
            System.out.println("src arity: " + src.arity());

            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

            Variable idx = Variable.unary("idx");
            Variable maxIdx = Variable.unary("maxIdx");

            // Get all indices from src
            // src is binary (index, value), project to index column by joining from right
            Expression indices = src.join(Expression.UNIV); // join phải -> lấy cột index
            System.out.println("indices arity: " + indices.arity());

            // maxIdx is the index where all other indices are <= it
            // Similar to max() pattern in SetOperationGroup
            Expression maxIndex = maxIdx.sum().gte(idx.sum())
                    .forAll(idx.oneOf(indices))
                    .comprehension(maxIdx.oneOf(indices));
            System.out.println("maxIndex arity: " + maxIndex.arity());

            // Get value at maxIndex
            // For binary (index, value), filter is maxIndex.product(UNIV)
            Expression filterExpr = maxIndex.product(Expression.UNIV);
            System.out.println("filterExpr arity: " + filterExpr.arity());

            Expression lastTuples = src.intersection(filterExpr);
            System.out.println("lastTuples arity: " + lastTuples.arity());

            // Project to value column: join from LEFT with UNIV to get value (right column)
            // lastTuples is (index, value), UNIV.join(lastTuples) gives value
            Expression lastValue = Expression.UNIV.join(lastTuples);
            System.out.println("lastValue arity: " + lastValue.arity());
            System.out.println("##############################################\n");

            return src.eq(undefined_Set_2).or(src.no())
                    .thenElse(undefined, lastValue);
        } catch (Exception e) {
            System.out.println("❌ ERROR in last(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->at(index)
    public final Expression at(Expression src, Expression index) {
        Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

        // src is binary (index, value)
        // Filter tuples where index = index parameter
        // For binary (index, value), filter is index.product(UNIV)
        Expression tuplesAtIndex = src.intersection(
                index.product(Expression.UNIV));

        // Project to value column: join from right with UNIV to get value
        Expression value = tuplesAtIndex.join(Expression.UNIV);

        return src.eq(undefined_Set_2).or(index.eq(undefined)).or(tuplesAtIndex.no())
                .thenElse(undefined, value);
    }

    // OCL: srcExpr->indexOf(elem)
    public final Expression indexOf(Expression src, Expression elem) {
        // Simplified implementation - return undefined for now
        return src.eq(undefined_Set).or(elem.eq(undefined)).thenElse(undefined, undefined);
    }

    // OCL: srcExpr->append(elem)
    public final Expression append(Expression src, Expression elem) {
        // Simplified implementation - just add the element
        Expression newTuple = Expression.UNIV.product(Expression.UNIV).product(elem);
        return src.eq(undefined_Set).or(elem.eq(undefined)).thenElse(undefined_Set,
                src.union(newTuple));
    }

    // OCL: srcExpr->prepend(elem)
    public final Expression prepend(Expression src, Expression elem) {
        // Simplified implementation - just add the element
        Expression newTuple = Expression.UNIV.product(Expression.UNIV).product(elem);
        return src.eq(undefined_Set).or(elem.eq(undefined)).thenElse(undefined_Set,
                src.union(newTuple));
    }

    // OCL: srcExpr->subSequence(lower, upper)
    public final Expression subSequence(Expression src, Expression lower, Expression upper) {
        // Simplified implementation - return the original sequence
        return src.eq(undefined_Set).or(lower.eq(undefined)).or(upper.eq(undefined))
                .thenElse(undefined_Set, src);
    }

    // OCL: srcExpr->union(seq)
    public final Expression union(Expression src, Expression seq) {
        // For sequences, union means concatenation
        return src.eq(undefined_Set).or(seq.eq(undefined_Set))
                .thenElse(undefined_Set, src.union(seq));
    }

    // OCL: srcExpr->intersection(seq)
    public final Expression intersection(Expression src, Expression seq) {
        // For sequences, intersection means common elements at same positions
        return src.eq(undefined_Set).or(seq.eq(undefined_Set))
                .thenElse(undefined_Set, src.intersection(seq));
    }

    // OCL: srcExpr->excluding(elem)
    public final Expression excluding(Expression src, Expression elem) {
        return src.eq(undefined_Set).thenElse(undefined_Set,
                src.difference(Expression.UNIV.product(Expression.UNIV).product(elem)));
    }

    // OCL: srcExpr->including(elem)
    public final Expression including(Expression src, Expression elem) {
        return append(src, elem);
    }

    // OCL: srcExpr->flatten()
    public final Expression flatten(Expression src) {
        // For sequences of sequences, flatten removes one level of nesting
        // This is a simplified implementation
        return src;
    }

    // OCL: srcExpr->collect(var | bodyExpr)
    public final Expression collect(Expression src, Expression body, Variable var) {
        try {
            System.out.println("\n### SequenceOperationGroup.collect() DEBUG ###");
            System.out.println("src: " + src);
            System.out.println("src arity: " + src.arity());
            System.out.println("body: " + body);
            System.out.println("body arity: " + body.arity());
            System.out.println("var: " + var);
            System.out.println("var arity: " + var.arity());
            System.out.println("var name: " + var.name());

            Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

            // Bước 1: Project src để lấy values (unary)
            // src có dạng (index, value) với arity 2
            // var có dạng (index, value) với arity 2
            // UNIV.join(src) sẽ lấy cột value (arity 1)
            // values là tập hợp các giá trị trong sequence
            Expression values = Expression.UNIV.join(src);
            System.out.println("values (UNIV.join(src)): " + values);
            System.out.println("values arity: " + values.arity());

            // Bước 2: Extract attribute từ body và rebuild với val
            Expression attribute = null;

            if (body instanceof BinaryExpression) {
                BinaryExpression binBody = (BinaryExpression) body;
                System.out.println("body.left(): " + binBody.left());
                System.out.println("body.right(): " + binBody.right());
                System.out.println("body.left().equals(var): " + binBody.left().equals(var));

                if (binBody.left().equals(var)) {
                    // body = var.join(attribute)
                    attribute = binBody.right();
                    System.out.println("Extracted attribute: " + attribute);
                    System.out.println("attribute arity: " + attribute.arity());
                } else {
                    throw new RuntimeException("Unexpected body structure: left is not var. body=" + body);
                }
            } else {
                throw new RuntimeException("Body is not a join expression: " + body.getClass().getName());
            }

            // Bước 3: Tạo biến val và res (unary)
            Variable val = Variable.unary("val");
            Variable res = Variable.unary("res");
            System.out.println("val (new unary variable): " + val);
            System.out.println("res (new unary variable): " + res);

            // Bước 4: Rebuild body với val (unary)
            Expression bodyWithVal = val.join(attribute); // arity 1 (vì val là unary)
            System.out.println("bodyWithVal (val.join(attribute)): " + bodyWithVal);
            System.out.println("bodyWithVal arity: " + bodyWithVal.arity());

            // Bước 5: Tạo Decls cho val và res
            Decls decls = val.oneOf(values).and(res.oneOf(bodyWithVal));
            System.out.println("decls: " + decls);

            // Bước 6: Dùng comprehension với Decls
            // Kết quả comprehension là tập hợp các res ứng với mỗi val
            Expression comprehension = Formula.TRUE.comprehension(decls);
            System.out.println("comprehension: " + comprehension);
            System.out.println("comprehension arity: " + comprehension.arity());

            Expression functionApplication = Expression.UNIV.join(comprehension);
            System.out.println("functionApplication (UNIV.join(comprehension)): " + functionApplication);
            System.out.println("functionApplication arity: " + functionApplication.arity());

            // Bước 7: Flatten kết quả
            Expression flattenedResult = functionApplication.difference(undefined_Set).count()
                    .lt(functionApplication.count())
                    .thenElse(functionApplication.difference(undefined_Set).union(undefined), functionApplication);

            System.out.println("flattenedResult: " + flattenedResult);
            System.out.println("flattenedResult arity: " + flattenedResult.arity());
            System.out.println("##############################################\n");

            return src.eq(undefined_Set_2).thenElse(undefined_Set, flattenedResult);
        } catch (Exception e) {
            System.out.println("❌ ERROR in collect(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // OCL: srcExpr->select(var | bodyExpr)
    public final Expression select(Expression src, Formula body, Variable var) {
        // Simplified implementation - return the original sequence
        return src.eq(undefined_Set).thenElse(undefined_Set, src);
    }

    // OCL: srcExpr->reject(var | bodyExpr)
    public final Expression reject(Expression src, Formula body, Variable var) {
        return select(src, body.not(), var);
    }

    // OCL: srcExpr->exists(var | bodyExpr)
    public final Formula exists(Expression src, Formula body, Variable var) {
        // Simplified implementation - return false for now
        return src.eq(undefined_Set).not().and(Formula.FALSE);
    }

    // OCL: srcExpr->forAll(var | bodyExpr)
    public final Formula forAll(Expression src, Formula body, Variable var) {
        // Simplified implementation - return true for now
        return src.eq(undefined_Set).or(Formula.TRUE);
    }

    // OCL: srcExpr->one(var | bodyExpr)
    public final Formula one(Expression src, Formula body, Variable var) {
        // Simplified implementation - return false for now
        return src.eq(undefined_Set).or(Formula.FALSE);
    }

    // OCL: srcExpr->any(var | bodyExpr)
    public final Expression any(Expression src, Formula body, Variable var) {
        final Expression select = select(src, body, var);
        return src.eq(undefined_Set).or(select.no()).thenElse(undefined, select);
    }

    // OCL: srcExpr->count(elem)
    public final Expression count(Expression src, Expression elem) {
        // Simplified implementation - return 0 for now
        return src.eq(undefined_Set).thenElse(undefined, IntConstant.constant(0).toExpression());
    }

    // OCL: srcExpr = seqExpr
    public final Formula equality(Expression src, Expression seq) {
        return src.eq(seq);
    }

    // OCL: srcExpr->includes(elem)
    public final Formula includes(Expression src, Expression elem) {

        Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);
        Expression values = Expression.UNIV.join(src);
        System.out.println("values: " + values);

        Expression intersection = elem.intersection(values);

        return src.eq(undefined_Set_2).not().and(
                intersection.some());
    }

    // OCL: srcExpr->excludes(elem)
    public final Formula excludes(Expression src, Expression elem) {
        Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);

        // UNIV.join(src) → chiếu ra cột value (arity 1)
        // Với src là (index, value), join từ bên trái lấy cột value
        Expression values = Expression.UNIV.join(src);
        return src.eq(undefined_Set_2).not().and(
                elem.intersection(values).no() // Kiểm tra giao rỗng
        );
    }

    public final Formula isUndefined(Expression src) {
        return src.eq(undefined_Set);
    }

    public Formula isUndefined(Formula src) {
        return Formula.FALSE;
    }
}