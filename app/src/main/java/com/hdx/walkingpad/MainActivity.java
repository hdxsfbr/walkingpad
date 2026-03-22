package com.hdx.walkingpad;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.SeekBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hdx.walkingpad.databinding.ActivityMainBinding;

import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CONNECT_PERMISSION = 1001;
    private static final float MIN_SPEED_MPH = 0.6f;
    private static final float SPEED_STEP_MPH = 0.1f;

    private ActivityMainBinding binding;
    private WalkingPadBleManager bleManager;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (isBluetoothEnabled()) {
                connectOrDisconnect();
            } else {
                renderBluetoothDisabled();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bleManager = new WalkingPadBleManager(this, this::renderState);

        configureSpeedSlider();
        binding.connectButton.setOnClickListener(v -> connectOrDisconnect());
        binding.startButton.setOnClickListener(v -> bleManager.start());
        binding.applySpeedButton.setOnClickListener(v -> bleManager.setSpeed());
        binding.pauseButton.setOnClickListener(v -> bleManager.pause());
        binding.stopButton.setOnClickListener(v -> bleManager.stop());

        float initialSpeed = progressToSpeed(binding.speedSeekBar.getProgress());
        bleManager.updateSelectedSpeed(initialSpeed);
        updateSpeedLabel(initialSpeed);
        renderBluetoothDisabledIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.shutdown();
        }
    }

    private void configureSpeedSlider() {
        binding.speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = progressToSpeed(progress);
                updateSpeedLabel(speed);
                bleManager.updateSelectedSpeed(speed);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void connectOrDisconnect() {
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_CONNECT_PERMISSION
            );
            return;
        }
        if (bleManager.isConnected()) {
            bleManager.disconnect();
        } else {
            bleManager.connect();
        }
    }

    private void requestBluetoothEnable() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(intent);
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        return adapter != null && adapter.isEnabled();
    }

    private void renderBluetoothDisabledIfNeeded() {
        if (!isBluetoothEnabled()) {
            renderBluetoothDisabled();
        }
    }

    private void renderBluetoothDisabled() {
        binding.connectionText.setText("Bluetooth disabled");
        binding.statusText.setText("Enable Bluetooth, then tap Connect.");
        binding.connectButton.setText(R.string.connect);
        setCommandButtonsEnabled(false);
    }

    private void renderState(WalkingPadBleManager.UiState state) {
        binding.connectionText.setText(state.connectionStatus);
        binding.statusText.setText(state.details);
        binding.connectButton.setText(state.connected ? R.string.disconnect : R.string.connect);
        setCommandButtonsEnabled(state.ready);
        updateSpeedLabel(state.selectedSpeedMph);
    }

    private void setCommandButtonsEnabled(boolean enabled) {
        binding.startButton.setEnabled(enabled);
        binding.applySpeedButton.setEnabled(enabled);
        binding.pauseButton.setEnabled(enabled);
        binding.stopButton.setEnabled(enabled);
    }

    private void updateSpeedLabel(float speedMph) {
        binding.speedLabel.setText(String.format(Locale.US, "Speed: %.1f mph", speedMph));
    }

    private float progressToSpeed(int progress) {
        return MIN_SPEED_MPH + (progress * SPEED_STEP_MPH);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CONNECT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectOrDisconnect();
            } else {
                binding.connectionText.setText("Permission required");
                binding.statusText.setText("Bluetooth permission was denied.");
            }
        }
    }
}
