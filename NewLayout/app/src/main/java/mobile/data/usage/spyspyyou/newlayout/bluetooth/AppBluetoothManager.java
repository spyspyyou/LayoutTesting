package mobile.data.usage.spyspyyou.newlayout.bluetooth;

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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import mobile.data.usage.spyspyyou.newlayout.R;

import static android.support.v4.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static mobile.data.usage.spyspyyou.newlayout.teststuff.VARS.APP_IDENTIFIER;
import static mobile.data.usage.spyspyyou.newlayout.teststuff.VARS.ASK_TO_TURN_ON_BT;


public class AppBluetoothManager {

    private static final char TEXT_SEPARATOR= '\3';

    public static final int
            INVALID_BLUETOOTH_STATE = -1,
            REQUEST_BLUETOOTH = 210,
            REQUEST_DISCOVERABLE = 211,
            REQUEST_CL_PERMISSION = 212;

    private static BluetoothMode
            MODE_IDLE = null;

    private static String
            localAddress = "",
            oldBluetoothName = "";

    private static BluetoothAdapter bluetoothAdapter = null;
    private static BluetoothBroadcastReceiver bluetoothBroadcastReceiver = null;

    private static final ArrayList<BluetoothActionListener> bluetoothActionListeners = new ArrayList<>();

    @NonNull
    private static volatile BluetoothMode bluetoothMode = new ModeNotInitialized();

    //----------------------------------------------------------------------------------------------
    //
    //          Bluetooth Activity Methods
    //
    //----------------------------------------------------------------------------------------------

    /**
     * Called when an Activity wants to use the Bluetooth API and thus extends BluetoothActivity.
     * @see BluetoothActivity
     * @param activity - Activity calling the method.
     */
    /*package*/ static void initialize(@NonNull Activity activity) {
        if (bluetoothMode instanceof ModeNotInitialized){
            Log.i("ABManager", "initialization starting");
            ((ModeNotInitialized) bluetoothMode).initialize(activity);
            MODE_IDLE.enter();
            Log.i("ABManager", "initialization complete, local Address is " + localAddress);
        }else {
            Log.i("ABManager", "already initialized");
        }
    }

    /*package*/ static void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQUEST_BLUETOOTH)
            bluetoothMode.onRequestResult(requestCode, resultCode == Activity.RESULT_OK);
        else if (requestCode == REQUEST_DISCOVERABLE)
            bluetoothMode.onRequestResult(REQUEST_DISCOVERABLE, resultCode == Activity.RESULT_OK);
    }

    /*package*/ static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CL_PERMISSION)
            bluetoothMode.onRequestResult(requestCode, grantResults[0] == PermissionChecker.PERMISSION_GRANTED);
    }

    //----------------------------------------------------------------------------------------------
    //
    //          START OF PUBLIC ACCESS METHODS
    //
    //----------------------------------------------------------------------------------------------

    public static void releaseAll(Activity activity){
        MODE_IDLE.enter();
    }

    public static void startServer(Activity activity, GameInformation gameInformation){
        new ModeServer(activity, gameInformation).enter();
    }

    public static void stopServer(Activity activity){
        new ModeClient().enter();
    }

    public static void searchGames(Activity activity, SearchListener searchListener){
        new ModeSearch(activity, searchListener).enter();
    }

    private static void cancelSearch(Activity activity){
        MODE_IDLE.enter();
    }

    public static void joinGame(Activity activity, BluetoothDevice bluetoothDevice, @Nullable ConnectionListener listener){
        new ModeClient(activity, bluetoothDevice, listener).enter();
    }

    //----------------------------------------------------------------------------------------------
    //
    //          LISTENER HELP METHODS
    //
    //----------------------------------------------------------------------------------------------

    public static void addBluetoothListener(BluetoothActionListener listener){
        synchronized (bluetoothActionListeners) {
            bluetoothActionListeners.add(listener);
        }
    }

    public static void removeBluetoothListener(BluetoothActionListener listener){
        synchronized (bluetoothActionListeners) {
            bluetoothActionListeners.remove(listener);
        }
    }

    public static void addConnectionListener(String address, ConnectionListener listener){
        ConnectionManager.addListener(address, listener);
    }

    public static void removeConnectionListener(String address, ConnectionListener listener){
        ConnectionManager.removeListener(address, listener);
    }

    //----------------------------------------------------------------------------------------------
    //
    //          INTERN METHODS
    //
    //----------------------------------------------------------------------------------------------

    private static void notifyStop() {
        synchronized (bluetoothActionListeners) {
            for (BluetoothActionListener listener : bluetoothActionListeners) {
                listener.onStop();

            }
        }
    }

    /*package*/ static void notifyConnectionEstablished(BluetoothDevice bluetoothDevice){
        synchronized (bluetoothActionListeners){
            for (BluetoothActionListener listener:bluetoothActionListeners){
                listener.onConnectionEstablished(bluetoothDevice);
            }
        }
    }

    /*package*/ static BluetoothServerSocket getBluetoothServerSocket(UUID uuid) throws IOException {
        return bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_IDENTIFIER, uuid);
    }

    public static String getLocalAddress(){
        return localAddress;
    }

    private static class BluetoothBroadcastReceiver extends BroadcastReceiver {

        private static final String[] FILTER_ACTIONS = {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_DISCOVERY_STARTED,
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED,
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED,
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
                case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:
                    onScanModeChanged(intent);
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
            }
        }

        private void onStateChanged(Intent intent){
            int
                    state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, INVALID_BLUETOOTH_STATE),
                    previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, INVALID_BLUETOOTH_STATE);
            if (previousState == BluetoothAdapter.STATE_ON &&(state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF))
                bluetoothMode.onBluetoothDeactivated();
            else
                Log.e("BluetoothBR", "Received an invalid bluetooth state changed event");
        }

        private void onScanModeChanged(Intent intent){
            int
                    mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, INVALID_BLUETOOTH_STATE),
                    previousMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, INVALID_BLUETOOTH_STATE);
            if (previousMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE && mode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                bluetoothMode.onDiscoverableEnded();
            else
                Log.e("BluetoothBR", "Received an invalid bluetooth scan mode changed event");
        }

        private void onDiscoveryStart(){
            Log.i("ABManager", "bluetooth discovery started");
            bluetoothMode.onDiscoveryStart();
        }

        private void onDiscoveryFinish(){
            Log.i("ABManager", "bluetooth discovery finished");
            bluetoothMode.onDiscoveryFinish();
        }

        private void onNameChange(){
            Log.i("ABManager", "Bluetooth name has changed to " + bluetoothAdapter.getName());
            bluetoothMode.onNameChange(bluetoothAdapter.getName());
        }

        private void onDeviceFound(Intent intent){
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null)
                Log.w("ABManager", "Received invalid device found intent");
            else
                bluetoothMode.onDeviceFound(device);
        }

        private void register(Context context){
            if (registered)
                Log.w("BluetoothBR", "receiver is already registered");
            else {
                registered = true;
                context.getApplicationContext().registerReceiver(this, filter);
            }
        }

        private void unregister(Context context){
            if (!registered)
                Log.w("BluetoothBR", "receiver isn't registered");
            else {
                registered = false;
                context.getApplicationContext().unregisterReceiver(this);
            }
        }

    }

    private static abstract class BluetoothMode {

        protected void enter(){
            if (bluetoothMode.getClass() == getClass()){
                Log.i("BluetoothMode", "Already in the requested mode: " + bluetoothMode.getClass().getSimpleName());
                return;
            }
            Log.e("BluetoothMode", "Changing mode from '" + bluetoothMode.getClass().getSimpleName() + "' to '" + this.getClass().getSimpleName() + "'");
            bluetoothMode.leaveMode();
            bluetoothMode = this;
            setupMode();
            Log.i("BluetoothMode", "Successfully changed bluetooth mode from '" + bluetoothMode.getClass().getSimpleName() + "' to '" + this.getClass().getSimpleName() + "'");
        }

        protected abstract void setupMode();

        protected abstract void leaveMode();

        protected abstract void onBluetoothDeactivated();

        protected abstract void onDiscoverableEnded();

        protected abstract void onDiscoveryStart();

        protected abstract void onDiscoveryFinish();

        protected abstract void onNameChange(String name);

        protected abstract void onDeviceFound(BluetoothDevice device);

        protected abstract void onRequestResult(int requestCode, boolean granted);

    }

    private static class ModeNotInitialized extends BluetoothMode {

        private static boolean initialized = false;

        @Override
        protected void setupMode() {
            Log.e("ModeNotInitialized", "setupMode(), should not be reached here");
        }

        @Override
        protected void leaveMode() {
            Log.i("ModeNotInitialized", "leaveMode()");
            if (!initialized) throw new BluetoothAPIException("The Bluetooth API was not initialized");
        }

        @Override
        protected void onBluetoothDeactivated() {
            Log.e("ModeNotInitialized", "onBluetoothDeactivated(), should not be reached here");
        }

        @Override
        protected void onDiscoverableEnded() {
            Log.e("ModeNotInitialized", "onDiscoverableEnded(), should not be reached here");
        }

        @Override
        protected void onDiscoveryStart() {
            Log.e("ModeNotInitialized", "onDiscoveryStart(), should not be reached here");
        }

        @Override
        protected void onDiscoveryFinish() {
            Log.e("ModeNotInitialized", "onDiscoveryFinish(), should not be reached here");
        }

        @Override
        protected void onNameChange(String name) {
            Log.e("ModeNotInitialized", "onNameChange(), should not be reached here");
        }

        @Override
        protected void onDeviceFound(BluetoothDevice device) {
            Log.e("ModeNotInitialized", "onDeviceFound(), should not be reached here");
        }

        @Override
        protected void onRequestResult(int requestCode, boolean granted) {
            Log.e("ModeNotInitialized", "onRequestResult(), should not be reached here");
        }

        private void initialize(@NonNull Activity activity) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null)
                handleNonBluetoothDevice(activity);
            else {
                localAddress = Settings.Secure.getString(activity.getContentResolver(), "bluetooth_address");
                oldBluetoothName = bluetoothAdapter.getName();
                if (localAddress == null) localAddress = bluetoothAdapter.getAddress();
                bluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
                ConnectionManager.initialize();
                MODE_IDLE = new ModeIdle(activity);
                initialized = true;
            }
        }

        private void handleNonBluetoothDevice(final Context context) {
            Log.w("APManager", "Device does not support bluetooth");
            new AlertDialog.Builder(context)
                    .setTitle("Bluetooth absent")
                    .setMessage("Unfortunately your device doesn't seem to have bluetooth. In a nutshell this app is pretty useless for you.")
                    .setPositiveButton("Ok :(", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    })
                    .show();
        }

    }

    private static class ModeIdle extends BluetoothMode {

        private final Context CONTEXT;

        private ModeIdle(Activity activity){
            CONTEXT = activity.getApplicationContext();
        }

        @Override
        protected void setupMode() {
            Log.i("ModeIdle", "setupMode()");
            bluetoothBroadcastReceiver.unregister(CONTEXT.getApplicationContext());
            bluetoothAdapter.cancelDiscovery();
            ConnectionManager.releaseAll();
            //todo:should it really be turned off?
            bluetoothAdapter.disable();
            if (!(bluetoothMode instanceof ModeNotInitialized))notifyStop();
        }

        @Override
        protected void leaveMode() {

        }

        @Override
        protected void onBluetoothDeactivated() {

        }

        @Override
        protected void onDiscoverableEnded() {

        }

        @Override
        protected void leaveMode(Context context) {
            Log.d("ModeIdle", "leaveMode()");
            bluetoothBroadcastReceiver.register(context.getApplicationContext());
        }

        @Override
        protected void onStateChanged(Context context, int state) {
            Log.d("ModeIdle", "onStateChanged()");
        }

        @Override
        protected void onScanModeChanged(Context context, int state) {
            Log.d("ModeIdle", "onScanModeChanged()");
        }

        @Override
        protected void onDiscoveryStart() {
            Log.w("ModeIdle", "onDiscoveryStart(), should not be reached here");
        }

        @Override
        protected void onDiscoveryFinish() {
            Log.d("ModeIdle", "onDiscoveryFinish()");
        }

        @Override
        protected void onNameChange(String name) {

        }

        @Override
        protected void onNameChange() {
            Log.d("ModeIdle", "onNameChanged: " + bluetoothAdapter.getName());
        }

        @Override
        protected void onDeviceFound(BluetoothDevice bluetoothDevice) {
            Log.w("ModeIdle", "onDeviceFound(), should not be reached here");
        }

        @Override
        protected void onRequestResult(int requestCode, boolean granted) {

        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode) {
            Log.w("ModeIdle", "onActivityResult(), should not be reached here");
        }

        @Override
        protected void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            Log.w("ModeIdle", "onRequestPermissionsResult(), should not be reached here");
        }
    }

    private static class ModeClient extends BluetoothMode {

        @Nullable
        private final Activity ACTIVITY;

        public ModeClient(ConnectionListener... connectionListeners){
            ACTIVITY = null;
            for (ConnectionListener connectionListener:connectionListeners)
                ConnectionManager.addListener(connectionListener);
        }

        public ModeClient(Activity activity, BluetoothDevice bluetoothDevice, @Nullable ConnectionListener listener){
            ACTIVITY = activity;
        }

        @Override
        protected void setupMode(Context context) {
            Log.i("ModeClient", "setupMode()");

        }

        @Override
        protected void leaveMode(Context context) {
            Log.d("ModeClient", "leaveMode()");
        }

        @Override
        protected void onStateChanged(Context context, int state) {
            Log.i("ModeClient", "onStateChanged()");
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF)
                MODE_IDLE.enter(ACTIVITY);
        }

        @Override
        protected void onScanModeChanged(Context context, int state) {
            Log.d("ModeClient", "onScanModeChanged()");
        }

        @Override
        protected void onDiscoveryStart() {
            Log.w("ModeClient", "onStateChanged(), should not be reached here");
        }

        @Override
        protected void onDiscoveryFinish() {
            Log.d("ModeClient", "onDiscoveryFinish()");
        }

        @Override
        protected void onNameChange() {
            Log.d("ModeClient", "onNameChange()");
        }

        @Override
        protected void onDeviceFound(BluetoothDevice device) {
            Log.w("ModeClient", "onDeviceFound(), should not be reached here");
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode) {
            Log.i("ModeClient", "onActivityResult()");
        }

        @Override
        protected void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            Log.w("ModeClient", "onRequestPermissionsResult(), should not be reached here");
        }
    }

    private static class ModeSearch extends BluetoothMode {

        private final ConnectionListener CONNECTION_LISTENER = new ConnectionListener() {
            @Override
            public void onConnectionEstablished(BluetoothDevice bluetoothDevice) {
                new GameInformationRequest(getLocalAddress()).send(bluetoothDevice.getAddress());
            }

            @Override
            public void onConnectionFailed(BluetoothDevice bluetoothDevice){
                isSearchFinished();
            }

            @Override
            public void onConnectionClosed(BluetoothDevice bluetoothDevice) {
                isSearchFinished();
            }
        };

        private final SearchListener SEARCH_LISTENER;
        private final Activity ACTIVITY;

        private ModeSearch(Activity activity, SearchListener searchListener) {
            ACTIVITY = activity;
            SEARCH_LISTENER = searchListener;
        }

        @Override
        protected void setupMode(Context context) {
            Log.i("ModeSearch", "setupMode()");
            if (hasCoarseLocationPermission(ACTIVITY)) {
                if (bluetoothAdapter.isEnabled())
                    startSearch();
                else if (ASK_TO_TURN_ON_BT)
                    ACTIVITY.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_BLUETOOTH);
                else
                    bluetoothAdapter.enable();
            } else
                requestCoarseLocationPermission(ACTIVITY);
        }

        @Override
        protected void leaveMode(Context context) {
            Log.d("ModeSearch", "leaveMode()");
            bluetoothAdapter.cancelDiscovery();
            ConnectionManager.releaseAll();
            SEARCH_LISTENER.onSearchFinished();
        }

        @Override
        protected void onStateChanged(Context context, int state) {
            Log.i("ModeSearch", "onStateChanged()");
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF)
                MODE_IDLE.enter(ACTIVITY);
        }

        @Override
        protected void onScanModeChanged(Context context, int state) {
            Log.d("ModeSearch", "onScanModeChanged()");
        }

        @Override
        protected void onDiscoveryStart() {
            Log.i("ModeSearch", "onDiscoveryStart()");
            SEARCH_LISTENER.onSearchStarted();
        }

        @Override
        protected void onDiscoveryFinish() {
            Log.i("ModeSearch", "onDiscoveryFinish()");
            isSearchFinished();
        }

        @Override
        protected void onNameChange() {
            Log.i("ModeSearch", "onNameChanged()");
        }

        @Override
        protected void onDeviceFound(BluetoothDevice device) {
            Log.i("ModeSearch", "onDeviceFound()");
            String deviceName = device.getName();
            if (deviceName == null)
                Log.d("ABManager", "Found a device with an invalid name");
            else {
                Log.d("ABManager", "Found a valid device: " + '"' + deviceName + '"');
                if (isHosting(deviceName)) {
                    Log.i("ABManager", "Found a Game:" + "\nName: " + deviceName.replace(APP_IDENTIFIER, "").replace(""+TEXT_SEPARATOR, "\nHost: ") + "\nAddress: " + device.getAddress());
                    SEARCH_LISTENER.onGameFound(deviceName.substring(APP_IDENTIFIER.length(), deviceName.indexOf(TEXT_SEPARATOR)), deviceName);
                    ConnectionManager.connect(device, CONNECTION_LISTENER);
                }
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode) {
            Log.i("ModeSearch", "onActivityResult()");
            if (requestCode == REQUEST_BLUETOOTH){
                if (resultCode == Activity.RESULT_OK)
                    startSearch();
                else {
                    SEARCH_LISTENER.onBluetoothNotAllowed();
                    MODE_IDLE.enter(ACTIVITY);
                }
            }
        }

        @Override
        protected void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            Log.i("ModeSearch", "onRequestPermissionsResult()");
            if (requestCode == REQUEST_CL_PERMISSION){
                if (grantResults[0] == PermissionChecker.PERMISSION_GRANTED){
                    if (bluetoothAdapter.isEnabled())
                        startSearch();
                    else if (ASK_TO_TURN_ON_BT)
                        ACTIVITY.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_BLUETOOTH);
                    else
                        bluetoothAdapter.enable();
                }else {
                    SEARCH_LISTENER.onCourseLocationNotGranted();
                    MODE_IDLE.enter(ACTIVITY);
                }
            }
        }

        private static boolean hasCoarseLocationPermission(final Activity activity) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        private static void requestCoarseLocationPermission(final Activity activity) {
            if (shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(activity)
                        .setTitle("Coarse Location Permission")
                        .setMessage("Dunno why but for since android 5.0 for some reason it is required to search for bluetooth devices...")
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CL_PERMISSION);
                                    }
                                }
                        )
                        .setCancelable(false)
                        .show();
            } else
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CL_PERMISSION);
        }

        private static void startSearch(){
            Log.i("ModeSearch", "startSearch()");
            bluetoothAdapter.cancelDiscovery();
            ConnectionManager.releaseAll();
            bluetoothAdapter.startDiscovery();
        }

        private void isSearchFinished(){
            if (ConnectionManager.isConnectionQueueEmpty() && !bluetoothAdapter.isDiscovering()) {
                MODE_IDLE.enter(ACTIVITY);
                SEARCH_LISTENER.onSearchFinished();
            }
        }

        private static boolean isHosting(String name){
            return name.startsWith(APP_IDENTIFIER);
        }

    }

    private static class ModeServer extends BluetoothMode {

        private final Activity ACTIVITY;
        private final GameInformation gameInformation;
        private final Intent DISCOVERABLE_INTENT =  new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

        public ModeServer(Activity activity, GameInformation gameInformation){
            ACTIVITY = activity;
            this.gameInformation = gameInformation;
        }

        @Override
        protected void setupMode(Context context) {
            Log.i("ModeServer", "setupMode()");
            bluetoothAdapter.cancelDiscovery();
            if (bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                setNameHosting();
                ConnectionManager.disconnect();
                ConnectionManager.startServer();
            } else {
                ACTIVITY.startActivityForResult(DISCOVERABLE_INTENT, REQUEST_DISCOVERABLE);
            }
        }

        @Override
        protected void leaveMode(Context context) {
            Log.d("ModeServer", "leaveMode()");
            ConnectionManager.stopServer();
            resetName();
        }

        @Override
        protected void onStateChanged(Context context, int state) {
            Log.i("ModeServer", "onStateChanged()");
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF)
                MODE_IDLE.enter(ACTIVITY);
        }

        @Override
        protected void onScanModeChanged(Context context, int state) {
            Log.i("ModeServer", "onScanModeChanged()");
            if (state == BluetoothAdapter.SCAN_MODE_CONNECTABLE)
                Snackbar.make(ACTIVITY.findViewById(R.id.toolbar_activityLobby), "Discoverability ended", Snackbar.LENGTH_LONG).setAction("Allow more joining", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ACTIVITY.startActivityForResult(DISCOVERABLE_INTENT, REQUEST_DISCOVERABLE);
                    }
                }).show();
        }

        @Override
        protected void onDiscoveryStart() {
            Log.w("ModeServer", "onDiscoveryStart(), should not be reached here");
        }

        @Override
        protected void onDiscoveryFinish() {
            Log.d("ModeServer", "onDiscoveryFinish()");
        }

        @Override
        protected void onNameChange() {
            Log.i("ModeServer", "onNameChange: " + bluetoothAdapter.getName());
            if (!hasRightName())setNameHosting();
        }

        @Override
        protected void onDeviceFound(BluetoothDevice device) {
            Log.w("ModeServer", "onDeviceFound(), should not be reached here");
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode) {
            Log.i("ModeServer", "onActivityResult()");
            if (requestCode == REQUEST_DISCOVERABLE){
                if (resultCode == Activity.RESULT_OK){
                    setNameHosting();
                    ConnectionManager.startServer();
                }else
                    MODE_IDLE.enter(ACTIVITY);

            }

        }

        @Override
        protected void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            Log.w("ModeServer", "onRequestPermissionsResult(), should not be reached here");
        }

        private boolean hasRightName(){
            return bluetoothAdapter.getName().equals(APP_IDENTIFIER + gameInformation.GAME_NAME + TEXT_SEPARATOR + gameInformation.HOST_NAME);
        }

        private void setNameHosting(){
            Log.w("ModeServer", "setNameHosting()");
            bluetoothAdapter.setName(APP_IDENTIFIER + gameInformation.GAME_NAME + TEXT_SEPARATOR + gameInformation.HOST_NAME);
        }

        private void resetName(){
            Log.w("ModeServer", "resetName()");
            bluetoothAdapter.setName(oldBluetoothName);
        }

    }

    //----------------------------------------------------------------------------------------------
    //
    //          LISTENERS
    //
    //----------------------------------------------------------------------------------------------

    public interface BluetoothActionListener{

        void onStop();

        void onConnectionEstablished(BluetoothDevice bluetoothDevice);

    }

    public interface ConnectionListener {

        void onConnectionEstablished(BluetoothDevice bluetoothDevice);

        void onConnectionFailed(BluetoothDevice bluetoothDevice);

        void onConnectionClosed(BluetoothDevice bluetoothDevice);
    }

    public interface SearchListener {

        void onCourseLocationNotGranted();

        void onBluetoothNotAllowed();

        void onSearchStarted();

        void onSearchFinished();

        void onGameFound(String gameName, String hostName);

        void onGameInformationReceived();

    }


    //----------------------------------------------------------------------------------------------
    //
    //          EXCEPTION
    //
    //----------------------------------------------------------------------------------------------

    public static class BluetoothAPIException extends RuntimeException{
        public BluetoothAPIException(String errorMessage){
            super(errorMessage);
        }
    }

}