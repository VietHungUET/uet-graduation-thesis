package org.tzi.kodkod.ocl.operation;

import kodkod.ast.Expression;

import org.tzi.kodkod.helper.ConstraintHelper;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLOperationGroup;

/**
 * Contains transformation methods for variable operations.
 * 
 * @author Hendrik
 */
public class VariableOperationGroup extends OCLOperationGroup {

	private boolean returnsSet = false;
	private boolean returnsSequence = false;

	public VariableOperationGroup(TypeFactory typeFactory) {
		super(typeFactory);
	}

	public final Expression navigation(Expression srcExpr, Expression assoc, Integer from_role, Integer to_role,
			Boolean assoc_class,
			Boolean object_type_end) {
		Expression res = assoc;

		if (from_role < to_role) {
			int i = 1;

			if (assoc_class)
				i = 0;

			for (; i < from_role; i++) {
				res = Expression.UNIV.join(res);
			}

			res = srcExpr.join(res);

			i += 1;
			for (; i < to_role; i++) {
				res = Expression.UNIV.join(res);
			}
			for (i = res.arity(); i > 1; i--) {
				res = res.join(Expression.UNIV);
			}
		} else {
			int i = res.arity();
			if (assoc_class)
				i -= 1;
			for (; i > from_role; i--) {
				res = res.join(Expression.UNIV);
			}

			res = res.join(srcExpr);
			i -= 1;
			for (; i > to_role; i--) {
				res = res.join(Expression.UNIV);
			}
			for (i = res.arity(); i > 1; i--) {
				res = Expression.UNIV.join(res);
			}
		}

		if (object_type_end) {
			returnsSet = false;
			returnsSequence = false; // reset: association navigation is never a Sequence
			return srcExpr.eq(undefined).thenElse(undefined, res);
		} else {
			returnsSet = true;
			returnsSequence = false; // reset: association navigation is never a Sequence
			return srcExpr.eq(undefined).thenElse(undefined_Set, res);
		}
	}

	public final Expression navigationClassifier(Expression src, Expression classifier, Integer toRole,
			Boolean isAssocClass) {
		Expression res;

		if (isAssocClass) {
			res = src.join(classifier);
		} else {
			res = src;
		}

		int totalArity = isAssocClass ? classifier.arity() - 1 : classifier.arity();

		res = ConstraintHelper.univLeftN(res, toRole - 1);
		res = ConstraintHelper.univRightN(res, totalArity - toRole);

		returnsSet = true;
		returnsSequence = false; // reset: association navigation is never a Sequence
		return res;
	}

	public final Expression access(Expression srcExpr, Expression attribute, Integer collectionType) {
		try {
			
			boolean isSequence = (collectionType == org.tzi.kodkod.ocl.CollectionType.SEQUENCE);
			boolean isSet = (collectionType == org.tzi.kodkod.ocl.CollectionType.SET);


			int srcArity = srcExpr.arity();

			if (isSequence) {
				returnsSet = false;
				returnsSequence = true;
				Expression undefined_Set_2 = undefined_Set.product(Expression.UNIV);
				Expression joinResult = srcExpr.join(attribute);
				// For sequence, srcExpr should be unary, so use undefined (arity 1)
				return srcExpr.eq(undefined).thenElse(undefined_Set_2, joinResult);
			} else if (isSet) {
				returnsSet = true;
				returnsSequence = false;
				Expression joinResult = srcExpr.join(attribute);
				// For set, srcExpr should be unary, so use undefined (arity 1)
				return srcExpr.eq(undefined).thenElse(undefined_Set, joinResult);
			} else {
				returnsSet = false;
				returnsSequence = false;
				Expression joinResult = srcExpr.join(attribute);
				// Handle different arities: if srcExpr has arity > 1, we can't use undefined
				// (arity 1)
				// In this case, we should check if the result is empty or handle it differently
				if (srcArity == 1) {
					// Normal case: unary expression, can check against undefined
					return srcExpr.eq(undefined).thenElse(undefined, joinResult);
				} else {
					// srcExpr has arity > 1 (e.g., from collection operations)
					// Can't check against undefined directly, just return joinResult
					// The undefined check should be handled at a higher level
					return joinResult;
				}
			}
		} catch (Exception e) {
			System.out.println("ERROR in access(): " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	@Override
	public boolean returnsSet(String opName) {
		return returnsSet;
	}

	@Override
	public boolean returnsSequence(String opName) {
		return returnsSequence;
	}
}
