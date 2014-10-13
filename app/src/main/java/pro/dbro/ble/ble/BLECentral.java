package pro.dbro.ble.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import pro.dbro.ble.LogConsumer;
import pro.dbro.ble.R;

/**
 * A basic BLE Central device that discovers peripherals
 *
 * Created by davidbrodsky on 10/2/14.
 */
public class BLECentral {
    public static final String TAG = "BLECentral";
    private static final boolean REPORT_NON_SUCCESSES = false;

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mScanner;
    private LogConsumer mLogger;
    private BLEComponentCallback mCallback;

    private boolean mIsScanning = false;

    // <editor-fold desc="Public API">

    public BLECentral(@NonNull Context context) {
        mContext = context;
        init();
        setScanCallback();
    }

    public void setLogConsumer(LogConsumer consumer) {
        mLogger = consumer;
    }

    public void setComponentCallback(BLEComponentCallback cb) {
        mCallback = cb;
    }

    public void start() {
        startScanning();
    }

    public void stop() {
        stopScanning();
    }

    public boolean isIsScanning() {
        return mIsScanning;
    }
    // </editor-fold>

    //<editor-fold desc="Private API">

    private void init() {
        // BLE check
        if (!BLEUtil.isBLESupported(mContext)) {
            Toast.makeText(mContext, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        // BT check
        BluetoothManager manager = BLEUtil.getManager(mContext);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            Toast.makeText(mContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

    }

    private void setScanCallback() {
        mScanCallback = new ScanCallback() {
            final String TAG = "ScanCallback";

            private boolean connected = false;

            @Override
            public void onAdvertisementUpdate(ScanResult scanResult) {
                String toLog = String.format("Scanned %s", scanResult.getDevice().getName());
                Log.i(TAG, toLog);

//                Only attempt one connection at a time
                if (connected) return;
//                if (REPORT_NON_SUCCESSES) Log.i(TAG, "Got new advertisement before connection");
                final BluetoothGatt bleGatt = scanResult.getDevice().connectGatt(mContext, true, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

                        if (status == BluetoothGatt.GATT_SUCCESS) {
//                            Log.i(TAG, "status indicates GATT Connection Success!");
                            logEvent("status indicates GATT Connection Success!");
                        } else {
//                            if (REPORT_NON_SUCCESSES) mLogger.onLogEvent("status indicates GATT Connection not yet successful");
                        }

                        switch (newState) {
                            case BluetoothProfile.STATE_DISCONNECTED:
                                if (REPORT_NON_SUCCESSES) logEvent("newState indicates GATT disconnected");
                                break;
                            case BluetoothProfile.STATE_CONNECTED:
                                logEvent("newState indicates indicates GATT connected");
                                connected = true;
                                onSuccessfulConnection(gatt);
                                break;
                        }

//                        if (!triedDiscoverServices) {
//                            triedDiscoverServices = true;
//                            boolean discovering = gatt.discoverServices();
//                            if (REPORT_NON_SUCCESSES || discovering)
//                                logEvent("Discovering services : " + discovering);
//                        }
//                        UUID characteristicUUID = UUID.fromString(BleUuid.IDENTITY_UUID);
//                        boolean readResult = gatt.readCharacteristic(new BluetoothGattCharacteristic(characteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.));
//                        if (REPORT_NON_SUCCESSES || readResult)
//                            logEvent("Attempting to read characteristic " + readResult);
                        super.onConnectionStateChange(gatt, status, newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        boolean foundExpectedCharacteristic = false;
//
                        List<BluetoothGattService> serviceList = gatt.getServices();
                        for (BluetoothGattService service : serviceList) {
                            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                if (characteristic.getUuid().equals(GATT.IDENTITY_UUID)) {
                                    logEvent("Found Identity char");
                                }
                                else if (characteristic.getUuid().equals(GATT.MESSAGES_UUID)) {
                                    logEvent("Readable Characteristic UUID: " +
                                            characteristic.getUuid().toString() +
                                            " properties: " + characteristic.getProperties() +
                                            " permissions: " + characteristic.getPermissions());

                                    foundExpectedCharacteristic = true;
                                    boolean result = gatt.readCharacteristic(characteristic);
                                    logEvent("Reading characteristic. Result: " + result);

                                }
                            }
                        }
                        if (foundExpectedCharacteristic)
                            logEvent("Found Characteristic. Requesting Read!");
                        super.onServicesDiscovered(gatt, status);
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        logEvent("onCharacteristicRead " + characteristic.getStringValue(0));
                        super.onCharacteristicRead(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//                        Log.i(TAG, "onCharacteristicWrite");
                        logEvent("onCharacteristicWrite " + characteristic.getStringValue(0));
                        super.onCharacteristicWrite(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        String toLog = null;
                        try {
                            toLog = "onCharacteristicChanged value: " + characteristic.getStringValue(0);
                        } catch (Exception e) {
                            // whoops
                            toLog = "onCharacteristicChanged uuid: " + characteristic.getUuid().toString();
                        }
                        logEvent(toLog);
//                        Log.i(TAG, "onCharacteristicChanged");
                        super.onCharacteristicChanged(gatt, characteristic);
                    }

                    @Override
                    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        Log.i(TAG, "onDescriptorRead");
                        super.onDescriptorRead(gatt, descriptor, status);
                    }

                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        Log.i(TAG, "onDescriptorWrite");
                        super.onDescriptorWrite(gatt, descriptor, status);
                    }

                    @Override
                    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                        Log.i(TAG, "onReliableWriteCompleted");
                        super.onReliableWriteCompleted(gatt, status);
                    }

                    @Override
                    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                        Log.i(TAG, "onReadRemoteRssi");
                        super.onReadRemoteRssi(gatt, rssi, status);
                    }
                });
            }

            @Override
            public void onScanFailed(int i) {
                String toLog = "Scan failed with code " + i;
                Log.i(TAG, toLog);
            }
        };
    }

    private void startScanning() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            if (mScanner == null) {
                mScanner = mBTAdapter.getBluetoothLeScanner();
            }
            mScanner.startScan(createScanFilters(), createScanSettings(), mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_started), Toast.LENGTH_SHORT).show();
        }
    }

    private static List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
        builder.setServiceUuid(new ParcelUuid(GATT.SERVICE_UUID));
        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(builder.build());
        return scanFilters;
    }

    private static ScanSettings createScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ON_UPDATE);
        builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        return builder.build();
    }

    private void stopScanning() {
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_stopped), Toast.LENGTH_SHORT).show();
        }
        mIsScanning = false;
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        }
    }

    /**
     * Invoked by {@link BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     */
    private void onSuccessfulConnection(BluetoothGatt gatt) {
        if (mCallback != null) {
            mCallback.onConnectedToPeripheral(gatt);
        }
    }

    //</editor-fold>
}