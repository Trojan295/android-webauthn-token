package jp.kshoji.blehid;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseData.Builder;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.kshoji.blehid.util.BleUuidUtils;

/**
 * BLE HID over GATT base features
 *
 * @author K.Shoji
 */
@TargetApi(VERSION_CODES.LOLLIPOP)
public abstract class HidPeripheral {
	private static final boolean DEBUG = true; // set false on production
    private static final String TAG = HidPeripheral.class.getSimpleName();

    /**
     * Main items
     */
    protected static byte INPUT(final int size) {
        return (byte) (0x80 | size);
    }
    protected static byte OUTPUT(final int size) {
        return (byte) (0x90 | size);
    }
    protected static byte COLLECTION(final int size) {
        return (byte) (0xA0 | size);
    }
    protected static byte FEATURE(final int size) {
        return (byte) (0xB0 | size);
    }
    protected static byte END_COLLECTION(final int size) {
        return (byte) (0xC0 | size);
    }

    /**
     * Global items
     */
    protected static byte USAGE_PAGE(final int size) {
        return (byte) (0x04 | size);
    }
    protected static byte LOGICAL_MINIMUM(final int size) {
        return (byte) (0x14 | size);
    }
    protected static byte LOGICAL_MAXIMUM(final int size) {
        return (byte) (0x24 | size);
    }
    protected static byte PHYSICAL_MINIMUM(final int size) {
        return (byte) (0x34 | size);
    }
    protected static byte PHYSICAL_MAXIMUM(final int size) {
        return (byte) (0x44 | size);
    }
    protected static byte UNIT_EXPONENT(final int size) {
        return (byte) (0x54 | size);
    }
    protected static byte UNIT(final int size) {
        return (byte) (0x64 | size);
    }
    protected static byte REPORT_SIZE(final int size) {
        return (byte) (0x74 | size);
    }
    protected static byte REPORT_ID(final int size) {
        return (byte) (0x84 | size);
    }
    protected static byte REPORT_COUNT(final int size) {
        return (byte) (0x94 | size);
    }

    /**
     * Local items
     */
    protected static byte USAGE(final int size) {
        return (byte) (0x08 | size);
    }
    protected static byte USAGE_MINIMUM(final int size) {
        return (byte) (0x18 | size);
    }
    protected static byte USAGE_MAXIMUM(final int size) {
        return (byte) (0x28 | size);
    }

    protected static byte LSB(final int value) {
        return (byte) (value & 0xff);
    }
    protected static byte MSB(final int value) {
        return (byte) (value >> 8 & 0xff);
    }
    
    /**
     * Device Information Service
     */
    private static final UUID SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A);
    private static final UUID CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29);
    private static final UUID CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24);
    private static final UUID CHARACTERISTIC_SERIAL_NUMBER = BleUuidUtils.fromShortValue(0x2A25);
    private static final int DEVICE_INFO_MAX_LENGTH = 20;

    private String manufacturer = "kshoji.jp";
    private String deviceName = "BLE HID";
    private String serialNumber = "12345678";

    /**
     * Battery Service
     */
    private static final UUID SERVICE_BATTERY = BleUuidUtils.fromShortValue(0x180F);
    private static final UUID CHARACTERISTIC_BATTERY_LEVEL = BleUuidUtils.fromShortValue(0x2A19);

    /**
     * HID Service
     */
    private static final UUID SERVICE_BLE_HID = BleUuidUtils.fromShortValue(0x1812);
    private static final UUID CHARACTERISTIC_HID_INFORMATION = BleUuidUtils.fromShortValue(0x2A4A);
    private static final UUID CHARACTERISTIC_REPORT_MAP = BleUuidUtils.fromShortValue(0x2A4B);
    private static final UUID CHARACTERISTIC_HID_CONTROL_POINT = BleUuidUtils.fromShortValue(0x2A4C);
    private static final UUID CHARACTERISTIC_REPORT = BleUuidUtils.fromShortValue(0x2A4D);
    private static final UUID CHARACTERISTIC_PROTOCOL_MODE = BleUuidUtils.fromShortValue(0x2A4E);

    /**
     * Represents Report Map byte array
     * @return Report Map data
     */
    protected abstract byte[] getReportMap();
    
    /**
     * HID Input Report
     */
    private final Queue<byte[]> inputReportQueue = new ConcurrentLinkedQueue<>();
    protected final void addInputReport(final byte[] inputReport) {
        if (inputReport != null && inputReport.length > 0) {
            inputReportQueue.offer(inputReport);
        }
    }

    /**
     * HID Output Report
     *
     * @param outputReport the report data
     */
    protected abstract void onOutputReport(final byte[] outputReport);

    /**
     * Gatt Characteristic Descriptor
     */
    private static final UUID DESCRIPTOR_REPORT_REFERENCE = BleUuidUtils.fromShortValue(0x2908);
    private static final UUID DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUuidUtils.fromShortValue(0x2902);

    private static final byte[] EMPTY_BYTES = {};
    private static final byte[] RESPONSE_HID_INFORMATION = {0x11, 0x01, 0x00, 0x03};

    /**
     * Instances for the peripheral
     */
    private final Context appContext;
    private final Handler handler;
	private final Object mSync = new Object();
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattCharacteristic inputReportCharacteristic;
    @Nullable
    private BluetoothGattServer gattServer;
    private final Map<String, BluetoothDevice> bluetoothDevicesMap = new HashMap<>();
	private Timer mDataSendTimer;
	private SetupServiceTask mSetupServiceTask;

    /**
     * Constructor<br />
     * Before constructing the instance, check the Bluetooth availability.
     *
     * @param context the ApplicationContext
     * @param needInputReport true: serves 'Input Report' BLE characteristic
     * @param needOutputReport true: serves 'Output Report' BLE characteristic
     * @param needFeatureReport true: serves 'Feature Report' BLE characteristic
     * @param dataSendingRate sending rate in milliseconds
     * @throws UnsupportedOperationException if starting Bluetooth LE Peripheral failed
     */
    protected HidPeripheral(@NonNull final Context context,
    	final boolean needInputReport, final boolean needOutputReport,
    	final boolean needFeatureReport, final int dataSendingRate)
    		throws UnsupportedOperationException {

        appContext = context.getApplicationContext();
        handler = new Handler(appContext.getMainLooper());

        final BluetoothManager bluetoothManager
        	= (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        if (!bluetoothAdapter.isEnabled()) {
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        }

        if (DEBUG) Log.v(TAG, "isMultipleAdvertisementSupported:"
        	+ bluetoothAdapter.isMultipleAdvertisementSupported());
        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
		if (DEBUG) Log.v(TAG, "bluetoothLeAdvertiser: " + bluetoothLeAdvertiser);
        if (bluetoothLeAdvertiser == null) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        gattServer = bluetoothManager.openGattServer(appContext, gattServerCallback);
        if (gattServer == null) {
            throw new UnsupportedOperationException("gattServer is null, check Bluetooth is ON.");
        }

		mSetupServiceTask = new SetupServiceTask(
			needInputReport, needOutputReport, needFeatureReport, dataSendingRate);
		new Thread(mSetupServiceTask, TAG).start();
    }
	
	@Override
	protected void finalize() throws Throwable {
		try {
			release();
		} finally {
			super.finalize();
		}
	}
	
	public void release() {
		synchronized (mSync) {
			if (mSetupServiceTask != null) {
				mSetupServiceTask.mIsRunning = false;
				mSync.notify();
				mSetupServiceTask = null;
			}
			mDataSendTimer = null;
		}
		unRegisterBroadcast();
	}

	/**
	 * workaround to avoid issue of BluetoothGattServer#addService
	 * when adding multiple BluetoothGattServices continuously.
	 */
	private class SetupServiceTask implements Runnable {
	
		private final boolean needInputReport;
		private final boolean needOutputReport;
		private final boolean needFeatureReport;
		private final int dataSendingRate;

		private volatile boolean mIsRunning;
		private volatile boolean mServiceAdded;
		
		private SetupServiceTask(
			final boolean needInputReport, final boolean needOutputReport,
			final boolean needFeatureReport, final int dataSendingRate) {
			
			this.needInputReport = needInputReport;
			this.needOutputReport = needOutputReport;
			this.needFeatureReport = needFeatureReport;
			this.dataSendingRate = dataSendingRate;
		}
		
		@Override
		public void run() {
			mIsRunning = true;
			// setup services
			addService(setUpHidService(needInputReport, needOutputReport, needFeatureReport));
			addService(setUpDeviceInformationService());
			addService(setUpBatteryService());
			synchronized (mSync) {
				if (mIsRunning) {
					// send report each dataSendingRate, if data available
					mDataSendTimer = new Timer();
					mDataSendTimer.scheduleAtFixedRate(new TimerTask() {
						@Override
						public void run() {
							final byte[] polled = inputReportQueue.poll();
							if (polled != null && inputReportCharacteristic != null) {
								inputReportCharacteristic.setValue(polled);
								handler.post(() -> {
									final Set<BluetoothDevice> devices = getDevices();
									for (final BluetoothDevice device : devices) {
										try {
											if (gattServer != null) {
												gattServer.notifyCharacteristicChanged(device, inputReportCharacteristic, false);
											}
										} catch (final Throwable e) {
											Log.w(TAG, e);
										}
									}
								});
							}
						}
					}, 0, dataSendingRate);
				}
				mSetupServiceTask = null;
			}
		}

		/**
		 * Add GATT service to gattServer
		 *
		 * @param service the service
		 */
		private void addService(@NonNull final BluetoothGattService service) {
			if (DEBUG) Log.v(TAG, "addService:" + service.getUuid());
			mServiceAdded = false;
			try {
				if (gattServer.addService(service)) {
					synchronized (mSync) {
						for ( ; mIsRunning && !mServiceAdded; ) {
							mSync.wait(1000);
						}
						if (mIsRunning) {
							mSync.wait(30);
						}
					}
					if (DEBUG) Log.v(TAG, "addService:" + service.getUuid() + " added.");
					return;
				}
			} catch (final Exception e) {
				Log.w(TAG, "Adding Service failed", e);
			}
			Log.w(TAG, "Adding Service failed");
		}
	}
	
    /**
     * Setup Device Information Service
     *
     * @return the service
     */
    private static BluetoothGattService setUpDeviceInformationService() {
        final BluetoothGattService service
        	= new BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
            		CHARACTERISTIC_MANUFACTURER_NAME,
            		BluetoothGattCharacteristic.PROPERTY_READ,
            		BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
            		CHARACTERISTIC_MODEL_NUMBER,
            		BluetoothGattCharacteristic.PROPERTY_READ,
            		BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
            		CHARACTERISTIC_SERIAL_NUMBER,
            		BluetoothGattCharacteristic.PROPERTY_READ,
            		BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        return service;
    }

    /**
     * Setup Battery Service
     *
     * @return the service
     */
    private static BluetoothGattService setUpBatteryService() {
        final BluetoothGattService service
        	= new BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Battery Level
        final BluetoothGattCharacteristic characteristic
        	= new BluetoothGattCharacteristic(
                CHARACTERISTIC_BATTERY_LEVEL,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
                	| BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);

        final BluetoothGattDescriptor clientCharacteristicConfigurationDescriptor
        	= new BluetoothGattDescriptor(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ
                	| BluetoothGattDescriptor.PERMISSION_WRITE);
        clientCharacteristicConfigurationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor);

        if (!service.addCharacteristic(characteristic)) {
			throw new RuntimeException("failed to add characteristic");
		}

        return service;
    }

    /**
     * Setup HID Service
     *
     * @param isNeedInputReport true: serves 'Input Report' BLE characteristic
     * @param isNeedOutputReport true: serves 'Output Report' BLE characteristic
     * @param isNeedFeatureReport true: serves 'Feature Report' BLE characteristic
     * @return the service
     */
    private BluetoothGattService setUpHidService(
    	final boolean isNeedInputReport,
    	final boolean isNeedOutputReport,
    	final boolean isNeedFeatureReport) {

        final BluetoothGattService service
        	= new BluetoothGattService(SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // HID Information
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_HID_INFORMATION,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);

            if (!service.addCharacteristic(characteristic)) {	// XXX これは良くない、最悪ビジーループになる
            	throw new RuntimeException("failed to add characteristic");
			}
        }

        // Report Map
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT_MAP,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        // Protocol Mode
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_PROTOCOL_MODE,
                    BluetoothGattCharacteristic.PROPERTY_READ
                    	| BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        // HID Control Point
        {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_HID_CONTROL_POINT,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        // Input Report
        if (isNeedInputReport) {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    	| BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);

            final BluetoothGattDescriptor clientCharacteristicConfigurationDescriptor
            	= new BluetoothGattDescriptor(
                    DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED); //  | BluetoothGattDescriptor.PERMISSION_WRITE
            clientCharacteristicConfigurationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor);

            final BluetoothGattDescriptor reportReferenceDescriptor
            	= new BluetoothGattDescriptor(
                    DESCRIPTOR_REPORT_REFERENCE,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
            characteristic.addDescriptor(reportReferenceDescriptor);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
            inputReportCharacteristic = characteristic;
        }

        // Output Report
        if (isNeedOutputReport) {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT,
                    BluetoothGattCharacteristic.PROPERTY_READ
                    	| BluetoothGattCharacteristic.PROPERTY_WRITE
                    	| BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            final BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                    DESCRIPTOR_REPORT_REFERENCE,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
            characteristic.addDescriptor(descriptor);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        // Feature Report
        if (isNeedFeatureReport) {
            final BluetoothGattCharacteristic characteristic
            	= new BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT,
                    BluetoothGattCharacteristic.PROPERTY_READ
                    	| BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);

            final BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                    DESCRIPTOR_REPORT_REFERENCE,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                    	| BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
            characteristic.addDescriptor(descriptor);

            if (!service.addCharacteristic(characteristic)) {
				throw new RuntimeException("failed to add characteristic");
			}
        }

        return service;
    }

    /**
     * Starts advertising
     */
    public final void startAdvertising() {
		registerBroadcast();
        handler.post(() -> {
			// set up advertising setting
			final AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
					.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
					.setConnectable(true)
					.setTimeout(0)
					.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
					.build();

			// set up advertising data
			final AdvertiseData advertiseData = new Builder()
					.setIncludeTxPowerLevel(false)
					.setIncludeDeviceName(true)
					.addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
					.addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
					.addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
					.build();

			// set up scan result
			final AdvertiseData scanResult = new Builder()
					.addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
					.addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_HID.toString()))
					.addServiceUuid(ParcelUuid.fromString(SERVICE_BATTERY.toString()))
					.build();

			Log.v(TAG, "advertiseData: " + advertiseData + ", scanResult: " + scanResult);
			bluetoothLeAdvertiser.startAdvertising(
				advertiseSettings, advertiseData, scanResult, advertiseCallback);
        });
    }

    /**
     * Stops advertising
     */
    public final void stopAdvertising() {
		unRegisterBroadcast();
        handler.post(() -> {
			try {
				bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
			} catch (final IllegalStateException e) {
				// BT Adapter is not turned ON
				if (DEBUG) Log.w(TAG, e);
			}
			try {
				if (gattServer != null) {
					final Set<BluetoothDevice> devices = getDevices();
					for (final BluetoothDevice device : devices) {
						gattServer.cancelConnection(device);
					}

					gattServer.close();
					gattServer = null;
				}
			} catch (final IllegalStateException e) {
				// BT Adapter is not turned ON
				if (DEBUG) Log.w(TAG, e);
			}
        });
    }

    /**
     * Callback for BLE connection<br />
     * nothing to do.
     */
    private final AdvertiseCallback advertiseCallback = new NullAdvertiseCallback();
    private static class NullAdvertiseCallback extends AdvertiseCallback {
		@Override
		public void onStartSuccess(final AdvertiseSettings settingsInEffect) {
			if (DEBUG) Log.v(TAG, "onStartSuccess:" + settingsInEffect);
		}
		@Override
		public void onStartFailure(final int errorCode) {
			if (DEBUG) Log.v(TAG, "onStartFailure:" + errorCode);
		}
    }

    /**
     * Obtains connected Bluetooth devices
     *
     * @return the connected Bluetooth devices
     */
    private Set<BluetoothDevice> getDevices() {
        final Set<BluetoothDevice> deviceSet;
        synchronized (bluetoothDevicesMap) {
            deviceSet = new HashSet<>(bluetoothDevicesMap.values());
        }
        return Collections.unmodifiableSet(deviceSet);
    }

    /**
     * Callback for BLE data transfer
     */
    private final BluetoothGattServerCallback gattServerCallback
    	= new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothDevice device,
        	final int status, final int newState) {

            super.onConnectionStateChange(device, status, newState);
			if (DEBUG) Log.v(TAG, "onConnectionStateChange status: " + status + ", newState: " + newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
					if (DEBUG) Log.v(TAG, "onConnectionStateChange:STATE_CONNECTED");
                    // check bond status
                    final int bondState = device.getBondState();
                    if (DEBUG) Log.v(TAG, "BluetoothProfile.STATE_CONNECTED bondState: " + bondState);
                    if (bondState == BluetoothDevice.BOND_NONE) {
                    	if (DEBUG) Log.v(TAG, "onConnectionStateChange:BOND_NONE");
                        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
                        appContext.registerReceiver(mBroadcastReceiver, filter);
						// create bond
//						try {
//							device.setPairingConfirmation(true);
//						} catch (final SecurityException e) {
//							Log.w(TAG, e);
//						}
						device.createBond();
                    } else if (bondState == BluetoothDevice.BOND_BONDED) {
						if (DEBUG) Log.v(TAG, "onConnectionStateChange:BOND_BONDED");
                        handler.post(() -> {
							if (gattServer != null) {
								gattServer.connect(device, true);
							}
                        });
                        synchronized (bluetoothDevicesMap) {
                            bluetoothDevicesMap.put(device.getAddress(), device);
                        }
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
					if (DEBUG) Log.v(TAG, "onConnectionStateChange:STATE_DISCONNECTED");
                    final String deviceAddress = device.getAddress();

					synchronized (bluetoothDevicesMap) {
						bluetoothDevicesMap.remove(deviceAddress);
					}
                    // try reconnect immediately
                    handler.post(() -> {
						if (gattServer != null) {
							// gattServer.cancelConnection(device);
							gattServer.connect(device, true);
						}
                    });
                    
                    break;

                default:
                    // do nothing
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(final BluetoothDevice device,
        	final int requestId, final int offset,
        	final BluetoothGattCharacteristic characteristic) {

            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (gattServer == null) {
                return;
            }
			if (DEBUG) Log.v(TAG, "onCharacteristicReadRequest characteristic: " + characteristic.getUuid() + ", offset: " + offset);

            handler.post(new Runnable() {
                @Override
                public void run() {

                    final UUID characteristicUuid = characteristic.getUuid();
                    if (BleUuidUtils.matches(CHARACTERISTIC_HID_INFORMATION, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, RESPONSE_HID_INFORMATION);
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_REPORT_MAP, characteristicUuid)) {
                        if (offset == 0) {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, getReportMap());
                        } else {
                            final int remainLength = getReportMap().length - offset;
                            if (remainLength > 0) {
                                final byte[] data = new byte[remainLength];
                                System.arraycopy(getReportMap(), offset, data, 0, remainLength);
                                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data);
                            } else {
                                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                            }
                        }
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_HID_CONTROL_POINT, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, new byte []{0});
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, EMPTY_BYTES);
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_MANUFACTURER_NAME, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, manufacturer.getBytes(StandardCharsets.UTF_8));
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_SERIAL_NUMBER, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, serialNumber.getBytes(StandardCharsets.UTF_8));
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_MODEL_NUMBER, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, deviceName.getBytes(StandardCharsets.UTF_8));
                    } else if (BleUuidUtils.matches(CHARACTERISTIC_BATTERY_LEVEL, characteristicUuid)) {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, new byte[] {0x64}); // always 100%
                    } else {
                        gattServer.sendResponse(device, requestId,
                        	BluetoothGatt.GATT_SUCCESS, 0, characteristic.getValue());
                    }
                }
            });
        }

        @Override
        public void onDescriptorReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
			if (DEBUG) Log.v(TAG, "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.getUuid());

            if (gattServer == null) {
                return;
            }

            handler.post(() -> {
				if (BleUuidUtils.matches(DESCRIPTOR_REPORT_REFERENCE, descriptor.getUuid())) {
					final int characteristicProperties = descriptor.getCharacteristic().getProperties();
					if (characteristicProperties == (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
						// Input Report
						gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0, 1});
					} else if (characteristicProperties == (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
						// Output Report
						gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0, 2});
					} else if (characteristicProperties == (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE)) {
						// Feature Report
						gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0, 3});
					} else {
						gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, EMPTY_BYTES);
					}
				}
            });
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
			if (DEBUG) Log.v(TAG, "onCharacteristicWriteRequest characteristic: " + characteristic.getUuid() + ", value: " + Arrays.toString(value));

            if (gattServer == null) {
                return;
            }

            if (responseNeeded) {
                if (BleUuidUtils.matches(CHARACTERISTIC_REPORT, characteristic.getUuid())) {
                    if (characteristic.getProperties() == (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
                        // Output Report
                        onOutputReport(value);

                        // send empty
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, EMPTY_BYTES);
                    } else {
                        // send empty
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, EMPTY_BYTES);
                    }
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattDescriptor descriptor, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
			if (DEBUG) Log.v(TAG, "onDescriptorWriteRequest descriptor: " + descriptor.getUuid() + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite);

            descriptor.setValue(value);

            if (responseNeeded) {
                if (BleUuidUtils.matches(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION, descriptor.getUuid())) {
                    // send empty
                    if (gattServer != null) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, EMPTY_BYTES);
                    }
                }
            }
        }

        @Override
        public void onServiceAdded(final int status, final BluetoothGattService service) {
            super.onServiceAdded(status, service);
			if (DEBUG) Log.v(TAG, "onServiceAdded status: " + status + ", service: " + service.getUuid());

			// workaround to avoid issue of BluetoothGattServer#addService
			// when adding multiple BluetoothGattServices continuously.
			synchronized (mSync) {
				if (mSetupServiceTask != null) {
					mSetupServiceTask.mServiceAdded = true;
					mSync.notify();
				}
			}
            if (status != 0) {
                Log.w(TAG, "onServiceAdded Adding Service failed..");
			}
        }
    };

	private void registerBroadcast() {
		synchronized (mSync) {
			if (mBroadcastReceiver == null) {
				mBroadcastReceiver = new MyBroadcastReceiver();
				final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
				filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
				appContext.registerReceiver(mBroadcastReceiver, filter);
			}
		}
	}

	private void unRegisterBroadcast() {
		synchronized (mSync) {
			if (mBroadcastReceiver != null) {
				try {
					appContext.unregisterReceiver(mBroadcastReceiver);
				} catch (final Exception e) {
					if (DEBUG) Log.w(TAG, e);
				}
				mBroadcastReceiver = null;
			}
		}
	}

	private BroadcastReceiver mBroadcastReceiver;
	
	private class MyBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent != null ? intent.getAction() : null;
			if (DEBUG) Log.v(TAG, "onReceive action: " + action);

			if (!TextUtils.isEmpty(action)) {
				final BluetoothDevice bondedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
				switch (action) {
				case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
					if (state == BluetoothDevice.BOND_BONDED) {
						// successfully bonded
         				context.unregisterReceiver(this);
						handler.post(() -> {
							if (gattServer != null) {
								gattServer.connect(bondedDevice, true);
							}
						});
						if (DEBUG) Log.v(TAG, "successfully bonded");
					}
					break;
				case BluetoothDevice.ACTION_PAIRING_REQUEST:
					if (DEBUG) Log.v(TAG, "BluetoothDevice.ACTION_PAIRING_REQUEST");
					break;
				}
			}
		}
	};

    /**
     * Set the manufacturer name
     *
     * @param newManufacturer the name
     */
    public final void setManufacturer(@NonNull final String newManufacturer) {
        // length check
        final byte[] manufacturerBytes = newManufacturer.getBytes(StandardCharsets.UTF_8);
        if (manufacturerBytes.length > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            final byte[] bytes = new byte[DEVICE_INFO_MAX_LENGTH];
            System.arraycopy(manufacturerBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH);
            manufacturer = new String(bytes, StandardCharsets.UTF_8);
        } else {
            manufacturer = newManufacturer;
        }
    }

    /**
     * Set the device name
     *
     * @param newDeviceName the name
     */
    public final void setDeviceName(@NonNull final String newDeviceName) {
        // length check
        final byte[] deviceNameBytes = newDeviceName.getBytes(StandardCharsets.UTF_8);
        if (deviceNameBytes.length > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            final byte[] bytes = new byte[DEVICE_INFO_MAX_LENGTH];
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH);
            deviceName = new String(bytes, StandardCharsets.UTF_8);
        } else {
            deviceName = newDeviceName;
        }
    }

    /**
     * Set the serial number
     *
     * @param newSerialNumber the number
     */
    public final void setSerialNumber(@NonNull final String newSerialNumber) {
        // length check
        final byte[] deviceNameBytes = newSerialNumber.getBytes(StandardCharsets.UTF_8);
        if (deviceNameBytes.length > DEVICE_INFO_MAX_LENGTH) {
            // shorten
            final byte[] bytes = new byte[DEVICE_INFO_MAX_LENGTH];
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_INFO_MAX_LENGTH);
            serialNumber = new String(bytes, StandardCharsets.UTF_8);
        } else {
            serialNumber = newSerialNumber;
        }
    }
}
