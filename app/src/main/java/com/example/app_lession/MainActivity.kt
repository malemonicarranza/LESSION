package com.example.app_lession

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModelProvider
import com.example.app_lession.feature.connection.ConnectionScreen
import com.example.app_lession.feature.connection.ConnectionViewModel
import com.example.app_lession.ui.theme.AppLessionTheme

class MainActivity : ComponentActivity() {
    private val connectionViewModel: ConnectionViewModel by lazy {
        ViewModelProvider(
            this,
            ConnectionViewModel.Factory(applicationContext)
        )[ConnectionViewModel::class.java]
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                connectionViewModel.startScanning()
            } else {
                connectionViewModel.reportPermissionError()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppLessionTheme {
                AppLessionApp(
                    viewModel = connectionViewModel,
                    onRequestBluetoothPermissions = ::requestBluetoothPermissions
                )
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val missingPermissions = requiredBluetoothPermissions()
            .filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

        if (missingPermissions.isEmpty()) {
            connectionViewModel.startScanning()
        } else {
            bluetoothPermissionLauncher.launch(missingPermissions)
        }
    }

    private fun requiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}

@Composable
fun AppLessionApp(
    viewModel: ConnectionViewModel,
    onRequestBluetoothPermissions: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (currentDestination == AppDestinations.HOME) {
                ConnectionScreen(
                    viewModel = viewModel,
                    onRequestPermissions = onRequestBluetoothPermissions,
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                Text(
                    text = "Esta sección se incorporará en una etapa posterior.",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Conexión", R.drawable.ic_home),
    FAVORITES("Sesiones", R.drawable.ic_favorite),
    PROFILE("Configuración", R.drawable.ic_account_box),
}