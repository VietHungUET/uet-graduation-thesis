package org.tzi.use.kodkod.solution;

import java.util.Map;

import kodkod.instance.Tuple;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObjectState;

/**
 * Strategy for the creation of attributes.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class AttributeStrategy extends ElementStrategy {

	private String attributeName;

	public AttributeStrategy(UseSystemApi sApi, Map<String, MObjectState> objectStates, String attributeName) {
		super(sApi, objectStates);
		this.attributeName = attributeName;
	}

	@Override
	public void createElement(Tuple currentTuple) throws UseApiException {
		// TODO collection typed attributes are not supported 100%

		MObjectState mObjectState = objectStates.get(currentTuple.atom(0));

		MAttribute mAttribute = findAttribute(mObjectState);

		if (mAttribute != null && !mAttribute.isDerived()) {
			Type attributeType = mAttribute.type();
			Object atom;

			// Sequence uses ternary relation: (object, index, value)
			// Need to extract value from atom(2) instead of atom(1)
			if (attributeType.isKindOfSequence(Type.VoidHandling.INCLUDE_VOID)) {
				// For Sequence: atom(0)=object, atom(1)=index, atom(2)=value
				atom = currentTuple.atom(2);
			} else {
				// For Set and primitive types: atom(0)=object, atom(1)=value
				atom = currentTuple.atom(1);
			}

			ValueCreator valueCreator = new ValueCreator(mModel, objectStates, mAttribute, mObjectState);
			Value newVal = valueCreator.create(attributeType, atom);

			if (newVal != null) {
				systemApi.setAttributeValueEx(mObjectState.object(), mAttribute, newVal);
			}
		}
	}

	private MAttribute findAttribute(MObjectState mObjectState) {
		if (mObjectState != null) {
			for (MAttribute mAttribute : mObjectState.attributeValueMap().keySet()) {
				if (mAttribute.name().equals(attributeName)) {
					return mAttribute;
				}
			}
		}
		return null;
	}
}
