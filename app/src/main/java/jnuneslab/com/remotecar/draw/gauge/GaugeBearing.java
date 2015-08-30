package jnuneslab.com.remotecar.draw.gauge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/*
 * Gyroscope Explorer
 * Copyright (C) 2013, Kaleb Kircher - Boki Software, Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Draws an analog gauge (a compass) for displaying bearing measurements from
 * device sensors.
 * 
 * Note that before TextureView in Android 4.0, SurfaceView existed as an
 * alternative to the UI hogging View class. We want to render outside of the UI
 * thread, which is what SurfaceView is for. However, SurfaceView has
 * significant drawbacks. TextView is essentially the same as the SurfaceView,
 * but it behaves as a normal view and supports normal view operations. TextView
 * requires hardware acceleration and, because it is more flexible than
 * SurfaceView, incurs a performance hit. You would not want to use it for
 * running a full-screen game.
 * 
 */
public final class GaugeBearing extends View
{



	private static final String tag = GaugeBearing.class.getSimpleName();

	// drawing tools
	private RectF rimRect;
	private Paint rimPaint;

	private RectF faceRect;
	private Paint facePaint;

	// added by Scott for the rectangles on the outside circle
	private RectF rimOuterTopRect;
	private RectF rimOuterBottomRect;
	private RectF rimOuterLeftRect;
	private RectF rimOuterRightRect;

	// Static bitmap for the face of the gauge
	private Bitmap hand;
	private Paint handPaint;
	private Path handPath;

	private Paint backgroundPaint;
	// end drawing tools

	private Bitmap background; // holds the cached static part

	private Canvas handCanvas;
	
	// the one in the top center (12 o'clock)
	private static final int centerDegree = 0;
	private static final int minDegrees = 0;
	private static final int maxDegrees = 360;

	// hand dynamics -- all are angular expressed in F degrees
	private boolean handInitialized = false;
	private float handPosition = centerDegree;
	private float handTarget = centerDegree;
	private float handVelocity = 0.0f;
	private float handAcceleration = 0.0f;
	private long lastHandMoveTime = -1L;

	private int unitsOfMeasure = UnitsOfMeasure.DEGREES;

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 */
	public GaugeBearing(Context context)
	{
		super(context);
		init();
	}

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 * @param attrs
	 */
	public GaugeBearing(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public GaugeBearing(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init();
	}

	public int getUnitsOfMeasure()
	{
		return unitsOfMeasure;
	}

	public void setUnitsOfMeasure(int unitsOfMeasure)
	{
		this.unitsOfMeasure = unitsOfMeasure;
	}

	/**
	 * Update the bearing of the device.
	 * 
	 * @param azimuth
	 */
	public void updateBearing(float azimuth)
	{
		// Adjust the range: 0 < range <= 360 (from: -180 < range <=
		// 180)
		azimuth = (float) (Math.toDegrees(azimuth) + 360) % 360;

		setHandTarget(azimuth);
	}
	
	public void updateBearing2(float azimuth)
    {
        // Adjust the range: 0 < range <= 360 (from: -180 < range <=
        // 180)
 
        setHandTarget(azimuth);
    }

	/**
	 * Run the instance. This can be thought of as onDraw().
	 */
	protected void onDraw(Canvas canvas)
	{
		drawBackground(canvas);

		drawHand(canvas);

		canvas.restore();

		moveHand();
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		Bundle bundle = (Bundle) state;
		Parcelable superState = bundle.getParcelable("superState");
		super.onRestoreInstanceState(superState);

		handInitialized = bundle.getBoolean("handInitialized");
		handPosition = bundle.getFloat("handPosition");
		handTarget = bundle.getFloat("handTarget");
		handVelocity = bundle.getFloat("handVelocity");
		handAcceleration = bundle.getFloat("handAcceleration");
		lastHandMoveTime = bundle.getLong("lastHandMoveTime");
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable("superState", superState);
		state.putBoolean("handInitialized", handInitialized);
		state.putFloat("handPosition", handPosition);
		state.putFloat("handTarget", handTarget);
		state.putFloat("handVelocity", handVelocity);
		state.putFloat("handAcceleration", handAcceleration);
		state.putLong("lastHandMoveTime", lastHandMoveTime);
		return state;
	}

	/**
	 * Initialize the instance.
	 */
	private void init()
	{
		initDrawingTools();
	}

	/**
	 * Initialize the drawing tools.
	 */
	private void initDrawingTools()
	{

		rimRect = new RectF(0.1f, 0.1f, 0.9f, 0.9f);

		// the linear gradient is a bit skewed for realism
		rimPaint = new Paint();
		rimPaint.setAntiAlias(true);
		rimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		rimPaint.setColor(Color.rgb(255, 255, 255));

		float rimSize = 0.03f;
		faceRect = new RectF();
		faceRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		rimOuterTopRect = new RectF(0.46f, 0.076f, 0.54f, 0.11f);

		rimOuterBottomRect = new RectF(0.46f, 0.89f, 0.54f, 0.924f);

		rimOuterLeftRect = new RectF(0.076f, 0.46f, 0.11f, 0.54f);

		rimOuterRightRect = new RectF(0.89f, 0.46f, 0.924f, 0.54f);

		facePaint = new Paint();
		facePaint.setStyle(Paint.Style.FILL);
		facePaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		facePaint.setAntiAlias(true);

		handPaint = new Paint();
		handPaint.setAntiAlias(true);
		handPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		handPaint.setColor(Color.WHITE);
		handPaint.setStyle(Paint.Style.FILL);

		handPath = new Path();
		handPath.moveTo(0.5f, 0.5f + 0.32f);
		handPath.lineTo(0.5f - 0.02f, 0.5f + 0.32f - 0.32f);

		handPath.lineTo(0.5f, 0.5f - 0.32f);
		handPath.lineTo(0.5f + 0.02f, 0.5f + 0.32f - 0.32f);
		handPath.lineTo(0.5f, 0.5f + 0.32f);
		handPath.addCircle(0.5f, 0.5f, 0.025f, Path.Direction.CW);

		backgroundPaint = new Paint();
		backgroundPaint.setFilterBitmap(true);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int chosenWidth = chooseDimension(widthMode, widthSize);
		int chosenHeight = chooseDimension(heightMode, heightSize);

		int chosenDimension = Math.min(chosenWidth, chosenHeight);

		setMeasuredDimension(chosenDimension, chosenDimension);
	}

	/**
	 * Chose the dimension of the view.
	 * 
	 * @param mode
	 * @param size
	 * @return
	 */
	private int chooseDimension(int mode, int size)
	{
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY)
		{
			return size;
		}
		else
		{ // (mode == MeasureSpec.UNSPECIFIED)
			return getPreferredSize();
		}
	}

	/**
	 * In case there is no size specified
	 * 
	 * @return
	 */
	private int getPreferredSize()
	{
		return 300;
	}

	/**
	 * Draw the rim of the gauge.
	 * 
	 * @param canvas
	 */
	private void drawRim(Canvas canvas)
	{
		// first, draw the metallic body
		canvas.drawOval(rimRect, rimPaint);

		// top rect
		canvas.drawRect(rimOuterTopRect, rimPaint);
		// bottom rect
		canvas.drawRect(rimOuterBottomRect, rimPaint);
		// left rect
		canvas.drawRect(rimOuterLeftRect, rimPaint);
		// right rect
		canvas.drawRect(rimOuterRightRect, rimPaint);
	}

	/**
	 * Draw the face of the gauge.
	 * 
	 * @param canvas
	 */
	private void drawFace(Canvas canvas)
	{
		canvas.drawOval(faceRect, facePaint);
	}

	/**
	 * Convert degrees to an angle.
	 * 
	 * @param degree
	 * @return
	 */
	private float degreeToAngle(float degree)
	{
		return degree;
	}

	/**
	 * Draw the gauge hand.
	 * 
	 * @param canvas
	 */
	/**
	 * Draw the gauge hand.
	 * 
	 * @param canvas
	 */
	private void drawHand(Canvas canvas)
	{
		// *Bug Notice* We draw the hand with a bitmap and a new canvas because
		// canvas.drawPath() doesn't work. This seems to be related to devices
		// with hardware acceleration enabled.

		// free the old bitmap
		if (hand != null)
		{
			hand.recycle();
		}

		hand = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);
		handCanvas = new Canvas(hand);
		float scale = (float) getWidth();
		handCanvas.scale(scale, scale);

		if (handInitialized)
		{
			float handAngle = degreeToAngle(handPosition);
			handCanvas.save(Canvas.MATRIX_SAVE_FLAG);
			handCanvas.rotate(handAngle, 0.5f, 0.5f);
			handCanvas.drawPath(handPath, handPaint);
		}
		else
		{
			float handAngle = degreeToAngle(0);
			handCanvas.save(Canvas.MATRIX_SAVE_FLAG);
			handCanvas.rotate(handAngle, 0.5f, 0.5f);
			handCanvas.drawPath(handPath, handPaint);
		}

		canvas.drawBitmap(hand, 0, 0, backgroundPaint);
	}

	/**
	 * Draw the background of the gauge.
	 * 
	 * @param canvas
	 */
	private void drawBackground(Canvas canvas)
	{
		if (background == null)
		{
			Log.w(tag, "Background not created");
		}
		else
		{
			canvas.drawBitmap(background, 0, 0, backgroundPaint);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		Log.d(tag, "Size changed to " + w + "x" + h);

		regenerateBackground();
	}

	/**
	 * Regenerate the background image. This should only be called when the size
	 * of the screen has changed. The background will be cached and can be
	 * reused without needing to redraw it.
	 */
	private void regenerateBackground()
	{
		// free the old bitmap
		if (background != null)
		{
			background.recycle();
		}

		background = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);
		Canvas backgroundCanvas = new Canvas(background);
		float scale = (float) getWidth();
		backgroundCanvas.scale(scale, scale);

		drawRim(backgroundCanvas);
		drawFace(backgroundCanvas);
	}

	/**
	 * Move the hand.
	 */
	private void moveHand()
	{
		handPosition = handTarget;
	}

	/**
	 * Indicate where the hand should be moved to.
	 * 
	 * @param bearing
	 */
	private void setHandTarget(float bearing)
	{
		if (bearing < minDegrees)
		{
			bearing = minDegrees;
		}
		else if (bearing > maxDegrees)
		{
			bearing = maxDegrees;
		}

		handTarget = bearing;
		handInitialized = true;

		invalidate();
	}

}
