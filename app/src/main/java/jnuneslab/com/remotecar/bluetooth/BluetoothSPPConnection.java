package jnuneslab.com.remotecar.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class BluetoothSPPConnection {
    
    private BluetoothDevice mDevice;    
    private final BluetoothAdapter mAdapter;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    
    private ListenerDelegate mListener;


    public BluetoothSPPConnection(BluetoothSPPConnectionListener btListener) {  
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mListener = new ListenerDelegate(btListener, null);
    }

    
    public String getDeviceName() {
        return mDevice.getName();
    }

    public synchronized void open(BluetoothDevice device) {

        mDevice = device;

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        
        mListener.onConnecting();
    }

    public synchronized void close() {

        if (mConnectThread != null) {
            mConnectThread.cancel(); 
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel(); 
            mConnectedThread = null;
        }

    }

    public void write(byte[] out) {
        if (mConnectedThread != null) {
            mConnectedThread.write(out);
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                
                /** UIID here**/
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) { System.out.println(">>>>< ioexception");}
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    System.out.println(">>>> fail");
                    mListener.onConnectionFailed();
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }
            

            // Do work to manage the connection (in a separate thread)
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
            
            mListener.onConnected();

      }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    
    
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    mListener.bluetoothWrite(bytes,buffer);
                } catch (IOException e) {
                    mListener.onConnectionLost();
                    break;
                }
            }
        }

        /* Call this from the main Activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main Activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    
    private class ListenerDelegate extends Binder {

        public static final int MESSAGE_CONNECTING = 1;
        public static final int MESSAGE_CONNECTED = 2;
        public static final int MESSAGE_CONNECTION_LOST = 3;
        public static final int MESSAGE_CONNECTION_FAILED = 4;
        public static final int MESSAGE_WRITE = 5;
        
        final BluetoothSPPConnectionListener mBluetoothSerialEventListener;
        private final Handler mHandler;

        ListenerDelegate(BluetoothSPPConnectionListener listener, Handler handler) {
            mBluetoothSerialEventListener = listener;
            Looper looper = (handler != null) ? handler.getLooper() : Looper.getMainLooper();

            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                    case MESSAGE_CONNECTING:
                        mBluetoothSerialEventListener.onConnecting();
                        break;
                    case MESSAGE_CONNECTED:
                        mBluetoothSerialEventListener.onConnected();
                        break;
                    case MESSAGE_CONNECTION_LOST:
                        mBluetoothSerialEventListener.onConnectionLost();
                        break;
                    case MESSAGE_CONNECTION_FAILED:
                        mBluetoothSerialEventListener.onConnectionFailed();
                        break;
                    case MESSAGE_WRITE:
                        byte[] buffer = (byte[]) msg.obj;
                        int bytes = msg.arg1;
                        mBluetoothSerialEventListener.bluetoothWrite(bytes, buffer);
                        break;
                    }
                }
            };

        }

        void bluetoothWrite(int bytes, byte[] buffer) {
            mHandler.obtainMessage(MESSAGE_WRITE, bytes, -1, buffer).sendToTarget();
        }
        void onConnecting() {
            mHandler.obtainMessage(MESSAGE_CONNECTING).sendToTarget();
        }
        void onConnected(){
            mHandler.obtainMessage(MESSAGE_CONNECTED).sendToTarget();
        }
        void onConnectionFailed() {
            mHandler.obtainMessage(MESSAGE_CONNECTION_FAILED).sendToTarget();
        }
        void onConnectionLost() {
            mHandler.obtainMessage(MESSAGE_CONNECTION_LOST).sendToTarget();
        }
        
    }

}
