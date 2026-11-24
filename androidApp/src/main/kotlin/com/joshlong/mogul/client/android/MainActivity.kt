package com.joshlong.mogul.client.android

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.joshlong.mogul.client.App
import com.joshlong.mogul.client.app.AndroidApp
import com.joshlong.mogul.client.platform.appBecameVisible
import com.joshlong.mogul.client.platform.navigateBack

/** Mogul Studio on a tablet: the same studio as the web build, laid out as the desktop is on a wide screen */
class MainActivity : ComponentActivity() {

    // debug builds talk to a gateway on this machine, a private address, which since android 17
    // takes this permission (declared in the debug manifest only). ask before connecting
    private val localNetwork = "android.permission.ACCESS_LOCAL_NETWORK"

    private val askForLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { this.show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the studio keeps clear of the system bars itself (App.kt), and runs its black top bar
        // under the status bar, which takes light icons whatever the theme
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        AndroidApp.attach(this)
        // 'back' goes back a page in the studio, and leaves it from the first one
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigateBack()) {
                    this.isEnabled = false
                    this@MainActivity.onBackPressedDispatcher.onBackPressed()
                    this.isEnabled = true
                }
            }
        })
        if (this.needsLocalNetwork()) this.askForLocalNetwork.launch(this.localNetwork) else this.show()
    }

    override fun onResume() {
        super.onResume()
        appBecameVisible()
    }

    private fun needsLocalNetwork(): Boolean {
        val requested = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        return this.localNetwork in requested && this.checkSelfPermission(this.localNetwork) != PackageManager.PERMISSION_GRANTED
    }

    private fun show() {
        this.setContent {
            App()
        }
    }
}
