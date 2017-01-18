package testing.gotl.spyspyyo.bluetoothtesting.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import testing.gotl.spyspyyo.bluetoothtesting.activities.BluetoothActivity;
import testing.gotl.spyspyyo.bluetoothtesting.teststuff.GameInformation;
import testing.gotl.spyspyyo.bluetoothtesting.teststuff.GameInformationList;

import static android.support.v4.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static testing.gotl.spyspyyo.bluetoothtesting.teststuff.TODS.APP_IDENTIFIER;
import static testing.gotl.spyspyyo.bluetoothtesting.teststuff.TODS.ASK_TO_TURN_ON_BT;
import static testing.gotl.spyspyyo.bluetoothtesting.teststuff.TODS.GAME_NAME;
import static testing.gotl.spyspyyo.bluetoothtesting.teststuff.TODS.REQUEST_COARSE_LOCATION_PERMISSION;

//todo:turn off when the app is left?

public class AppBluetoothManager {

    private static final String NOT_HOSTING_STRING = "\n";

    private static final int
            REQUEST_START_DISCOVERABLE = 0,
            REQUEST_ENABLE_BT = 1;

    //the status options
    private static final byte
            NONE = 0,
            CLIENT = 1,
            SEARCH = 2,
            SERVER = 3;

    private static byte status = NONE;

    private static boolean
            bluetoothOnRequested = false,
            bluetoothDiscoverableRequested = false;

    private static String localAddress = "";

    private static BluetoothAdapter bluetoothAdapter = null;
    private static BluetoothBroadcastReceiver bluetoothBroadcastReceiver = null;
    private static GameInformationList gameList;

    /**
     * Called when an Activity uses Bluetooth
     * @see BluetoothActivity
     * @param context - context of the application calling this
     */
    public static void initialize(Context context){
        if (bluetoothAdapter != null)return;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null){
            handleNonBluetoothDevice(context);
            return;
        }
        localAddress = Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
        bluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
    }

    //----------------------------------------------------------------------------------------------
    //
    //          START OF PUBLIC ACCESS METHODS
    //
    //----------------------------------------------------------------------------------------------

    public static void releaseRequirements(Context context){
        bluetoothBroadcastReceiver.unregister(context);
        bluetoothAdapter.cancelDiscovery();

        ConnectionManager.stopServer();
        ConnectionManager.disconnect();

        status = NONE;
    }

    public static void startServer(Activity activity, GameInformation gameInformation){
        changeStatus(SERVER);
        bluetoothBroadcastReceiver.register(activity);
        //start the reception threads
        ConnectionManager.startServer();
        if(bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE && ! bluetoothDiscoverableRequested) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            activity.startActivityForResult(intent, REQUEST_START_DISCOVERABLE);
            bluetoothDiscoverableRequested = true;
        }else {
            setBluetoothName();
            bluetoothAdapter.cancelDiscovery();
            Log.i("BtTest", "bluetooth server available");
        }
    }

    public static void stopServer(){
        changeStatus(CLIENT);
        setBluetoothName();
        ConnectionManager.stopServer();
        //todo:turn off discoverable with 1s intent if user wants it
    }

    public static void searchGames(Activity activity, GameInformationList list){
        if (!assureCoarseLocationPermission(activity)){
            Log.w("APManager", "ain't got no coarse location permission");
            return;
        }

        changeStatus(SEARCH);
        if (prepareClient(activity))startSearch();

        gameList = list;
        gameList.clear();
    }

    public static void joinGame(String gameAddress, @Nullable ConnectionListener listener){
        ConnectionManager.connect(getBluetoothDevice(gameAddress), listener);
    }


    //----------------------------------------------------------------------------------------------
    //
    //          INTERN METHODS
    //
    //----------------------------------------------------------------------------------------------

    private static void changeStatus(byte newStatus){
        status = newStatus;
        setBluetoothName();
        //todo:notify listeners
    }

    private static void startSearch(){
        if (bluetoothAdapter.isDiscovering())bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();
        Log.i("BtTest", "searching for Hosts");
    }

    private static boolean prepareClient(Activity activity) {
        bluetoothBroadcastReceiver.register(activity);
        if (bluetoothAdapter.isEnabled()){
            setBluetoothName();
            Log.i("BtTest", "bluetooth client available");
            return true;
        }
        else if (!bluetoothOnRequested) {
            if (ASK_TO_TURN_ON_BT){
                activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
                bluetoothOnRequested = true;
            }else{
                bluetoothAdapter.enable();
            }
        }
        return false;
    }

    private static void setBluetoothName(){
        String bluetoothName = APP_IDENTIFIER + ((status == SERVER)?GAME_NAME:NOT_HOSTING_STRING);
        if (!bluetoothAdapter.isEnabled() || bluetoothAdapter.getName().equals(bluetoothName))return;
        bluetoothAdapter.setName(bluetoothName);
    }

    private static BluetoothDevice getBluetoothDevice(String address) {
        BluetoothDevice bluetoothDevice = null;
        if (BluetoothAdapter.checkBluetoothAddress(address))
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        else Log.w("ABManager", "received an invalid address");
        return bluetoothDevice;
    }

    /*package*/ static String getLocalAddress(){
        return localAddress;
    }

    /*package*/ static BluetoothServerSocket getBluetoothServerSocket(UUID uuid) throws IOException{
        return bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_IDENTIFIER, uuid);
    }

    private static boolean assureCoarseLocationPermission(Activity activity) {
        int permissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(activity)
                        .setTitle("Coarse Location")
                        .setMessage("Dunno why but need that to find games")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                }
                        ).show();
            }
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION_PERMISSION);
            return false;
        }
        return true;
    }

    private static void handleNonBluetoothDevice(final Context context){
        Log.w("APManager", "Device does not support bluetooth");
        new AlertDialog.Builder(context)
                .setTitle("Bluetooth?")
                .setMessage("Unfortunately your device seems to have no bluetooth. In a nutshell this app is pretty useless for you.")
                .setPositiveButton("Ok :(", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent, null);
                    }
                })
                .show();
    }

    private static class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private static final int INVALID_STATE = -1;

        private static String[] FILTER_ACTIONS = {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_DISCOVERY_STARTED,
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED,
                BluetoothDevice.ACTION_FOUND
        };

        private static final IntentFilter filter = new IntentFilter();
        static {
            for (String action:FILTER_ACTIONS) {
                filter.addAction(action);
            }
        }

        private static boolean registered = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch(action){
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    onStateChanged(intent);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    onDiscoveryStart();
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    onDiscoveryFinish();
                    break;
                case BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED:
                    onNameChange();
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    onDeviceFound(intent);
                    break;
                default:
                    Log.w("BtTest", "Received an unidentifiable Intent");
            }
        }

        private void onStateChanged(Intent intent){
            int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, INVALID_STATE);
            switch (extra){
                case BluetoothAdapter.STATE_ON:
                    setBluetoothName();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    break;
            }
        }

        private void onDiscoveryStart(){
            Log.i("BtTest", "starting discovery");
        }

        private void onDiscoveryFinish(){
            Log.i("BtTest", "discovery finished");
            //todo:notify the searcher
        }

        private void onNameChange(){
            Log.i("BtTest", "name was changed to" + bluetoothAdapter.getName());
            setBluetoothName();
        }

        private void onDeviceFound(Intent intent){
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null){
                Log.w("BtTest", "Received invalid device found intent");
                return;
            }

            String deviceName = device.getName();
            if (deviceName == null){
                Log.d("BtTest", "Found a Device with invalid name");
                return;
            }

            Log.d("ABManager", "Found a Device: " + '"' + deviceName + '"');
            if (isHosting(device) && !deviceAlreadyOnList(device)) gameList.add(new GameInformation(device.getAddress()));
        }

        private void register(Context context){
            if (registered)return;
            registered = true;
            context.getApplicationContext().registerReceiver(this, filter);
        }

        private void unregister(Context context){
            if (!registered)return;
            registered = false;
            context.getApplicationContext().unregisterReceiver(this);
        }

        private boolean deviceAlreadyOnList(BluetoothDevice bluetoothDevice){
            for (GameInformation gameInformation: gameList){
                if (gameInformation.getHostAddress().equals(bluetoothDevice.getAddress()))return true;
            }
            return false;
        }

        private boolean isHosting(BluetoothDevice bluetoothDevice){
            if (!bluetoothDevice.getName().endsWith(NOT_HOSTING_STRING))return true;
            return false;
        }
    }

    public interface ConnectionListener {

        void onConnectionEstablished();

        void onConnectionFailed();

        void onConnectionClosed();

        void onCOnnectionInterrupted();
    }

    public interface BluetoothActionListener{
        void stateChanged();
        void discoveryStarted();
        void discoveryFinished();
        void readyForSearch();
    }
}