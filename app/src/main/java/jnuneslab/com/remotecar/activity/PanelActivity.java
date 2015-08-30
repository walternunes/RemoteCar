package jnuneslab.com.remotecar.activity;

import java.text.DecimalFormat;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.view.View.OnClickListener;

import jnuneslab.com.remotecar.R;
import jnuneslab.com.remotecar.draw.filter.MeanFilter;
import jnuneslab.com.remotecar.draw.gauge.GaugeRotation;
import jnuneslab.com.remotecar.bluetooth.BluetoothSPPConnection;
import jnuneslab.com.remotecar.bluetooth.BluetoothSPPConnectionListener;


public class PanelActivity extends Activity implements SensorEventListener, BluetoothSPPConnectionListener

{

    public static final float EPSILON = 0.000000001f;

    private static final String tag = PanelActivity.class.getSimpleName();

    private static final int MEAN_FILTER_WINDOW = 10;
    private static final int MIN_SAMPLE_COUNT = 30;

    private boolean hasInitialOrientation = false;
    private boolean stateInitializedCalibrated = false;

    private boolean useFusedEstimation = false;
    private boolean useRadianUnits = false;

    private GaugeRotation gaugeTiltCalibrated;

    private DecimalFormat df;

    // Calibrated maths.
    private float[] currentRotationMatrixCalibrated;
    private float[] gyroscopeOrientationCalibrated;

    // Uncalibrated maths
    private float[] currentRotationMatrixRaw;

    // accelerometer and magnetometer based rotation matrix
    private float[] initialRotationMatrix;

    // accelerometer vector
    private float[] acceleration;

    // magnetic field vector
    private float[] magnetic;

    // private FusedGyroscopeSensor fusedGyroscopeSensor;

    private int accelerationSampleCount = 0;
    private int magneticSampleCount = 0;

    private MeanFilter accelerationFilter;
    private MeanFilter magneticFilter;

    // We need the SensorManager to register for Sensor Events.
    private SensorManager sensorManager;

    private TextView xAxisCalibrated;
    private TextView yAxisCalibrated;
    private TextView zAxisCalibrated;

    private TextView text_right;
    private TextView text_left;
    private TextView text_stand;

    private int color_green;
    private int color_red;
    private int color_white;

    private float x1 = 0;
    private float y1 = 0;
    private float z1 = 0;
    private float size = 0;
    private float gravity[];
    
    private BluetoothSPPConnection mBluetoothSPPConnection;
    private BluetoothAdapter mBluetoothAdapter = null;
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    private  byte command []; 
                              // [0] = 0 => stop
                              // [0] = 1 => acc speed 1
                              // [0] = 2 => acc speed 2
                              // [0] = 3 => acc speed 3
                              // [0] = 4 => rev speed 1
                              // [0] = 5 => rev speed 2
                              // [0] = 6 => rev speed 3
                              // [1] = 0 => straight
                              // [1] = 1 => left
                              // [1] = 2 => right
                              // [2] = 0 => light off
                              // [2] = 1 => light on
                              // [2] = 2 => lantern **
                              // [3] = 0 => no sign
                              // [3] = 1 => right sign
                              // [3] = 2 => left sign
                              // [4] = 1 => acc speed 1
                              // [4] = 2 => acc speed 2
                              // [4] = 3 => acc speed 3
                              // [4] = -1 => rev speed 1
                              // [4] = -2 => rev speed 2
                              // [4] = -3 => rev speed 3

    private PowerManager.WakeLock wl;
    
    private int height;
    private int width;
    private int yDelta;
    
    // Toogle Buttons
    private ToggleButton buttonRightSign;
    private ToggleButton buttonLeftSign;
    private ToggleButton buttonAlert;
    private ToggleButton buttonHighLights;
    private ToggleButton buttonLantern;

    // Button
    private Button button_scan;
    
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
        initUI();
        initMaths();
        initSensors();
        initFilters();
        
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x /2;
        height = size.y;

        command = new byte[4];
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If bluetooth is not activated, ask the user to activate
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        
         mBluetoothSPPConnection = new BluetoothSPPConnection(this); // Registers the   
        
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
        
         
        // Initializing the "connect" button.
        // Register this an the OnClickListener.
        button_scan = (Button) findViewById(R.id.scan);
       // bt.setText("Connect");
        button_scan.setOnClickListener(new OnClickListener() {
            
            public void onClick(View v) {
                // When not connected, start the DeviceListActivity that allows the user to select a device.
                // The activity will return a bluetoothdevice and on that return, the onActivityResult() (see below)
                // member function is called.
               // if (!connected) {
                if(true){
                    Intent serverIntent = new Intent(v.getContext(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                // When connected, close the bluetooth connection.
                } else {
                    mBluetoothSPPConnection.close();
                }
            }
        });
        
        
     // Getting a WakeLock. This insures that the phone does not sleep
        // while driving the robot.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag");
        wl.acquire();

    };
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int)event.getX();
        int y = (int)event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:{
                yDelta = y;
                if(x > width){
                    //acelera
                    command[0] = '1'; 
                    System.out.println("<><> press acc" );
                    mBluetoothSPPConnection.write(command);
                }else{
                  //reverse
                    command[0] = '4'; 
                    System.out.println("<><> press rev" );
                    mBluetoothSPPConnection.write(command); 
                }
            //    System.out.println("<><>" + x + "y" + y + " size - " + width + " h " + height);
                break;
            }
                
            case MotionEvent.ACTION_MOVE:{
                if(y - yDelta > 20){
                    if(x > width){         
                        System.out.println("<><> moving down acc" + y + "delta" +  yDelta);
                    } else System.out.println("<><> moving down rev" + y + "delta" +  yDelta);
                }else if(y - yDelta < -20 ){
                    if(x > width){                        
                        if(y - yDelta < -120){
                            command[0] = '3'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed2 acc" + y + "delta" +  yDelta+ " = " + (y - yDelta));
                        }
                        else if(y - yDelta < -60){
                            command[0] = '2'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed1 acc" + y + "delta" +  yDelta + " = " + (y - yDelta));
                        } else {
                            command[0] = '1'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed0 acc" + y + "delta" +  yDelta+ " = " + (y - yDelta));
                        }
                    } else{
                        if(y - yDelta < -120){
                            command[0] = '6'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed2 rev" + y + "delta" +  yDelta+ " = " + (y - yDelta));
                        }
                        else if(y - yDelta < -60){
                            command[0] = '5'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed1 rev" + y + "delta" +  yDelta + " = " + (y - yDelta));
                        } else {
                            command[0] = '4'; 
                            mBluetoothSPPConnection.write(command);
                            System.out.println("<><> moving up speed0 rev" + y + "delta" +  yDelta+ " = " + (y - yDelta));
                        }                                               
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:{      
                if(x > width){
                    command[0] = '0'; 

                    System.out.println("<><> release acc" );
                    mBluetoothSPPConnection.write(command);
                }else  {
                    command[0] = '0'; 

                    System.out.println("<><> release rev" );
                    mBluetoothSPPConnection.write(command); 
                }
                break;
            }
            
        }
    return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mBluetoothSPPConnection.open(device);
                    System.out.println(">>>><><>" + address);
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

        // Inflate the menu; this adds items to the action bar if it is present.
      //  getMenuInflater().inflate(R.menu.gyroscope, menu);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

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

        readPrefs();

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

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            onAccelerationSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            onMagneticSensorChanged(event.values, event.timestamp);
        }

    }

    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {

        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation) {
            // Get a local copy of the raw magnetic values from the device sensor.
            System.arraycopy(acceleration, 0, this.acceleration, 0, acceleration.length);

            // Use a mean filter to smooth the sensor inputs
            this.acceleration = accelerationFilter.filterFloat(this.acceleration);

            // Count the number of samples received.
            accelerationSampleCount++;

            // Only determine the initial orientation after the acceleration sensor
            // and magnetic sensor have had enough time to be smoothed by the mean
            // filters. Also, only do this if the orientation hasn't already been
            // determined since we only need it once.
            if (accelerationSampleCount > MIN_SAMPLE_COUNT && magneticSampleCount > MIN_SAMPLE_COUNT
                    && !hasInitialOrientation) {
                calculateOrientation();
            }

            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedCalibrated) {

            stateInitializedCalibrated = true;

        }

        float alpha = (float) 0.8;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * acceleration[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * acceleration[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * acceleration[2];

        // Normalize the gravity vector and rescale it so that every component fits one byte.
        size = (float) Math.sqrt(Math.pow(gravity[0], 2) + Math.pow(gravity[1], 2) + Math.pow(gravity[2], 2));
        x1 = (128 * gravity[0] / size) / 100;
        y1 = (128 * gravity[1] / size) / 100;
        z1 = (128 * gravity[2] / size) / 100;

        gyroscopeOrientationCalibrated[1] = y1;
        gyroscopeOrientationCalibrated[0] = 0;
        gyroscopeOrientationCalibrated[2] = gyroscopeOrientationCalibrated[1];
        gyroscopeOrientationCalibrated[1] = 0;

        gaugeTiltCalibrated.updateRotation(gyroscopeOrientationCalibrated);

        if (z1 < 0.95) {
            text_stand.setVisibility(View.INVISIBLE);
        } else {
            text_stand.setText(getResources().getString(R.string.stand_position));
            text_stand.setTextColor(color_red);            
            text_stand.setVisibility(View.VISIBLE);
        }

        if (y1 < -0.50) {
            command[1] = '1';
 
            text_left.setTextColor(color_green);
            mBluetoothSPPConnection.write(command);

        } else if (y1 > 0.50) {
            command[1] = '2';
 
            text_right.setTextColor(color_green);
            mBluetoothSPPConnection.write(command);

        } else {
            command[1] = '0';
 
            text_right.setTextColor(color_white);
            text_left.setTextColor(color_white);
            mBluetoothSPPConnection.write(command);
        }

        xAxisCalibrated.setText(df.format(x1));
        yAxisCalibrated.setText(df.format(y1));
        zAxisCalibrated.setText(df.format(z1));

    }

    public void onMagneticSensorChanged(float[] magnetic, long timeStamp) {

        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);

        // Use a mean filter to smooth the sensor inputs
        this.magnetic = magneticFilter.filterFloat(this.magnetic);

        // Count the number of samples received.
        magneticSampleCount++;
    }

    /**
     * Calculates orientation angles from accelerometer and magnetometer output. Note that we only use this
     * *once* at the beginning to orient the gyroscope to earth frame. If you do not call this, the gyroscope
     * will orient itself to whatever the relative orientation the device is in at the time of initialization.
     */
    private void calculateOrientation() {

        hasInitialOrientation = SensorManager.getRotationMatrix(initialRotationMatrix, null, acceleration,
                magnetic);
        // Remove the sensor observers since they are no longer required.
        if (hasInitialOrientation) {
            // sensorManager.unregisterListener(this,
            // sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensorManager
                    .unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        }
    }

    /**
     * Initialize the mean filters.
     */
    private void initFilters() {

        accelerationFilter = new MeanFilter();
        accelerationFilter.setWindowSize(MEAN_FILTER_WINDOW);

        magneticFilter = new MeanFilter();
        magneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }

    /**
     * Initialize the data structures required for the maths.
     */
    private void initMaths() {

        acceleration = new float[3];
        magnetic = new float[3];

        initialRotationMatrix = new float[9];

        currentRotationMatrixCalibrated = new float[9];
        gyroscopeOrientationCalibrated = new float[3];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixCalibrated[0] = 1.0f;
        currentRotationMatrixCalibrated[4] = 1.0f;
        currentRotationMatrixCalibrated[8] = 1.0f;

        currentRotationMatrixRaw = new float[9];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixRaw[0] = 1.0f;
        currentRotationMatrixRaw[4] = 1.0f;
        currentRotationMatrixRaw[8] = 1.0f;
    }

    /**
     * Initialize the sensors.
     */
    private void initSensors() {

        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        // fusedGyroscopeSensor = new FusedGyroscopeSensor();
    }

    /**
     * Initialize the UI.
     */
    private void initUI() {

        // Get a decimal formatter for the text views
        df = new DecimalFormat("#.##");

        // Initialize the raw (uncalibrated) text views
        // xAxisRaw = (TextView) this.findViewById(R.id.value_x_axis_raw);
        // yAxisRaw = (TextView) this.findViewById(R.id.value_y_axis_raw);
        // zAxisRaw = (TextView) this.findViewById(R.id.value_z_axis_raw);

        // Initialize the calibrated text views
        xAxisCalibrated = (TextView) this.findViewById(R.id.value_x_axis_calibrated);
        yAxisCalibrated = (TextView) this.findViewById(R.id.value_y_axis_calibrated);
        zAxisCalibrated = (TextView) this.findViewById(R.id.value_z_axis_calibrated);

        text_right = (TextView) this.findViewById(R.id.label_right);
        text_left = (TextView) this.findViewById(R.id.label_left);
        text_stand = (TextView) this.findViewById(R.id.label_stand_position);

        color_green = getResources().getColor(R.color.light_green);
        color_red = getResources().getColor(R.color.light_red);        
        color_white = getResources().getColor(R.color.white);

        gaugeTiltCalibrated = (GaugeRotation) findViewById(R.id.gauge_tilt_calibrated);
    }

    /**
     * Restarts all of the sensor observers and resets the activity to the initial state. This should only be
     * called *after* a call to reset().
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void restart() {

        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    /**
     * Removes all of the sensor observers and resets the activity to the initial state.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void reset() {

        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));

        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

        initMaths();

        accelerationSampleCount = 0;
        magneticSampleCount = 0;

        hasInitialOrientation = false;
        stateInitializedCalibrated = false;

    }

    private void readPrefs() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // useFusedEstimation = prefs.getBoolean(ConfigActivity.FUSION_PREFERENCE,
        // false);

        // useRadianUnits = prefs
        // .getBoolean(ConfigActivity.UNITS_PREFERENCE, true);

        useRadianUnits = true;

        Log.d(tag, "Fusion: " + String.valueOf(useFusedEstimation));

        Log.d(tag, "Units Radians: " + String.valueOf(useRadianUnits));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

        // TODO Auto-generated method stub

    }
 
    public void onConnecting() {
        // This function is called on the moment the phone starts making a connecting with the bluetooth module.
        // The function is executed in the main thread.

        // Change the text in the connectionInfo TextView       
        text_stand.setText("Connecting...");
        text_stand.setVisibility(View.VISIBLE);
        

        System.out.println(">>>>>>>>> connecting");
    }

    @SuppressLint("NewApi")
    public void onConnected() {
        // This function is called on the moment a connection is realized between the phone and the bluetooth module.
        // The function is executed in the main thread.

       // connected = true;

        // Change the text in the connectionInfo TextView
       
        text_stand.setText("Connected to "+mBluetoothSPPConnection.getDeviceName());
        text_stand.setTextColor(color_green);
        text_stand.setVisibility(View.VISIBLE);
        
        button_scan.setBackground(getResources().getDrawable(R.drawable.btscan_green));
        // Change the text in the connect button.
        //Button bt = (Button) findViewById(R.id.connect);
        //bt.setText("Disconnect");

        System.out.println(">>>>>>>>> connected");
        // Send the 's' character so that the communication can start.
       // byte[] command = new byte[1];
        //command[0]='f';
        //mBluetoothSPPConnection.write(command);
    }

    public void onConnectionFailed() {
        // This function is called when the intended connection could not be realized.
        // The function is executed in the main thread.

       // connected = false;

        System.out.println(">>>>>>>>> connect fail");
        // Change the text in the connectionInfo TextView        
        text_stand.setText("Connection failed!");
        text_stand.setTextColor(color_red);
        text_stand.setVisibility(View.VISIBLE);
        // Change the text in the connect button.
      //  Button bt = (Button) findViewById(R.id.connect);
      //  bt.setText("Connect");
    }

    public void onConnectionLost() {
        // This function is called when the intended connection could not be realized.
        // The function is executed in the main thread.
        
        //connected = false;
        System.out.println(">>>>>>>>> connect lost");
        // Change the text in the connectionInfo TextView      
        text_stand.setText("Not Connected!");
        text_stand.setTextColor(color_red);
        text_stand.setVisibility(View.VISIBLE);
        // Change the text in the connect button.
        //Button bt = (Button) findViewById(R.id.connect);
        //bt.setText("Connect");
    }

    
    //useless ?
    public void bluetoothWrite(int bytes, byte[] buffer) {
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
    }
}
