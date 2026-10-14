package com.nutricare.mobile.features.wearables

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Standard Bluetooth SIG Heart Rate service & measurement characteristic
private const val HR_SERVICE_UUID = "0000180d-0000-1000-8000-00805f934bfb"
private const val HR_CHAR_UUID = "00002a37-0000-1000-8000-00805f934bfb"
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f934bfb"

data class WearablesUiState(
    val scanning: Boolean = false,
    val devices: Map<String, String> = emptyMap(), // address -> name
    val connectedName: String? = null,
    val heartRate: Int? = null,
    val status: String = "Scan for a heart-rate watch or strap to begin.",
    val permissionNeeded: Boolean = false,
)

/**
 * Real Bluetooth LE client for the standard Heart Rate profile
 * (what nearly every smartwatch and chest strap exposes).
 */
class WearablesViewModel : ViewModel() {
    val state = MutableStateFlow(WearablesUiState())

    private var gatt: BluetoothGatt? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    val devName = g.device?.name ?: "BLE Smartwatch"
                    state.update { it.copy(status = "Connected — discovering services…", connectedName = devName) }
                    com.nutricare.mobile.core.WatchManager.connectDevice(devName)
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    state.update { it.copy(status = "Disconnected. Tap a device to reconnect.", heartRate = null, connectedName = null) }
                    com.nutricare.mobile.core.WatchManager.disconnect()
                    g.close()
                    if (gatt === g) gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val hrService = g.getService(java.util.UUID.fromString(HR_SERVICE_UUID))
            val hrChar = hrService?.getCharacteristic(java.util.UUID.fromString(HR_CHAR_UUID))
            if (hrChar == null) {
                state.update { it.copy(status = "Connected, but this device doesn't expose a standard Heart Rate service.") }
                return
            }
            g.setCharacteristicNotification(hrChar, true)
            val descriptor = hrChar.getDescriptor(java.util.UUID.fromString(CCC_DESCRIPTOR_UUID))
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            state.update { it.copy(status = "Connected — listening for heart rate…") }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid.toString() == HR_CHAR_UUID && value.isNotEmpty()) {
                // Heart Rate Measurement: flags byte 0; if bit0 == 0 the BPM is uint8 at byte 1
                val flags = value[0].toInt()
                val bpm = if (flags and 0x01 == 0) value[1].toInt() and 0xFF
                else ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                state.update { it.copy(heartRate = bpm) }
                com.nutricare.mobile.core.WatchManager.updateHeartRate(bpm)
            }
        }
    }

    fun scan(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (needed.isNotEmpty()) {
            state.update { it.copy(permissionNeeded = true) }
            return
        }

        state.update { it.copy(scanning = true, devices = emptyMap(), status = "Scanning for nearby devices…") }

        val leScanner = adapter.bluetoothLeScanner ?: run {
            state.update { it.copy(scanning = false, status = "Bluetooth is off. Turn it on and try again.") }
            return
        }

        val callback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val name = result.device?.name ?: return
                val address = result.device.address ?: return
                state.update { s -> s.copy(devices = s.devices + (address to (name.ifBlank { "Unknown device" }))) }
            }
            override fun onScanFailed(errorCode: Int) {
                state.update { it.copy(scanning = false, status = "Scan failed (error $errorCode). Is Bluetooth on?") }
            }
        }

        // Heart-rate, blood-pressure, and general watches
        val filter = android.bluetooth.le.ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid.fromString(HR_SERVICE_UUID))
            .build()
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        leScanner.startScan(listOf(filter), settings, callback)

        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000)
            runCatching { leScanner.stopScan(callback) }
            state.update { s ->
                s.copy(
                    scanning = false,
                    status = if (s.devices.isEmpty()) "No heart-rate devices found. Wear your watch and scan again."
                    else "Found ${s.devices.size} device(s) — tap one to connect.",
                )
            }
        }
    }

    fun connect(context: Context, address: String) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device: BluetoothDevice = manager.adapter.getRemoteDevice(address)
        state.update { it.copy(status = "Connecting to ${device.name ?: "device"}…") }
        gatt?.close()
        gatt = null
        if (Build.VERSION.SDK_INT >= 31) {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            gatt = device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        state.update { it.copy(heartRate = null, connectedName = null, status = "Disconnected.") }
    }

    override fun onCleared() {
        gatt?.close()
        gatt = null
    }
}

@Composable
fun WearablesScreen(onBack: () -> Unit, vm: WearablesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.scan(context)
        else vm.state.update { it.copy(status = "Bluetooth permissions are required to find your watch.") }
    }

    DisposableEffect(Unit) { onDispose { vm.disconnect() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Smartwatch", style = MaterialTheme.typography.headlineMedium)
        }
        Text("Live heart rate over Bluetooth LE", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.height(20.dp))

        // Live HR card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bpm = state.heartRate
                if (bpm != null) {
                    Icon(Icons.Filled.Favorite, null, tint = Rose, modifier = Modifier.size(30.dp))
                    Text("$bpm", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("BPM — live", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                } else {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFA7F3D0), modifier = Modifier.size(30.dp))
                    Text("—", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("No device streaming", style = MaterialTheme.typography.labelSmall)
                }
                state.connectedName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(state.status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 31) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                } else {
                    vm.scan(context)
                }
            },
            enabled = !state.scanning,
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EmeraldDark),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(if (state.scanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.scanning) "Scanning…" else "Scan for devices")
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.devices.entries.toList()) { (address, name) ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Mist),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.connect(context, address) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bluetooth, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
