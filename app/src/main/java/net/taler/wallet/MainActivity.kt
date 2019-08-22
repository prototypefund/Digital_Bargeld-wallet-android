package net.taler.wallet


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import me.zhanghai.android.materialprogressbar.MaterialProgressBar



class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var model: WalletViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        navView.menu.getItem(0).isChecked = true


        navView.setNavigationItemSelectedListener(this)

        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.showBalance, R.id.settings, R.id.walletHistory), drawerLayout)

        findViewById<Toolbar>(R.id.toolbar)
            .setupWithNavController(navController, appBarConfiguration)

        model = ViewModelProviders.of(this)[WalletViewModel::class.java]

        val progressBar = findViewById<MaterialProgressBar>(R.id.progress_bar)
        progressBar.visibility = View.INVISIBLE

        model.init()
        model.getBalances()

        val triggerPaymentFilter = IntentFilter(HostCardEmulatorService.TRIGGER_PAYMENT_ACTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {

                if (model.payStatus.value !is PayStatus.None) {
                    return
                }

                val url = p1!!.extras!!.get("contractUrl") as String

                findNavController(R.id.nav_host_fragment).navigate(R.id.action_showBalance_to_promptPayment)
                model.preparePay(url)

            }
        }, triggerPaymentFilter)

        val nfcConnectedFilter = IntentFilter(HostCardEmulatorService.MERCHANT_NFC_CONNECTED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                Log.v(TAG, "got MERCHANT_NFC_CONNECTED")
                //model.startTunnel()
            }
        }, nfcConnectedFilter)

        val nfcDisconnectedFilter = IntentFilter(HostCardEmulatorService.MERCHANT_NFC_DISCONNECTED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                Log.v(TAG, "got MERCHANT_NFC_DISCONNECTED")
                //model.stopTunnel()
            }
        }, nfcDisconnectedFilter)


        IntentFilter(HostCardEmulatorService.HTTP_TUNNEL_RESPONSE).also { filter ->
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, p1: Intent?) {
                    Log.v("taler-tunnel", "got HTTP_TUNNEL_RESPONSE")
                    model.tunnelResponse(p1!!.getStringExtra("response"))
                }
            }, filter)
        }

        //model.startTunnel()
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_home -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.showBalance)
            }
            R.id.nav_settings -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.settings)
            }
            R.id.nav_history -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.walletHistory)
            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IntentIntegrator.REQUEST_CODE) {
            return
        }

        val scanResult: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (scanResult == null || scanResult.contents == null) {
            val bar: Snackbar = Snackbar.make(
                findViewById(R.id.nav_host_fragment),
                "QR Code scan canceled.",
                Snackbar.LENGTH_SHORT
            )
            bar.show()
            return
        }

        val url = scanResult.contents!!
        if (!url.startsWith("talerpay:")) {
            val bar: Snackbar = Snackbar.make(
                findViewById(R.id.nav_host_fragment),
                "Scanned QR code doesn't contain Taler payment.",
                Snackbar.LENGTH_SHORT
            )
            bar.show()
            return
        }

        Log.v(TAG, "navigating!")

        findNavController(R.id.nav_host_fragment).navigate(R.id.action_showBalance_to_promptPayment)
        model.preparePay(url)
    }


}
