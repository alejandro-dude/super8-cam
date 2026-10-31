package com.alejandro.super8cam.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private val FULL_RECT = floatArrayOf(
    -1f, -1f, 0f, 1f,
    1f, -1f, 0f, 1f,
    -1f, 1f, 0f, 1f,
    1f, 1f, 0f, 1f
)

private val TEX_COORDS = floatArrayOf(
    0f, 0f, 0f, 1f,
    1f, 0f, 0f, 1f,
    0f, 1f, 0f, 1f,
    1f, 1f, 0f, 1f
)

/** Compiles the film-look shader once and draws the camera's OES texture through it. */
class FilmRenderer {

    private var programId = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uTime = 0
    private var uFrameSeed = 0
    private var uAspectFix = 0

    private val posBuffer = toFloatBuffer(FULL_RECT)
    private val texBuffer = toFloatBuffer(TEX_COORDS)

    var cameraTextureId = -1
        private set

    fun setup() {
        programId = buildProgram(FilmShader.VERTEX_SHADER, FilmShader.FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        uTime = GLES20.glGetUniformLocation(programId, "uTime")
        uFrameSeed = GLES20.glGetUniformLocation(programId, "uFrameSeed")
        uAspectFix = GLES20.glGetUniformLocation(programId, "uAspectFix")
        cameraTextureId = createOesTexture()
    }

    fun draw(texMatrix: FloatArray, timeSeconds: Float, frameSeed: Float, aspect: Float, viewportW: Int, viewportH: Int) {
        GLES20.glViewport(0, 0, viewportW, viewportH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 4, GLES20.GL_FLOAT, false, 0, posBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 4, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uTime, timeSeconds)
        GLES20.glUniform1f(uFrameSeed, frameSeed)
        GLES20.glUniform1f(uAspectFix, aspect)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    fun release() {
        if (programId != 0) GLES20.glDeleteProgram(programId)
        if (cameraTextureId != -1) GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
    }
}

private fun toFloatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data)
        position(0)
    }

private fun createOesTexture(): Int {
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    return ids[0]
}

private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vs)
    GLES20.glAttachShader(program, fs)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteProgram(program)
        error("Program link failed: $log")
    }
    return program
}

private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(shader)
        GLES20.glDeleteShader(shader)
        error("Shader compile failed: $log")
    }
    return shader
}
