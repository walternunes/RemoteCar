package jnuneslab.com.remotecar.draw.gauge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Draws an analog gauge for displaying rotation measurements in three-space
 * from device sensors.
 * 
 * Note that after Android 4.0 TextureView exists, as does SurfaceView for
 * Android 3.0 which won't hog the UI thread like View will. This should only be
 * used with devices or certain libraries that require View.
 * 
 */
public final class GaugeRotation extends View
{
	private static final String tag = GaugeRotation.class.getSimpleName();

	// drawing tools
	private RectF rimOuterRect;
	private RectF rimTopRect;
	private RectF rimBottomRect;
	private RectF rimLeftRect;
	private RectF rimRightRect;
	private Paint rimOuterPaint;

	// Keep static bitmaps of the gauge so we only have to redraw if we have to
	// Static bitmap for the bezel of the gauge
	private Bitmap bezelBitmap;
	// Static bitmap for the face of the gauge
	private Bitmap faceBitmap;

	// Keep track of the rotation of the device
	private float[] rotation = new float[3];

	// Rectangle to draw the earth section of the gauge face
	private RectF earthRect;
	// Rectangle to draw the face of the gauge
	private RectF faceRect;
	// Rectangle to draw the rim of the gauge
	private RectF rimRect;
	// Rectangle to draw the sky section of the gauge face
	private RectF skyRect;
	// Rectangle to draw the sky section of the gauge face
	private RectF skyBackgroundRect;

	// Paint to draw the red arrow for the roll angle scales
	private Paint arrowPaint;
	// Paint to draw the gauge bitmaps
	private Paint backgroundPaint;
	// Paint to draw the earth portion of the gauge face
	private Paint earthPaint;
	// Paint to draw the bitmap for the pitch angle scale's guide gauge
	private Paint gaugeGuidePaint;
	// Paint to draw numbers
	private Paint numericPaint;
	// Paint to draw the outer rim of the bezel
	private Paint rimCirclePaint;
	// Paint to draw the rim of the bezel
	private Paint rimPaint;
	// Paint to draw the shadow of the bezel
	private Paint rimShadowPaint;
	// Paint to draw the scales
	private Paint scalePaint;
	// Paint to draw the sky portion of the gauge face
	private Paint skyPaint;
	// Paint to draw the small tick marks
	private Paint smallTickPaint;
	// Paint to draw the thick tick marks
	private Paint thickScalePaint;

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 */
	public GaugeRotation(Context context)
	{
		super(context);

		initDrawingTools();
	}

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 * @param attrs
	 */
	public GaugeRotation(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		initDrawingTools();
	}

	/**
	 * Create a new instance.
	 * 
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public GaugeRotation(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);

		initDrawingTools();
	}

	/**
	 * Update the rotation of the device.
	 * 
	 * @param rotation
	 */
	public void updateRotation(float[] rotation)
	{
		System.arraycopy(rotation, 0, this.rotation, 0, rotation.length);

		this.invalidate();
	}

	private void initDrawingTools()
	{
		// Rectangle for the rim of the gauge bezel
		rimRect = new RectF(0.12f, 0.12f, 0.88f, 0.88f);

		// Paint for the rim of the gauge bezel
		rimPaint = new Paint();
		rimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		// The linear gradient is a bit skewed for realism
		rimPaint.setShader(new LinearGradient(0.40f, 0.0f, 0.60f, 1.0f, Color
				.rgb(0, 0, 0), Color.rgb(0, 0, 0), Shader.TileMode.CLAMP));

		float rimOuterSize = -0.04f;
		rimOuterRect = new RectF();
		rimOuterRect.set(rimRect.left + rimOuterSize, rimRect.top
				+ rimOuterSize, rimRect.right - rimOuterSize, rimRect.bottom
				- rimOuterSize);

		// still a work in progress changing the rimOuterSize will not
		// dynamically change the small rectangles to the appropriate size.
		rimTopRect = new RectF(0.5f, 0.106f, 0.5f, 0.06f);
		rimTopRect.set(rimTopRect.left + rimOuterSize, rimTopRect.top
				+ rimOuterSize, rimTopRect.right - rimOuterSize,
				rimTopRect.bottom - rimOuterSize);

		rimBottomRect = new RectF(0.5f, 0.94f, 0.5f, 0.894f);
		rimBottomRect.set(rimBottomRect.left + rimOuterSize, rimBottomRect.top
				+ rimOuterSize, rimBottomRect.right - rimOuterSize,
				rimBottomRect.bottom - rimOuterSize);

		rimLeftRect = new RectF(0.106f, 0.5f, 0.06f, 0.5f);
		rimLeftRect.set(rimLeftRect.left + rimOuterSize, rimLeftRect.top
				+ rimOuterSize, rimLeftRect.right - rimOuterSize,
				rimLeftRect.bottom - rimOuterSize);

		rimRightRect = new RectF(0.94f, 0.5f, 0.894f, 0.5f);
		rimRightRect.set(rimRightRect.left + rimOuterSize, rimRightRect.top
				+ rimOuterSize, rimRightRect.right - rimOuterSize,
				rimRightRect.bottom - rimOuterSize);

		rimOuterPaint = new Paint();
		rimOuterPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		rimOuterPaint.setColor(Color.rgb(255, 255, 255));

		// Paint for the outer circle of the gauge bezel
		rimCirclePaint = new Paint();
		rimCirclePaint.setAntiAlias(true);
		rimCirclePaint.setStyle(Paint.Style.STROKE);
		rimCirclePaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));
		rimCirclePaint.setStrokeWidth(0.005f);

		float rimSize = 0.02f;
		faceRect = new RectF();
		faceRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		earthRect = new RectF();
		earthRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		skyRect = new RectF();
		skyRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		skyBackgroundRect = new RectF();
		skyBackgroundRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		// set to black
		skyPaint = new Paint();
		skyPaint.setAntiAlias(true);
		skyPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		skyPaint.setColor(Color.WHITE);

		// set to white
		earthPaint = new Paint();
		earthPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		earthPaint.setShader(new LinearGradient(0.40f, 0.0f, 0.60f, 1.0f, Color
				.rgb(238, 238, 238), Color.rgb(238, 238, 238),
				Shader.TileMode.CLAMP));

		rimShadowPaint = new Paint();
		rimShadowPaint.setShader(new RadialGradient(0.5f, 0.5f, faceRect
				.width() / 2.0f, new int[]
		{ 0x00000000, 0x00000500, 0x50000500 }, new float[]
		{ 0.96f, 0.96f, 0.99f }, Shader.TileMode.MIRROR));
		rimShadowPaint.setStyle(Paint.Style.FILL);

		scalePaint = new Paint();
		scalePaint.setStyle(Paint.Style.STROKE);
		scalePaint.setColor(Color.WHITE);
		scalePaint.setStrokeWidth(0.005f);
		scalePaint.setAntiAlias(true);

		arrowPaint = new Paint();
		arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		arrowPaint.setColor(Color.RED);
		arrowPaint.setStrokeWidth(0.005f);
		arrowPaint.setAntiAlias(true);

		thickScalePaint = new Paint();
		thickScalePaint.setStyle(Paint.Style.STROKE);
		thickScalePaint.setColor(Color.WHITE);
		thickScalePaint.setStrokeWidth(0.008f);
		thickScalePaint.setAntiAlias(true);

		numericPaint = new Paint();
		numericPaint.setTextSize(0.035f);
		numericPaint.setColor(Color.WHITE);
		numericPaint.setTypeface(Typeface.DEFAULT);
		numericPaint.setAntiAlias(true);
		numericPaint.setTextAlign(Paint.Align.CENTER);
		numericPaint.setLinearText(true);

		smallTickPaint = new Paint();
		smallTickPaint.setStyle(Paint.Style.STROKE);
		smallTickPaint.setColor(Color.argb(100, 255, 255, 255));
		smallTickPaint.setStrokeWidth(0.005f);
		smallTickPaint.setAntiAlias(true);

		gaugeGuidePaint = new Paint();
		gaugeGuidePaint.setFilterBitmap(true);
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;

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

	private int chooseDimension(int mode, int size)
	{
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY)
		{
			return size;
		}
		else
		{
			return getPreferredSize();
		}
	}

	// in case there is no size specified
	private int getPreferredSize()
	{
		return 300;
	}

	/**
	 * Draw the gauge rim.
	 * 
	 * @param canvas
	 */
	private void drawRim(Canvas canvas)
	{
		// First draw the most back rim
		canvas.drawOval(rimOuterRect, rimOuterPaint);
		// Then draw the small black line
		canvas.drawOval(rimRect, rimPaint);
		// now the outer rim circle
		// canvas.drawOval(rimRect, rimCirclePaint);

		canvas.drawRect(rimTopRect, rimOuterPaint);
		// bottom rect
		canvas.drawRect(rimBottomRect, rimOuterPaint);
		// left rect
		canvas.drawRect(rimLeftRect, rimOuterPaint);
		// right rect
		canvas.drawRect(rimRightRect, rimOuterPaint);
	}

	/**
	 * Draw the gauge face.
	 * 
	 * @param canvas
	 */
	private void drawFace(Canvas canvas)
	{
		// free the old bitmap
		if (faceBitmap != null)
		{
			faceBitmap.recycle();
		}

		faceBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);

		Canvas faceCanvas = new Canvas(faceBitmap);
		float scale = (float) getWidth();
		faceCanvas.scale(scale, scale);

		skyBackgroundRect.set(rimRect.left, rimRect.top, rimRect.right,
				rimRect.bottom);

		faceCanvas.drawArc(skyBackgroundRect, 0, 360, true, skyPaint);

		int[] allpixels = new int[faceBitmap.getHeight()
				* faceBitmap.getWidth()];

		faceBitmap.getPixels(allpixels, 0, faceBitmap.getWidth(), 0, 0,
				faceBitmap.getWidth(), faceBitmap.getHeight());

		for (int i = 0; i < faceBitmap.getHeight() * faceBitmap.getWidth(); i++)
		{
			allpixels[i] = Color.TRANSPARENT;
		}

		int height = (int) ((faceBitmap.getHeight() / 2) - ((faceBitmap
				.getHeight() / 2.5) * rotation[1]));

		if (height > faceBitmap.getHeight())
		{
			height = faceBitmap.getHeight();
		}

		faceBitmap.setPixels(allpixels, 0, faceBitmap.getWidth(), 0, 0,
				faceBitmap.getWidth(), height);

		float x = -rotation[2];

		// Restrict x between 1 and -1
		if (x > 1)
		{
			x = 1;
		}
		if (x < -1)
		{
			x = -1;
		}

		// Calculate the rotation angle from
		// http://www.st.com/web/en/resource/technical/document/application_note/CD00268887.pdf
		float angle = (float) (Math.asin(x) * 57.2957795);

		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.rotate(-angle, faceBitmap.getWidth() / 2f,
				faceBitmap.getHeight() / 2f);

		canvas.drawBitmap(faceBitmap, 0, 0, backgroundPaint);
		canvas.restore();
	}

	/**
	 * Draw the gauge bezel.
	 * 
	 * @param canvas
	 */
	private void drawBezel(Canvas canvas)
	{
		if (bezelBitmap == null)
		{
			Log.w(tag, "Bezel not created");
		}
		else
		{
			canvas.drawBitmap(bezelBitmap, 0, 0, backgroundPaint);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		Log.d(tag, "Size changed to " + w + "x" + h);

		regenerateBezel();
	}

	/**
	 * Regenerate the background image. This should only be called when the size
	 * of the screen has changed. The background will be cached and can be
	 * reused without needing to redraw it.
	 */
	private void regenerateBezel()
	{
		// free the old bitmap
		if (bezelBitmap != null)
		{
			bezelBitmap.recycle();
		}

		bezelBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);
		Canvas bezelCanvas = new Canvas(bezelBitmap);
		float scale = (float) getWidth();
		bezelCanvas.scale(scale, scale);

		drawRim(bezelCanvas);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		drawBezel(canvas);
		drawFace(canvas);

		float scale = (float) getWidth();
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.scale(scale, scale);

		canvas.restore();
	}

}
