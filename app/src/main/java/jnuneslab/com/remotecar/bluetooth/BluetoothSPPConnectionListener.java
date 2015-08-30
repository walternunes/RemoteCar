package jnuneslab.com.remotecar.bluetooth;

public interface BluetoothSPPConnectionListener {
    public void bluetoothWrite(int bytes, byte[] buffer);
    public void onConnecting();
    public void onConnected();
    public void onConnectionFailed();
    public void onConnectionLost();
}