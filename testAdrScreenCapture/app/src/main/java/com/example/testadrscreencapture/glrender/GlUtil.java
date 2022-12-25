package com.example.testadrscreencapture.glrender;

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

//import android.opengl.GLES30;

/**
 * Some OpenGL utility functions.
 */
public class GlUtil {
	public static final String TAG = "Grafika";

	/** Identity matrix for general use. Don't modify or life will get weird. */
	public static final float[] IDENTITY_MATRIX;
	static {
		IDENTITY_MATRIX = new float[16];
		Matrix.setIdentityM(IDENTITY_MATRIX, 0);
	}

	private static final int SIZEOF_FLOAT = 4;

	private GlUtil() {
	} // do not instantiate

	/**
	 * Creates a new program from the supplied vertex and fragment shaders.
	 * 
	 * @return A handle to the program, or 0 on failure.
	 */
	public static int createProgram(String vertexSource, String fragmentSource) {
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		if (vertexShader == 0) {
			return 0;
		}
		int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		if (pixelShader == 0) {
			return 0;
		}

		int program = GLES20.glCreateProgram();
		checkGlError("glCreateProgram");
		if (program == 0) {
			Log.e(TAG, "Could not create program");
		}
		GLES20.glAttachShader(program, vertexShader);
		checkGlError("glAttachShader");
		GLES20.glAttachShader(program, pixelShader);
		checkGlError("glAttachShader");
		GLES20.glLinkProgram(program);
		int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
		if (linkStatus[0] != GLES20.GL_TRUE) {
			Log.e(TAG, "Could not link program: ");
			Log.e(TAG, GLES20.glGetProgramInfoLog(program));
			GLES20.glDeleteProgram(program);
			program = 0;
		}
		return program;
	}

	/**
	 * Compiles the provided shader source.
	 * 
	 * @return A handle to the shader, or 0 on failure.
	 */
	public static int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		checkGlError("glCreateShader type=" + shaderType);
		GLES20.glShaderSource(shader, source);
		GLES20.glCompileShader(shader);
		int[] compiled = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.e(TAG, "Could not compile shader " + shaderType + ":");
			Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}
		return shader;
	}

	/**
	 * Checks to see if a GLES error has been raised.
	 */
	public static void checkGlError(String op) {
		int error = GLES20.glGetError();
		if (error != GLES20.GL_NO_ERROR) {
			String msg = op + ": glError 0x" + Integer.toHexString(error);
			Log.e(TAG, msg);
			throw new RuntimeException(msg);
		}
	}

	/**
	 * Checks to see if the location we obtained is valid. GLES returns -1 if a
	 * label could not be found, but does not set the GL error.
	 * <p>
	 * Throws a RuntimeException if the location is invalid.
	 */
	public static void checkLocation(int location, String label) {
		if (location < 0) {
			throw new RuntimeException("Unable to locate '" + label
					+ "' in program");
		}
	}

	/**
	 * 创建Texture对象，返回Texture Id
	 * @param textureTarget Texture Target的类型，可以为GLES20.GL_TEXTURE_2D或者GLES11Ext.GL_TEXTURE_EXTERNAL_OES
	 * @return 返回创建的Texture对象的Id
	 */
	public static int createTextureObject(int textureTarget) {
		int[] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		GlUtil.checkGlError("glGenTextures");

		int texId = textures[0];
		GLES20.glBindTexture(textureTarget, texId);
		GlUtil.checkGlError("glBindTexture " + texId);

		//GLES11Ext.GL_TEXTURE_EXTERNAL_OES
		GLES20.glTexParameterf(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GlUtil.checkGlError("glTexParameter");

		return texId;
	}

	/**
	 * Creates a texture from raw data.
	 * 
	 * @param data
	 *            Image data, in a "direct" ByteBuffer.
	 * @param width
	 *            Texture width, in pixels (not bytes).
	 * @param height
	 *            Texture height, in pixels.
	 * @param format
	 *            Image data format (use constant appropriate for
	 *            glTexImage2D(), e.g. GL_RGBA).
	 * @return Handle to texture.
	 */
	public static int createImageTexture(ByteBuffer data, int width,
			int height, int format) {
		int[] textureHandles = new int[1];
		int textureHandle;

		GLES20.glGenTextures(1, textureHandles, 0);
		textureHandle = textureHandles[0];
		GlUtil.checkGlError("glGenTextures");

		// Bind the texture handle to the 2D texture target.
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

		// Configure min/mag filtering, i.e. what scaling method do we use if
		// what we're rendering
		// is smaller or larger than the source image.
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GlUtil.checkGlError("loadImageTexture");

		// Load the data from the buffer into the texture handle.
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, /* level */0, format, width,
				height, /* border */0, format, GLES20.GL_UNSIGNED_BYTE, data);
		GlUtil.checkGlError("loadImageTexture");

		return textureHandle;
	}

	public static int createImageTexture(Bitmap bitmap){
		int[] texture=new int[1];
		if(bitmap!=null&&!bitmap.isRecycled()) {
			//生成纹理
			GLES20.glGenTextures(1,texture,0);
			//生成纹理
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0]);
			//设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
			//设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
			//设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
			//设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
			//根据以上指定的参数，生成一个2D纹理
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
			return texture[0];
		}
		return 0;
	}

	/**
	 * Allocates a direct float buffer, and populates it with the float array
	 * data.
	 */
	public static FloatBuffer createFloatBuffer(float[] coords) {
		// Allocate a direct ByteBuffer, using 4 bytes per float, and copy
		// coords into it.
		ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(coords);
		fb.position(0);
		return fb;
	}

	/**
	 * Writes GL version info to the log.
	 
	public static void logVersionInfo() {
		Log.i(TAG, "vendor  : " + GLES20.glGetString(GLES20.GL_VENDOR));
		Log.i(TAG, "renderer: " + GLES20.glGetString(GLES20.GL_RENDERER));
		Log.i(TAG, "version : " + GLES20.glGetString(GLES20.GL_VERSION));

		if (false) {
			int[] values = new int[1];
			GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, values, 0);
			int majorVersion = values[0];
			GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, values, 0);
			int minorVersion = values[0];
			if (GLES30.glGetError() == GLES30.GL_NO_ERROR) {
				Log.i(TAG, "iversion: " + majorVersion + "." + minorVersion);
			}
		}
	}*/
}
