package org.tzi.kodkod.ocl;

/**
 * Constants for different types of OCL collection operations.
 * Used to distinguish between operations on different collection types.
 * 
 * @author SME Lab
 */
public class CollectionType {
    /** Operations on single objects (non-collection) */
    public static final int OBJECT = 0;

    /** Operations on Set collections */
    public static final int SET = 1;

    /** Operations on Sequence collections */
    public static final int SEQUENCE = 2;

    // Future extension possibilities:
    // public static final int BAG = 3;
    // public static final int ORDERED_SET = 4;

    /**
     * Private constructor to prevent instantiation
     */
    private CollectionType() {
    }

    /**
     * Converts collection type to string for debugging
     * 
     * @param type the collection type constant
     * @return string representation
     */
    public static String toString(int type) {
        switch (type) {
            case OBJECT:
                return "OBJECT";
            case SET:
                return "SET";
            case SEQUENCE:
                return "SEQUENCE";
            default:
                return "UNKNOWN(" + type + ")";
        }
    }

    /**
     * Determines the collection type based on flags.
     * Priority: SEQUENCE > SET > OBJECT
     * 
     * @param isSet      true if the operation is on a Set
     * @param isSequence true if the operation is on a Sequence
     * @return the corresponding collection type constant
     */
    public static int from(boolean isSet, boolean isSequence) {
        if (isSequence) {
            return SEQUENCE;
        } else if (isSet) {
            return SET;
        } else {
            return OBJECT;
        }
    }
}
