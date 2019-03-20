package com.fxyan.draco.entity;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author fxYan
 */
public class PlyModel {

    public static final int PER_FLOAT_BYTES = 4;
    public static final int PER_INT_BYTES = 4;

    public static final int PER_VERTEX_COORD_SIZE = 3;
    public static final int PER_VERTEX_NORMAL_SIZE = 3;
    public static final int PER_VERTEX_SIZE = PER_VERTEX_COORD_SIZE + PER_VERTEX_NORMAL_SIZE;
    public static final int PER_VERTEX_STRIDE = PER_VERTEX_SIZE * PER_FLOAT_BYTES;

    protected FloatBuffer vertexBuffer;
    protected float[] vertex;
    protected IntBuffer indexBuffer;
    protected int[] index;
    public int type;

    public PlyModel(float[] _vertex, int[] _index, int type) {
        this.vertex = _vertex;
        this.index = _index;
        this.type = type;

        vertexBuffer = ByteBuffer.allocateDirect(vertex.length * PER_FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertex);
        vertexBuffer.position(0);

        indexBuffer = ByteBuffer.allocateDirect(index.length * PER_INT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
                .put(index);
        indexBuffer.position(0);
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    public void onDrawFrame(float[] mvpMatrix, int programHandle) {
        int mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        int textureHandle = GLES20.glGetUniformLocation(programHandle, "u_Texture");
        GLES20.glUniform1i(textureHandle, 0);

        vertexBuffer.position(0);
        int positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, PER_VERTEX_COORD_SIZE, GLES20.GL_FLOAT, false, PER_VERTEX_STRIDE, vertexBuffer);

        vertexBuffer.position(PER_VERTEX_COORD_SIZE);
        int normalHandle = GLES20.glGetAttribLocation(programHandle, "a_Normal");
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, PER_VERTEX_NORMAL_SIZE, GLES20.GL_FLOAT, true, PER_VERTEX_STRIDE, vertexBuffer);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, index.length, GLES20.GL_UNSIGNED_INT, indexBuffer);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
    }
}
