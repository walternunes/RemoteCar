package jnuneslab.com.remotecar.activity;

import java.text.DecimalFormat;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.view.View.OnClickListener;

import jnuneslab.com.remotecar.R;
import jnuneslab.com.remotecar.draw.filter.MeanFilter;
import jnuneslab.com.remotecar.draw.gauge.GaugeRotation;
import jnuneslab.com.remotecar.bluetooth.BluetoothSPPConnection;
import jnuneslab.com.remotecar.bluetooth.BluetoothSPPConnectionListener;

/**
 * Main Activate responsible for initialize all the sensors and filters necessary to draw the gauge
 * Also starts the interface with the bluetooth interface and send to the remote all the command given by the user
 */
public class PanelActivity extends Activity implements SensorEventListener, BluetoothSPPConnectionListener{

    // Debugging
    private static final String TAG = PanelActivity.class.getSimpleName();
    private static final boolean D = false;
    private static final boolean DEBUG_DIRECTION = false;

    private static final int MEAN_FILTER_WINDOW = 10;
    private static final int MIN_SAMPLE_COUNT = 30;

    private boolean hasInitialOrientation = false;
    private boolean stateInitializedCalibrated = false;

    private GaugeRotation mGaugeTiltCalibrated;
    private DecimalFormat df;

    // Calibrated maths
    private float[] currentRotationMatrixCalibrated;
    private float[] gyroscopeOrientationCalibrated;

    // Uncalibrated maths - Raw
    private float[] mCurrentRotationMatrixRaw;

    // accelerometer and magnetometer based rotation matrix
    private float[] mInitialRotationMatrix;

    // accelerometer vector
    private float[] mAcceleration;

    // mMagnetic field vector
    private float[] mMagnetic;

    // calibrate sensor variables
    private int mAccelerationSampleCount = 0;
    private int mMagneticSampleCount = 0;

    // filter variables
    private MeanFilter mAccelerationFilter;
    private MeanFilter mMagneticFilter;

    // SensorManager used to register for Sensor Events.
    private SensorManager mSensorManager;

    // ImageViews
    private ImageView mImageViewAccLevel1;
    private ImageView mImageViewAccLevel2;
    private ImageView mImageViewAccLevel3;

    private ImageView mImageViewAccLevelr2;
    private ImageView mImageViewAccLevelr3;

    private ImageView mImageViewStandWarning;

    // TextViews
    private TextView mXAxisCalibrated;
    private TextView mYAxisCalibrated;
    private TextView mZAxisCalibrated;

    private TextView mTextLeft;
    private TextView mTextRight;
    private TextView mTextStand;

    private int color_green;
    private int color_red;
    private int color_white;

    private float x1 = 0;
    private float y1 = 0;
    private float z1 = 0;
    private float size = 0;
    private float gravity[];

    // Bluetooth interface variables
    private BluetoothSPPConnection mBluetoothSPPConnection;
    private BluetoothAdapter mBluetoothAdapter = null;
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private PowerManager.WakeLock wl;

    // Variables used to calculate mAcceleration given by the touch
    private int mHeight;
    private int mWidth;
    private int mYDelta;
    
    // Toggle Buttons
    private ToggleButton buttonRightSign;
    private ToggleButton buttonLeftSign;
    private ToggleButton buttonAlert;
    private ToggleButton buttonHighLights;
    private ToggleButton buttonLantern;

    // Buttons
    private Button button_scan;

    // Command Map Vector sent by bluetooth:
    private  byte command [];
    // [0] = 0 => stop                 // [3] = 0 => no sign
    // [0] = 1 => acc speed 1          // [3] = 1 => right sign
    // [0] = 2 => acc speed 2          // [3] = 2 => left sign
    // [0] = 3 => acc speed 3          // [4] = 1 => acc speed 1
    // [0] = 4 => rev speed 1          // [4] = 2 => acc speed 2
    // [0] = 5 => rev speed 2          // [4] = 3 => acc speed 3
    // [0] = 6 => rev speed 3          // [4] = -1 => rev speed 1
    // [1] = 0 => straight             // [4] = -2 => rev speed 2
    // [1] = 1 => left                 // [4] = -3 => rev speed 3
    // [1] = 2 => right
    // [2] = 0 => light off
    // [2] = 1 => light on
    // [2] = 2 => lantern

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_panel);

        // Initializing the gravity vector to zero.
        gravity = new float[3];
        gravity[0] = 0;
        gravity[1] = 0;
        gravity[2] = 0;

        // Initializing UI, Math, Sensor and filters
        initUI();
        initMaths();
        initSensors();
        initFilters();

        // Get the size of the screen
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mWidth = size.x /2;
        mHeight = size.y;

        command = new byte[4];
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If bluetooth is not activated, ask the user to activate
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        
         mBluetoothSPPConnection = new BluetoothSPPConnection(this);

         // Logic used in the Right Sign Button listener to not have more than one Sign button active at the same time
         buttonRightSign = (ToggleButton) findViewById(R.id.toggleButtonRightSign);
         buttonRightSign.setOnClickListener(new OnClickListener() {            
             public void onClick(View v) {
                 boolean on = ((ToggleButton) v).isChecked();
                 if(on){
                     command[3] = '1'; 
                     if(buttonLeftSign.isChecked()){
                         buttonLeftSign.setChecked(false);
                     }
                     if(buttonAlert.isChecked()){
                         buttonAlert.setChecked(false);
                     }
                     mBluetoothSPPConnection.write(command);
                 }else{
                     command[3] = '0'; 
                     mBluetoothSPPConnection.write(command);
                 }
             }
         });

        // Logic used in the Left Sign Button listener to not have more than one Sign button active at the same time
         buttonLeftSign = (ToggleButton) findViewById(R.id.toggleButtonLeftSign);
         buttonLeftSign.setOnClickListener(new OnClickListener() {            
             public void onClick(View v) {
                 boolean on = ((ToggleButton) v).isChecked();
                 if(on){                    
                     command[3] = '2'; 
                     if(buttonRightSign.isChecked()){
                         buttonRightSign.setChecked(false);
                     }
                     if(buttonAlert.isChecked()){
                         buttonAlert.setChecked(false);
                     }
                     mBluetoothSPPConnection.write(command);
                 }else{
                     command[3] = '0'; 
                     mBluetoothSPPConnection.write(command);
                 }
             }
         });

        // Logic used in the Alert Sign Button listener to not have more than one Sign button active at the same time
         buttonAlert = (ToggleButton) findViewById(R.id.toggleButtonAlert);
         buttonAlert.setOnClickListener(new OnClickListener() {            
             public void onClick(View v) {
                 boolean on = ((ToggleButton) v).isChecked();
                 if(on){
                     command[2] = '4';
                     if(buttonLeftSign.isChecked()){
                         buttonLeftSign.setChecked(false);
                     }
                     if(buttonRightSign.isChecked()){
                         buttonRightSign.setChecked(false);
                     }
                     mBluetoothSPPConnection.write(command);
                 }else{
                     command[2] = '0'; 
                     mBluetoothSPPConnection.write(command);
                 }
             }
         });

         // Register HighLight listener
         buttonHighLights = (ToggleButton) findViewById(R.id.toggleButtonHighLights);
         buttonHighLights.setOnClickListener(new OnClickListener() {            
             public void onClick(View v) {
                 boolean on = ((ToggleButton) v).isChecked();
                 if(on){
                     command[2] = '1'; 
                     mBluetoothSPPConnection.write(command);
                 }else{
                     command[2] = '0'; 
                     mBluetoothSPPConnection.write(command);
                 }
             }
         });

         //Register Lantern listener
         buttonLantern = (ToggleButton) findViewById(R.id.toggleButtonLantern);
         buttonLantern.setOnClickListener(new OnClickListener() {            
             public void onClick(View v) {
                 boolean on = ((ToggleButton) v).isChecked();
                 if(on){
                     command[2] = '2'; 
                     mBluetoothSPPConnection.write(command);
                 }else{
                     command[2] = '0'; 
                     mBluetoothSPPConnection.write(command);
                 }
             }
         });

        // Register bluetooth scan listener
        button_scan = (Button) findViewById(R.id.scan);
        button_scan.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                    Intent serverIntent = new Intent(v.getContext(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            }
        });
        
        
        // Getting a WakeLock. This insures that the phone does not sleep
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag");
        wl.acquire();

    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int)event.getX();
        int y = (int)event.getY();
        switch (event.getAction()) {

            // Touch event
            case MotionEvent.ACTION_DOWN:{
                mYDelta = y;
                if(x > mWidth){
                    //accelerate - right side of the screen
                    if (D) Log.d(TAG, "Acceleration touch pressed");
                    command[0] = '1';
                    printAccelerationSpeedLevel(1);
                    mBluetoothSPPConnection.write(command);
                }else{
                    //reverse - left side of the screen
                    if (D) Log.d(TAG, "Reversion touch released");
                    command[0] = '4';
                    printAccelerationSpeedLevel(1);
                    mBluetoothSPPConnection.write(command); 
                }
                break;
            }

            // Move event to calculate the acceleration level
            case MotionEvent.ACTION_MOVE:{
                // The movement is going down -> do nothing in this case
                if(y - mYDelta > 20){
                    if(x > mWidth){
                        if (D) Log.d(TAG, "Moving down acceleration - current Y axis - " + y + " - delta Y -" + mYDelta);
                    }else{
                        if (D) Log.d(TAG, "Moving down reversion - current Y axis - " + y + " - delta Y -" + mYDelta);
                    }
                }else if(y - mYDelta < -20 ){
                    if(x > mWidth){
                        if(y - mYDelta < -300){
                            if (D) Log.d(TAG, "Acceleration speed is 3 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '3';
                            printAccelerationSpeedLevel(3);
                            mBluetoothSPPConnection.write(command);
                        }
                        else if(y - mYDelta < -150){
                            if (D) Log.d(TAG, "Acceleration speed is 2 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '2';
                            printAccelerationSpeedLevel(2);
                            mBluetoothSPPConnection.write(command);
                        } else {
                            if (D) Log.d(TAG, "Acceleration speed is 1 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '1';
                            printAccelerationSpeedLevel(1);
                            mBluetoothSPPConnection.write(command);
                        }
                    } else{
                        if(y - mYDelta < -300){
                            if (D) Log.d(TAG, "Reversion speed is 3 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '6';
                            printAccelerationSpeedLevel(5);
                            mBluetoothSPPConnection.write(command);
                        }
                        else if(y - mYDelta < -150){
                            if (D) Log.d(TAG, "Reversion speed is 2 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '5';
                            printAccelerationSpeedLevel(4);
                            mBluetoothSPPConnection.write(command);
                        } else {
                            if (D) Log.d(TAG, "Reversion speed is 1 - current Y axis - " + y + " - Delta Y - " + mYDelta + " - (Y - DeltaY =" + (y-mYDelta));
                            command[0] = '4';
                            printAccelerationSpeedLevel(1);
                            mBluetoothSPPConnection.write(command);
                        }                                               
                    }
                }
                break;
            }

            // Release touch event
            case MotionEvent.ACTION_UP:{      
                if(x > mWidth){
                    if (D) Log.d(TAG, "Acceleration touch released");
                    command[0] = '0';
                    printAccelerationSpeedLevel(0);
                    mBluetoothSPPConnection.write(command);
                }else  {
                    if (D) Log.d(TAG, "Reversion touch released");
                    command[0] = '0';
                    printAccelerationSpeedLevel(0);
                    mBluetoothSPPConnection.write(command); 
                }
                break;
            }
            
        }
    return false;
    }

    /**
     * Method responsible for print the images of the speed according to the level passed
     * @param level - Integer that represents the level of the car
     *              1 = level 1 forward or reverse
     *              2 = level 2 forward  4 = level 2 reverse
     *              3 = level 3 forward  5 = level 3 reverse
     */
    private void printAccelerationSpeedLevel(int level){
        switch (level){
            case 1:{
                mImageViewAccLevel1.setVisibility(View.VISIBLE);
                mImageViewAccLevel2.setVisibility(View.INVISIBLE);
                mImageViewAccLevelr2.setVisibility(View.INVISIBLE);
                mImageViewAccLevel3.setVisibility(View.INVISIBLE);
                mImageViewAccLevelr3.setVisibility(View.INVISIBLE);
                break;
            }
            case 2:{
                mImageViewAccLevel1.setVisibility(View.VISIBLE);
                mImageViewAccLevel2.setVisibility(View.VISIBLE);
                mImageViewAccLevel3.setVisibility(View.INVISIBLE);
                break;
            }
            case 3:{
                mImageViewAccLevel3.setVisibility(View.VISIBLE);
                break;
            }
            case 4:{
                mImageViewAccLevel1.setVisibility(View.VISIBLE);
                mImageViewAccLevelr2.setVisibility(View.VISIBLE);
                mImageViewAccLevelr3.setVisibility(View.INVISIBLE);
                break;
            }
            case 5:{
                mImageViewAccLevelr3.setVisibility(View.VISIBLE);
                break;
            }
            default:{
                mImageViewAccLevel1.setVisibility(View.INVISIBLE);
                mImageViewAccLevel2.setVisibility(View.INVISIBLE);
                mImageViewAccLevelr2.setVisibility(View.INVISIBLE);
                mImageViewAccLevel3.setVisibility(View.INVISIBLE);
                mImageViewAccLevelr3.setVisibility(View.INVISIBLE);
            }
        }

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mBluetoothSPPConnection.open(device);
                    if (D) Log.d(TAG, "Open connection bluetooth address - " + address);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO - Add menu bar when add config activity
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.gyroscope, menu);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO - Add menu action buttons (reset button) when add config activity
        /* switch (item.getItemId()) {
        // Reset everything
            case R.id.action_reset:
                reset();
                restart();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }*/
        return true;
    }

    public void onResume() {

        super.onResume();
        restart();
    }

    public void onPause() {

        super.onPause();
        reset();
    }
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Close the bluetooth connection
        mBluetoothSPPConnection.close();
        
        // Release the WakeLock so that the phone can go to sleep to preserve battery.
        wl.release();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // event catch by accelerometer sensor
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            onAccelerationSensorChanged(event.values, event.timestamp);
        }

        // event catch by mMagnetic sensor
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            onMagneticSensorChanged(event.values, event.timestamp);
        }

    }

    /**
     * On accelerometer sensor changed
     * @param acceleration - X Y Z Axis of the event
     * @param timeStamp - timestamp of the event
     */
    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {

        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation) {
            // Get a local copy of the raw mMagnetic values from the device sensor.
            System.arraycopy(acceleration, 0, this.mAcceleration, 0, acceleration.length);

            // Use a mean filter to smooth the sensor inputs
            this.mAcceleration = mAccelerationFilter.filterFloat(this.mAcceleration);

            // Count the number of samples received.
            mAccelerationSampleCount++;

            // Only determine the initial orientation after the acceleration sensor
            // and mMagnetic sensor have had enough time to be smoothed by the mean
            // filters. Also, only do this if the orientation hasn't already been
            // determined since we only need it once.
            if (mAccelerationSampleCount > MIN_SAMPLE_COUNT && mMagneticSampleCount > MIN_SAMPLE_COUNT
                    && !hasInitialOrientation) {
                calculateOrientation();
            }
            return;
        }

        // Initialization of the accelerometer based on rotation matrix
        if (!stateInitializedCalibrated) {
            stateInitializedCalibrated = true;
        }

        // Raw values got from the accelerometer sensor
        float alpha = (float) 0.8;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * acceleration[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * acceleration[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * acceleration[2];

        // Normalize the gravity vector and rescale it so that every component fits one byte.
        size = (float) Math.sqrt(Math.pow(gravity[0], 2) + Math.pow(gravity[1], 2) + Math.pow(gravity[2], 2));
        x1 = (128 * gravity[0] / size) / 100;
        y1 = (128 * gravity[1] / size) / 100;
        z1 = (128 * gravity[2] / size) / 100;

        // Setting Orientation calibrated Axis values of gyroscope to draw the gauge in 2D
        gyroscopeOrientationCalibrated[1] = y1;
        gyroscopeOrientationCalibrated[0] = 0;
        gyroscopeOrientationCalibrated[2] = gyroscopeOrientationCalibrated[1];
        gyroscopeOrientationCalibrated[1] = 0;

        // Update gauge
        mGaugeTiltCalibrated.updateRotation(gyroscopeOrientationCalibrated);

        // Controlling Z Axis
        if (z1 < 1.05) { // Limit in Z Axis to make the accelerometer works as expected in this position
            mTextStand.setVisibility(View.INVISIBLE);
            mImageViewStandWarning.setVisibility(View.INVISIBLE);
        } else {
            mImageViewStandWarning.setVisibility(View.VISIBLE);
        }

        // Controlling Y Axis
        if (y1 < -0.50) { // Bellow this limit in Y Axis indicates to turn left
            if (DEBUG_DIRECTION) Log.d(TAG, "Turning Left event");
            command[1] = '1';
            mTextRight.setTextColor(color_green);
            mBluetoothSPPConnection.write(command);

        } else if (y1 > 0.50) { // Above this limit in Y Axis indicates to turn right
            if (DEBUG_DIRECTION) Log.d(TAG, "Turning right event");
            command[1] = '2';
            mTextLeft.setTextColor(color_green);
            mBluetoothSPPConnection.write(command);

        } else {
            if (DEBUG_DIRECTION) Log.d(TAG, "Straight ahead event");
            command[1] = '0';
            mTextLeft.setTextColor(color_white);
            mTextRight.setTextColor(color_white);
            mBluetoothSPPConnection.write(command);
        }

        // Formatting Axis values to be printed
        mXAxisCalibrated.setText(df.format(x1));
        mYAxisCalibrated.setText(df.format(y1));
        mZAxisCalibrated.setText(df.format(z1));

    }

    /**
     * On magnetic sensor changed
     * @param magnetic - X Y Z Axis of the event
     * @param timeStamp - timestamp of the event
     */
    public void onMagneticSensorChanged(float[] magnetic, long timeStamp) {

        // Get a local copy of the raw mMagnetic values from the device sensor.
        System.arraycopy(magnetic, 0, this.mMagnetic, 0, magnetic.length);

        // Use a mean filter to smooth the sensor inputs
        this.mMagnetic = mMagneticFilter.filterFloat(this.mMagnetic);

        // Count the number of samples received.
        mMagneticSampleCount++;
    }

    /**
     * Calculates orientation angles from accelerometer and magnetometer output. Note that we only use this
     * *once* at the beginning to orient the gyroscope to earth frame.
     */
    private void calculateOrientation() {

        hasInitialOrientation = SensorManager.getRotationMatrix(mInitialRotationMatrix, null, mAcceleration,
                mMagnetic);
        // Remove the magnetic sensor observers since they are no longer required.
        if (hasInitialOrientation) {
            mSensorManager
                    .unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        }
    }

    /**
     * Initialize the mean filters.
     */
    private void initFilters() {

        mAccelerationFilter = new MeanFilter();
        mAccelerationFilter.setWindowSize(MEAN_FILTER_WINDOW);

        mMagneticFilter = new MeanFilter();
        mMagneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }

    /**
     * Initialize the data structures required for the maths.
     */
    private void initMaths() {

        mAcceleration = new float[3];
        mMagnetic = new float[3];

        mInitialRotationMatrix = new float[9];

        currentRotationMatrixCalibrated = new float[9];
        gyroscopeOrientationCalibrated = new float[3];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixCalibrated[0] = 1.0f;
        currentRotationMatrixCalibrated[4] = 1.0f;
        currentRotationMatrixCalibrated[8] = 1.0f;

        mCurrentRotationMatrixRaw = new float[9];

        // Initialize the current rotation matrix as an identity matrix...
        mCurrentRotationMatrixRaw[0] = 1.0f;
        mCurrentRotationMatrixRaw[4] = 1.0f;
        mCurrentRotationMatrixRaw[8] = 1.0f;
    }

    /**
     * Initialize the sensors.
     */
    private void initSensors() {

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Initialize the UI.
     */
    private void initUI() {

        // Get a decimal formatter for the text views
        df = new DecimalFormat("#.##");

        // Initialize the images and text views
        mXAxisCalibrated = (TextView) this.findViewById(R.id.value_x_axis_calibrated);
        mYAxisCalibrated = (TextView) this.findViewById(R.id.value_y_axis_calibrated);
        mZAxisCalibrated = (TextView) this.findViewById(R.id.value_z_axis_calibrated);

        mTextLeft = (TextView) this.findViewById(R.id.label_right);
        mTextRight = (TextView) this.findViewById(R.id.label_left);
        mTextStand = (TextView) this.findViewById(R.id.label_stand_position);

        mImageViewAccLevel1 = (ImageView) this.findViewById(R.id.imageView_level1);
        mImageViewAccLevel2 = (ImageView) this.findViewById(R.id.imageView_level2);
        mImageViewAccLevel3 = (ImageView) this.findViewById(R.id.imageView_level3);
        mImageViewAccLevelr2 = (ImageView) this.findViewById(R.id.imageView_levelr2);
        mImageViewAccLevelr3 = (ImageView) this.findViewById(R.id.imageView_levelr3);

        mImageViewStandWarning = (ImageView) this.findViewById(R.id.imageView_stand_position);

        color_green = getResources().getColor(R.color.light_green);
        color_red = getResources().getColor(R.color.light_red);        
        color_white = getResources().getColor(R.color.white);

        mGaugeTiltCalibrated = (GaugeRotation) findViewById(R.id.gauge_tilt_calibrated);
    }

    /**
     * Restarts all of the sensor observers and resets the activity to the initial state. This should only be
     * called *after* a call to reset().
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void restart() {

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    /**
     * Removes all of the sensor observers and resets the activity to the initial state.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void reset() {

        mSensorManager.unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));

        mSensorManager.unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

        initMaths();

        mAccelerationSampleCount = 0;
        mMagneticSampleCount = 0;

        hasInitialOrientation = false;
        stateInitializedCalibrated = false;

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

        // TODO Auto-generated method stub

    }

    /**
     * Method called in the moment that the phone starts to connecting with the bluetooth module.
     */
    public void onConnecting() {
        if (D) Log.d(TAG, "Connecting...");

        // Change the text in the connectionInfo TextView       
        mTextStand.setText("Connecting...");
        mTextStand.setVisibility(View.VISIBLE);
    }

    /**
     * Method called in the moment a connection is realized between the phone and the bluetooth module.
     */
    @SuppressLint("NewApi")
    public void onConnected() {
        if (D) Log.d(TAG, "Connected");

        // Change the text in the connectionInfo TextView
        mTextStand.setText("Connected to " + mBluetoothSPPConnection.getDeviceName());
        mTextStand.setTextColor(color_green);
        mTextStand.setVisibility(View.VISIBLE);
        
        button_scan.setBackground(getResources().getDrawable(R.drawable.btscan_green));
    }

    /**
     * Method called when the intended connection could not be realized.
     */
    public void onConnectionFailed() {
        if (D) Log.d(TAG, "Connection fail");

        // Change the text in the connectionInfo TextView        
        mTextStand.setText("Connection failed!");
        mTextStand.setTextColor(color_red);
        mTextStand.setVisibility(View.VISIBLE);
    }

    /**
     * Method called when the intended connection has been lost by some reason (e.g. the signal is lost when the car go to far)
     */
    public void onConnectionLost() {
        if (D) Log.d(TAG, "Connect lost");

        // Change the text in the connectionInfo TextView
        mTextStand.setText("Not Connected!");
        mTextStand.setTextColor(color_red);
        mTextStand.setVisibility(View.VISIBLE);
    }


    //useless ? - implemented but not used

    public void bluetoothWrite(int bytes, byte[] buffer) {
        /*
        // This function is called when the bluetooth module sends data to the android device.
        // The function is executed in the main thread.

        // Normalize the gravity vector and rescaled it so that every component fits one byte.
     //   float size=(float) Math.sqrt(Math.pow(gravity[0], 2)+Math.pow(gravity[1], 2)+Math.pow(gravity[2], 2));
    //    byte x = (byte) (128*gravity[0]/size);
     //   byte y = (byte) (128*gravity[1]/size);
     //   byte z = (byte) (128*gravity[2]/size);

        // If we are in driving mode, send the 'c' character followed the gravity components.
        if (true) {

        //  command[0]=1;
        //  command[1]=0;
        //  command[2]=y;
         // command[3]=z;

          command[0]='z';
          command[1]='x';
          mBluetoothSPPConnection.write(command);

        // If we are not in driving mode, send the 's' character.
        } else {
          byte[] command = new byte[1];
          command[0]='s';
          mBluetoothSPPConnection.write(command);
        }
       */
    }

}
