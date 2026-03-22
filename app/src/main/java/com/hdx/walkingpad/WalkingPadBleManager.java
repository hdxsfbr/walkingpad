package com.hdx.walkingpad;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class WalkingPadBleManager {
    interface Listener {
        void onStateChanged(UiState state);
    }

    static final class UiState {
        final boolean connected;
        final boolean ready;
        final String connectionStatus;
        final String details;
        final float selectedSpeedMph;
        final float lastFeedbackMph;
        final float lastTargetMph;

        UiState(
            boolean connected,
            boolean ready,
            String connectionStatus,
            String details,
            float selectedSpeedMph,
            float lastFeedbackMph,
            float lastTargetMph
        ) {
            this.connected = connected;
            this.ready = ready;
            this.connectionStatus = connectionStatus;
            this.details = details;
            this.selectedSpeedMph = selectedSpeedMph;
            this.lastFeedbackMph = lastFeedbackMph;
            this.lastTargetMph = lastTargetMph;
        }
    }

    private static final UUID SERVICE_UUID = UUID.fromString(WalkingPadProtocol.SERVICE_UUID);
    private static final UUID WRITE_UUID = UUID.fromString(WalkingPadProtocol.WRITE_UUID);
    private static final UUID NOTIFY_UUID = UUID.fromString(WalkingPadProtocol.NOTIFY_UUID);
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService ioExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Queue<byte[]> writeQueue = new ConcurrentLinkedQueue<>();

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private boolean writeInFlight;
    private boolean notificationsEnabled;
    private ScheduledFuture<?> keepaliveTask;
    private int seq = 0;
    private float selectedSpeedMph = WalkingPadProtocol.rawToMph(WalkingPadProtocol.DEFAULT_START_RAW);
    private float lastFeedbackMph = 0.0f;
    private float lastTargetMph = 0.0f;
    private String connectionStatus = "Disconnected";
    private String details = "Waiting for connection.";

    WalkingPadBleManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        publishState();
    }

    boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
    }

    boolean isConnected() {
        return bluetoothGatt != null && notificationsEnabled;
    }

    void updateSelectedSpeed(float speedMph) {
        selectedSpeedMph = speedMph;
        publishState();
    }

    void connect() {
        ioExecutor.execute(this::connectInternal);
    }

    void disconnect() {
        ioExecutor.execute(this::disconnectInternal);
    }

    void start() {
        ioExecutor.execute(() -> sendCommand(WalkingPadProtocol.cmdStart(nextSeq(), selectedSpeedMph)));
    }

    void setSpeed() {
        ioExecutor.execute(() -> sendCommand(WalkingPadProtocol.cmdSetSpeed(nextSeq(), selectedSpeedMph)));
    }

    void pause() {
        ioExecutor.execute(() -> sendCommand(WalkingPadProtocol.cmdPause(nextSeq(), selectedSpeedMph)));
    }

    void stop() {
        ioExecutor.execute(() -> sendCommand(WalkingPadProtocol.cmdStop(nextSeq())));
    }

    void shutdown() {
        disconnectInternal();
        ioExecutor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    private void connectInternal() {
        if (!hasConnectPermission()) {
            updateStatus("Permission required", "Grant Bluetooth permission and try again.");
            return;
        }

        BluetoothManager bluetoothManager = appContext.getSystemService(BluetoothManager.class);
        if (bluetoothManager == null) {
            updateStatus("Bluetooth unavailable", "BluetoothManager not present on this device.");
            return;
        }

        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            updateStatus("Bluetooth unavailable", "No Bluetooth adapter found.");
            return;
        }
        if (!adapter.isEnabled()) {
            updateStatus("Bluetooth disabled", "Enable Bluetooth first.");
            return;
        }

        disconnectInternal();
        updateStatus("Connecting", "Opening GATT connection to " + WalkingPadProtocol.DEVICE_ADDRESS);

        BluetoothDevice device = adapter.getRemoteDevice(WalkingPadProtocol.DEVICE_ADDRESS);
        bluetoothGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        if (bluetoothGatt == null) {
            updateStatus("Connect failed", "connectGatt returned null.");
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnectInternal() {
        stopKeepalive();
        notificationsEnabled = false;
        writeCharacteristic = null;
        notifyCharacteristic = null;
        writeQueue.clear();
        writeInFlight = false;
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (Exception ignored) {
            }
            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {
            }
            bluetoothGatt = null;
        }
        updateStatus("Disconnected", "Waiting for connection.");
    }

    private void sendCommand(byte[] payload) {
        if (!notificationsEnabled || bluetoothGatt == null || writeCharacteristic == null) {
            updateStatus(connectionStatus, "Connect before sending commands.");
            return;
        }
        enqueueWrite(payload);
    }

    private void enqueueWrite(byte[] payload) {
        writeQueue.offer(payload);
        drainWrites();
    }

    private void stopKeepalive() {
        if (keepaliveTask != null) {
            keepaliveTask.cancel(true);
            keepaliveTask = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void drainWrites() {
        if (writeInFlight || !notificationsEnabled || bluetoothGatt == null || writeCharacteristic == null) {
            return;
        }
        byte[] payload = writeQueue.poll();
        if (payload == null) {
            return;
        }
        writeInFlight = true;
        int properties = writeCharacteristic.getProperties();
        boolean supportsWrite = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
        boolean supportsWriteNoResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        int writeType = supportsWrite
            ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        if (!supportsWrite && !supportsWriteNoResponse) {
            writeInFlight = false;
            updateStatus("Write failed", "Characteristic is not writable.");
            return;
        }
        writeCharacteristic.setWriteType(writeType);
        writeCharacteristic.setValue(payload);
        boolean started = bluetoothGatt.writeCharacteristic(writeCharacteristic);
        if (!started) {
            writeInFlight = false;
            updateStatus("Write failed", "writeCharacteristic returned false.");
            return;
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            ioExecutor.schedule(() -> {
                if (writeInFlight) {
                    writeInFlight = false;
                    drainWrites();
                }
            }, 40, TimeUnit.MILLISECONDS);
        }
    }

    private int nextSeq() {
        int current = seq & 0xFF;
        seq = (seq + 1) & 0xFF;
        return current;
    }

    private void publishState() {
        UiState state = new UiState(
            bluetoothGatt != null && notificationsEnabled,
            notificationsEnabled,
            connectionStatus,
            details,
            selectedSpeedMph,
            lastFeedbackMph,
            lastTargetMph
        );
        mainHandler.post(() -> listener.onStateChanged(state));
    }

    private void updateStatus(String status, String detailText) {
        connectionStatus = status;
        details = detailText;
        publishState();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (gatt != bluetoothGatt && bluetoothGatt != null) {
                try {
                    gatt.close();
                } catch (Exception ignored) {
                }
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                ioExecutor.execute(() -> {
                    disconnectInternal();
                    updateStatus("Connection error", "GATT status " + status);
                });
                return;
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                updateStatus("Connected", "Discovering services...");
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                ioExecutor.execute(() -> {
                    stopKeepalive();
                    notificationsEnabled = false;
                    writeQueue.clear();
                    writeInFlight = false;
                    if (bluetoothGatt != null) {
                        try {
                            bluetoothGatt.close();
                        } catch (Exception ignored) {
                        }
                        bluetoothGatt = null;
                    }
                    updateStatus("Disconnected", "Connection closed.");
                });
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateStatus("Service discovery failed", "GATT status " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) {
                updateStatus("Service missing", "Did not find " + SERVICE_UUID);
                return;
            }

            writeCharacteristic = service.getCharacteristic(WRITE_UUID);
            notifyCharacteristic = service.getCharacteristic(NOTIFY_UUID);
            if (writeCharacteristic == null || notifyCharacteristic == null) {
                updateStatus("Characteristic missing", "Write or notify characteristic was not found.");
                return;
            }

            boolean notifySet = gatt.setCharacteristicNotification(notifyCharacteristic, true);
            BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
            if (!notifySet || descriptor == null) {
                updateStatus("Notify setup failed", "Could not enable notifications.");
                return;
            }

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) {
                updateStatus("Notify setup failed", "Descriptor write did not start.");
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            if (CCC_DESCRIPTOR_UUID.equals(descriptor.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                notificationsEnabled = true;
                updateStatus("Ready", "Notifications enabled. Keepalive started.");
                stopKeepalive();
                keepaliveTask = ioExecutor.scheduleAtFixedRate(
                    () -> enqueueWrite(WalkingPadProtocol.makeKeepalive(nextSeq())),
                    0,
                    200,
                    TimeUnit.MILLISECONDS
                );
            } else if (CCC_DESCRIPTOR_UUID.equals(descriptor.getUuid())) {
                updateStatus("Notify setup failed", "Descriptor status " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }
            ioExecutor.execute(() -> {
                writeInFlight = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    updateStatus("Write error", "Characteristic write status " + status);
                }
                drainWrites();
            });
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (gatt != bluetoothGatt) {
                return;
            }
            if (!NOTIFY_UUID.equals(characteristic.getUuid())) {
                return;
            }
            byte[] value = characteristic.getValue();
            if (value == null) {
                return;
            }
            renderStatusPacket(gatt, value);
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (gatt != bluetoothGatt) {
                return;
            }
            if (!NOTIFY_UUID.equals(characteristic.getUuid()) || value == null) {
                return;
            }
            renderStatusPacket(gatt, value);
        }

        @SuppressLint("MissingPermission")
        private void renderStatusPacket(BluetoothGatt gatt, byte[] value) {
            WalkingPadProtocol.StatusPacket packet = WalkingPadProtocol.parseStatus(value);
            lastFeedbackMph = packet.feedbackMph;
            lastTargetMph = packet.targetMph;
            String deviceName = gatt.getDevice().getName() != null ? gatt.getDevice().getName() : WalkingPadProtocol.DEVICE_NAME;
            String detailText = String.format(
                Locale.US,
                "Device: %s\nFeedback: %.3f mph\nTarget: %.3f mph\nMax: %.3f mph\nPacket: %d\nRaw: %s",
                deviceName,
                packet.feedbackMph,
                packet.targetMph,
                packet.maxMph,
                packet.packetType,
                packet.rawHex
            );
            updateStatus("Ready", detailText);
        }
    };
}
