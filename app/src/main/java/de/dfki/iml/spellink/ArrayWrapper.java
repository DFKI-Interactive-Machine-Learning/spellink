package de.dfki.iml.spellink;

public class ArrayWrapper {
    public static float[][][] getArray(long[] shape){
        return new float[1][(int) shape[1]][3];
    }
}
