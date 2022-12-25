package com.example.testadrscreencapture.glrender;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

// TODO
// 1.支持缩放、移动以及按照图片的比例进行显示。
// 2.支持纯色

/**
 * 2D 矩形的渲染，图片内容或者视频内容作为矩形的Texture被渲染。
 */
public class RectSpirit2d extends DrawableElem {

	public static interface Callback {
		void onTextureCreated(int textureId);
		void onTextureUpdated(int textureId);
		void onTextureDestroyed();
	}

	public void setCallback(Callback callback) {
		mCallback = callback;
	}

	@Override
	public void draw() {
		drawFrame(GlUtil.IDENTITY_MATRIX);
	}

	public enum ProgramType {
		TEXTURE_2D, TEXTURE_EXT
	}

	private static final int SIZEOF_FLOAT = 4;

	private static final float FULL_RECTANGLE_COORDS[] = {
			-1.0f, -1.0f, // 0 bottom left
			1.0f, -1.0f, // 1 bottom right
			-1.0f, 1.0f, // 2 top left
			1.0f, 1.0f, // 3 top right
	};

	private float mX = -1.0f, mY = -1.0f, mWidth=2.0f, mHeight=2.0f;
//	private static final float FULL_RECTANGLE_COORDS[] = {
//			-0.5f, -0.5f, // 0 bottom left
//			0.5f, -0.5f, // 1 bottom right
//			-0.5f, 0.5f, // 2 top left
//			0.5f, 0.5f, // 3 top right
//	};

	private Callback mCallback;
	private Texture2dProgram mProgram;
	private static final FloatBuffer FULL_RECTANGLE_BUF = GlUtil
			.createFloatBuffer(FULL_RECTANGLE_COORDS);
	private static final FloatBuffer FULL_RECTANGLE_TEX_BUF = GlUtil
			.createFloatBuffer(TextureRotationUtil.TEXTURE_NO_ROTATION);
	private static final FloatBuffer FULL_RECTANGLE_TEX_BUF_90 = GlUtil
			.createFloatBuffer(TextureRotationUtil.TEXTURE_ROTATED_90);
	private static final FloatBuffer FULL_RECTANGLE_TEX_BUF_180 = GlUtil
			.createFloatBuffer(TextureRotationUtil.TEXTURE_ROTATED_180);
	private static final FloatBuffer FULL_RECTANGLE_TEX_BUF_270 = GlUtil
			.createFloatBuffer(TextureRotationUtil.TEXTURE_ROTATED_270);
	private FloatBuffer mVertexArray;
	private FloatBuffer mTexCoordArray;
	private ProgramType mProgramType = ProgramType.TEXTURE_2D;
	private int mVertexCount;
	private int mCoordsPerVertex;
	private int mVertexStride;
	private int mTexCoordStride;
	private int mSourceTextureId;		// 需要显示的Texture的ID
	private Bitmap mSourceImage;		// Texture的数据源
	private AtomicBoolean mIsSourceUpdated = new AtomicBoolean(false);	// 标记是否mSourceImage更新了

	public RectSpirit2d(ProgramType type) {

		mVertexArray = FULL_RECTANGLE_BUF;
		mTexCoordArray = FULL_RECTANGLE_TEX_BUF;

		mCoordsPerVertex = 2;
		mVertexStride = mCoordsPerVertex * SIZEOF_FLOAT;
		mVertexCount = FULL_RECTANGLE_COORDS.length / mCoordsPerVertex;
		mTexCoordStride = 2 * SIZEOF_FLOAT;

		mProgramType = type;
	}

	public void release(boolean doEglCleanup) {
		if (mProgram != null) {
			if (doEglCleanup) {
				mProgram.release();
			}
			mProgram = null;
		}
	}

	public void setTextRotation(int degrees, boolean flipHorizontal,
			boolean flipVertical) {
		mTexCoordArray = ByteBuffer
				.allocateDirect(
						TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();

		float[] rotatedTex = TextureRotationUtil.getRotation(Rotation.fromInt(degrees), flipHorizontal, flipVertical);
		mTexCoordArray = ByteBuffer.allocateDirect(rotatedTex.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mTexCoordArray.put(rotatedTex).position(0);
	}

	public void move(float x, float y, float width, float height) {
		float Cur_RECTANGLE_COORDS[] = {
				x, y-height, // 0 bottom left
				x+width, y-height, // 1 bottom right
				x, y, // 2 top left
				x+width, y, // 3 top right
		};
		mVertexArray =GlUtil.createFloatBuffer(Cur_RECTANGLE_COORDS);
	}

	public void adjustTexture(int imageWidth, int imageHeight, int ow, int oh,
			boolean trans) {

		float outputWidth = ow;
		float outputHeight = oh;
		float imw ,imh;
		if (trans) {
			 imw = imageHeight;
			 imh = imageWidth;
		} else {
			 imw = imageWidth;
			 imh = imageHeight;
		}
		float ratio1 = outputWidth / imw;
		float ratio2 = outputHeight / imh;
		float ratioMax = Math.max(ratio1, ratio2);
		int imageWidthNew = Math.round(imw * ratioMax);
		int imageHeightNew = Math.round(imh * ratioMax);

		float ratioWidth = imageWidthNew / outputWidth;
		float ratioHeight = imageHeightNew / outputHeight;

		// float[] textureCords = mTexCoordArray.get(0);
		float distHorizontal;
		float distVertical;
		if (trans) {
			distVertical = (1 - 1 / ratioWidth) / 2;
			distHorizontal = (1 - 1 / ratioHeight) / 2;

		} else {
			distHorizontal = (1 - 1 / ratioWidth) / 2;
			distVertical = (1 - 1 / ratioHeight) / 2;
		}

		float[] textureCords = new float[] {
				addDistance(mTexCoordArray.get(0), distHorizontal),
				addDistance(mTexCoordArray.get(1), distVertical),
				addDistance(mTexCoordArray.get(2), distHorizontal),
				addDistance(mTexCoordArray.get(3), distVertical),
				addDistance(mTexCoordArray.get(4), distHorizontal),
				addDistance(mTexCoordArray.get(5), distVertical),
				addDistance(mTexCoordArray.get(6), distHorizontal),
				addDistance(mTexCoordArray.get(7), distVertical), };

		mTexCoordArray.clear();
		mTexCoordArray.put(textureCords).position(0);
	}

	public void adjustVertex(int imageWidth, int imageHeight, int ow, int oh) {

		float outputWidth = ow;
		float outputHeight = oh;
		float ratio1 = outputWidth / imageWidth;
		float ratio2 = outputHeight / imageHeight;
		float ratioMax = Math.min(ratio1, ratio2);
		int imageWidthNew = Math.round(imageWidth * ratioMax);
		int imageHeightNew = Math.round(imageHeight * ratioMax);

		float ratioWidth = imageWidthNew / outputWidth;
		float ratioHeight = imageHeightNew / outputHeight;

		// float[] textureCords = mTexCoordArray.get(0);
		float distHorizontal;
		float distVertical;
		distHorizontal = (1 - ratioWidth);
		distVertical = (1 - ratioHeight);

		float[] vCords = new float[] {
				addVDistance(mVertexArray.get(0), distHorizontal),
				addVDistance(mVertexArray.get(1), distVertical),
				addVDistance(mVertexArray.get(2), distHorizontal),
				addVDistance(mVertexArray.get(3), distVertical),
				addVDistance(mVertexArray.get(4), distHorizontal),
				addVDistance(mVertexArray.get(5), distVertical),
				addVDistance(mVertexArray.get(6), distHorizontal),
				addVDistance(mVertexArray.get(7), distVertical), };
		mVertexArray = ByteBuffer.allocateDirect(vCords.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mVertexArray.put(vCords).position(0);
		
	}

	private float addVDistance(float coordinate, float distance) {
		return coordinate < 0.0f ? coordinate + distance : coordinate
				- distance;
	}

	private float addDistance(float coordinate, float distance) {
		return coordinate == 0.0f ? distance : 1 - distance;
	}

	private static float flip(final float i) {
		if (i == 0.0f) {
			return 1.0f;
		}
		return 0.0f;
	}

	/**
	 * 直接设置TextureId作为Shader中的Texture
	 * @param textureId
	 */
	public void setSourceTexture(int textureId)
	{
		mSourceTextureId = textureId;
	}

	/**
	 * 获取Texture的ID
	 * @return 此RECT渲染的Texture的ID
	 */
	public int getSourceTextureId() {
		return mSourceTextureId;
	}

	/**
	 * 设置Bitmap对象作为Texture的数据源进行渲染显示
	 * @param bitmap Texture数据源，Bitmap对象
	 */
	public void setSourceImage(Bitmap bitmap) {
		mSourceImage = bitmap;
		mIsSourceUpdated.set(true);
	}

	private SurfaceTexture mSurfaceTextureNeedUpdate = null;
	public void notifyTextureFrameAvailable(SurfaceTexture sftexture) {
		mSurfaceTextureNeedUpdate = sftexture;
	}

	public Texture2dProgram getProgram() {
		return mProgram;
	}

	public void drawFrame(float[] texMatrix) {
		if(mProgram==null) {
			mProgram = new Texture2dProgram(mProgramType);
		}
		if(mSourceTextureId==0)
		{
			mSourceTextureId = GlUtil.createTextureObject(GLES20.GL_TEXTURE_2D);
			if(mCallback!=null)
				mCallback.onTextureCreated(mSourceTextureId);
		}
		if(mIsSourceUpdated.get())
		{
			Bitmap bitmap = mSourceImage;
			mIsSourceUpdated.set(false);
			if(bitmap==null && mSourceTextureId!=0)
			{
				int[] texs = new int[1];
				texs[0] = mSourceTextureId;
				GLES20.glDeleteTextures(1, texs, 0);
				mSourceTextureId = 0;
				if(mCallback!=null)
					mCallback.onTextureDestroyed();
			}
			//mSourceTextureId = GlUtil.createImageTexture(bitmap);
			if(mSourceTextureId!=0) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSourceTextureId);
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
				if(mCallback!=null)
					mCallback.onTextureUpdated(mSourceTextureId);
			}
		}
		if(mSurfaceTextureNeedUpdate!=null) {
			mSurfaceTextureNeedUpdate.updateTexImage();
			texMatrix = new float[16];
			mSurfaceTextureNeedUpdate.getTransformMatrix(texMatrix);
		}
		mProgram.draw(GlUtil.IDENTITY_MATRIX, mVertexArray, 0, mVertexCount,
				mCoordsPerVertex, mVertexStride, texMatrix, mTexCoordArray,
				mSourceTextureId, mTexCoordStride);
	}

	public class Texture2dProgram {
		private static final String TAG = GlUtil.TAG;

		// Simple vertex shader, used for all programs.
		private static final String VERTEX_SHADER =
				"uniform mat4 uMVPMatrix;\n"
				+ "uniform mat4 uTexMatrix;\n"
				+ "attribute vec4 aPosition;\n"
				+ "attribute vec4 aTextureCoord;\n"
				+ "varying vec2 vTextureCoord;\n"
				+ "void main() {\n"
				+ "    gl_Position = uMVPMatrix * aPosition;\n"
				+ "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n"
				+ "}\n";

		// Simple fragment shader for use with "normal" 2D textures.
		private static final String FRAGMENT_SHADER_2D =
				"precision mediump float;\n"
				+ "varying vec2 vTextureCoord;\n"
				+ "uniform sampler2D sTexture;\n"
				+ "void main() {\n"
				+ "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
				+ "}\n";

		// Simple fragment shader for use with external 2D textures (e.g. what
		// we get from
		// SurfaceTexture).
		private static final String FRAGMENT_SHADER_EXT =
				"#extension GL_OES_EGL_image_external : require\n"
				+ "precision mediump float;\n"
				+ "varying vec2 vTextureCoord;\n"
				+ "uniform samplerExternalOES sTexture;\n"
				+ "void main() {\n"
				+ "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
				+ "}\n";

		// Handles to the GL program and various components of it.
		private int mProgramHandle;
		private int muMVPMatrixLoc;
		private int muTexMatrixLoc;

		private int maPositionLoc;
		private int maTextureCoordLoc;

		private int mTextureTarget;

		public Texture2dProgram(ProgramType programType) {

			switch (programType) {
			case TEXTURE_2D:
				mTextureTarget = GLES20.GL_TEXTURE_2D;
				mProgramHandle = GlUtil.createProgram(VERTEX_SHADER,
						FRAGMENT_SHADER_2D);
				break;
			case TEXTURE_EXT:
				mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
				mProgramHandle = GlUtil.createProgram(VERTEX_SHADER,
						FRAGMENT_SHADER_EXT);
				break;

			default:
				throw new RuntimeException("Unhandled type " + programType);
			}
			if (mProgramHandle == 0) {
				throw new RuntimeException("Unable to create program");
			}
			Log.d(TAG, "Created program " + mProgramHandle + " (" + programType
					+ ")");

			// get locations of attributes and uniforms

			maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle,
					"aPosition");
			GlUtil.checkLocation(maPositionLoc, "aPosition");
			maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle,
					"aTextureCoord");
			GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
			muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle,
					"uMVPMatrix");
			GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
			muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle,
					"uTexMatrix");
			GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
		}

		public void release() {
			Log.d(TAG, "deleting program " + mProgramHandle);
			GLES20.glDeleteProgram(mProgramHandle);
			mProgramHandle = -1;
		}

		public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer,
				int firstVertex, int vertexCount, int coordsPerVertex,
				int vertexStride, float[] texMatrix, FloatBuffer texBuffer,
				int textureId, int texStride) {
			GlUtil.checkGlError("draw start");

			// Select the program.
			GLES20.glUseProgram(mProgramHandle);
			GlUtil.checkGlError("glUseProgram");

			// Set the texture.
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(mTextureTarget, textureId);

			// Copy the model / view / projection matrix over.
			GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
			GlUtil.checkGlError("glUniformMatrix4fv");

			// Copy the texture transformation matrix over.
			GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
			GlUtil.checkGlError("glUniformMatrix4fv");

			// Enable the "aPosition" vertex attribute.
			GLES20.glEnableVertexAttribArray(maPositionLoc);
			GlUtil.checkGlError("glEnableVertexAttribArray");

			// Connect vertexBuffer to "aPosition".
			GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
					GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
			GlUtil.checkGlError("glVertexAttribPointer");

			// Enable the "aTextureCoord" vertex attribute.
			GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
			GlUtil.checkGlError("glEnableVertexAttribArray");

			// Connect texBuffer to "aTextureCoord".
			GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT,
					false, texStride, texBuffer);
			GlUtil.checkGlError("glVertexAttribPointer");

			// Draw the rect.
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex,
					vertexCount);
			GlUtil.checkGlError("glDrawArrays");

			// Done -- disable vertex array, texture, and program.
			GLES20.glDisableVertexAttribArray(maPositionLoc);
			GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
			GLES20.glBindTexture(mTextureTarget, 0);
			GLES20.glUseProgram(0);
		}
	}

}
