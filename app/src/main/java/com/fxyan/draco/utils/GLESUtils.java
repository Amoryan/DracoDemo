package com.fxyan.draco.utils;

import android.opengl.GLES20;
import android.util.Log;

/**
 * @author fxYan
 */
public final class GLESUtils {

    private static int createShader(int shaderType, String path) {
        int shaderHandle = GLES20.glCreateShader(shaderType);
        if (shaderHandle != 0) {
            GLES20.glShaderSource(shaderHandle, AssetsUtils.read(path));
            GLES20.glCompileShader(shaderHandle);
            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {

                String error = GLES20.glGetShaderInfoLog(shaderHandle);
                Log.d("fxYan", String.format("%s , %s", path, error));

                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }
        if (shaderHandle == 0) {
            throw new RuntimeException("create shader error!");
        }
        return shaderHandle;
    }

    public static int createAndLinkProgram(String vertPath, String fragPath) {
        int vertexShaderHandle = createShader(GLES20.GL_VERTEX_SHADER, vertPath);
        int fragmentShaderHandle = createShader(GLES20.GL_FRAGMENT_SHADER, fragPath);

        int programHandle = GLES20.glCreateProgram();
        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle);
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);
            GLES20.glLinkProgram(programHandle);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {

                String error = GLES20.glGetProgramInfoLog(programHandle);
                Log.d("fxYan", error);

                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }
        if (programHandle == 0) {
            throw new RuntimeException("create program error!");
        }
        return programHandle;
    }

}
