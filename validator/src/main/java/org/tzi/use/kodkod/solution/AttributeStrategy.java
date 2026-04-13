package org.tzi.use.kodkod.solution;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import kodkod.instance.Tuple;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.SequenceType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.SequenceValue;
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

	/**
	 * Buffer for sequence attributes: objectAtom -> TreeMap(index -> value).
	 * Needed because Kodkod may return tuples in arbitrary order;
	 * we must sort by index before building the SequenceValue.
	 */
	private final Map<Object, TreeMap<Integer, Value>> sequenceBuffers = new HashMap<>();

	public AttributeStrategy(UseSystemApi sApi, Map<String, MObjectState> objectStates, String attributeName) {
		super(sApi, objectStates);
		this.attributeName = attributeName;
	}

	@Override
	public void createElement(Tuple currentTuple) throws UseApiException {

		Object objectAtom  = currentTuple.atom(0);
		MObjectState mObjectState = objectStates.get(objectAtom);
		MAttribute   mAttribute  = findAttribute(mObjectState);

		if (mAttribute == null || mAttribute.isDerived()) return;

		Type attributeType = mAttribute.type();

		if (attributeType.isKindOfSequence(Type.VoidHandling.INCLUDE_VOID)) {
			// Sequence: ternary tuple (object, index, value)
			// Collect into buffer sorted by index, then rebuild SequenceValue.
			int    index     = (Integer) currentTuple.atom(1);
			Object valueAtom = currentTuple.atom(2);

			SequenceType seqType = (SequenceType) attributeType;
			ValueCreator creator = new ValueCreator(mModel, objectStates, mAttribute, mObjectState);
			// Pass elemType (e.g. Integer), NOT seqType — so create() goes to createValue(), not createCollectionValue()
			Value elemVal = creator.create(seqType.elemType(), valueAtom);

			if (elemVal == null) return;

			// Store (index -> value) in buffer for this object
			sequenceBuffers
				.computeIfAbsent(objectAtom, k -> new TreeMap<>())
				.put(index, elemVal);

			// Rebuild SequenceValue in index order
			SequenceValue sequenceValue = new SequenceValue(seqType.elemType());
			for (Value v : sequenceBuffers.get(objectAtom).values()) {
				sequenceValue = sequenceValue.append(seqType, v);
			}

			systemApi.setAttributeValueEx(mObjectState.object(), mAttribute, sequenceValue);

		} else {
			// Set / primitive: binary tuple (object, value)
			Object atom = currentTuple.atom(1);
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
