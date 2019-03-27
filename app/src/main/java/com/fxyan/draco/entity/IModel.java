package com.fxyan.draco.entity;

/**
 * @author fxYan
 */
public interface IModel {
    int PER_FLOAT_BYTES = 4;
    int PER_INT_BYTES = 4;

    int PER_VERTEX_COORD_SIZE = 3;
    int PER_VERTEX_NORMAL_SIZE = 3;
    int PER_VERTEX_SIZE = PER_VERTEX_COORD_SIZE + PER_VERTEX_NORMAL_SIZE;
    int PER_VERTEX_STRIDE = PER_VERTEX_SIZE * PER_FLOAT_BYTES;

    default void onSurfaceCreated() {
    }

    default void onSurfaceChanged(int width, int height) {
    }

    default void onDrawFrame(float[] mvpMatrix, int programHandle) {
    }
}
