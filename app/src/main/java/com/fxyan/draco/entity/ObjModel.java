package com.fxyan.draco.entity;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author fxYan
 */
public class ObjModel
        implements IModel {

    protected FloatBuffer vertexBuffer;
    protected float[] vertex;

    public ObjModel(float[] _vertex) {
        this.vertex = _vertex;

        vertexBuffer = ByteBuffer.allocateDirect(vertex.length * PER_FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertex);
        vertexBuffer.position(0);
    }

    public void onDrawFrame(float[] mvpMatrix, int programHandle) {
        int mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        vertexBuffer.position(0);
        int positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, PER_VERTEX_COORD_SIZE, GLES20.GL_FLOAT, false, PER_VERTEX_STRIDE, vertexBuffer);

        vertexBuffer.position(PER_VERTEX_COORD_SIZE);
        int normalHandle = GLES20.glGetAttribLocation(programHandle, "a_Normal");
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, PER_VERTEX_NORMAL_SIZE, GLES20.GL_FLOAT, true, PER_VERTEX_STRIDE, vertexBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertex.length / PER_VERTEX_SIZE);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
    }
}
