/*
 This file is part of GNU Taler
 (C) 2019 Taler Systems S.A.

 GNU Taler is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3, or (at your option) any later version.

 GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import java.util.Locale.ROOT

class MainActivity : AppCompatActivity(), OnNavigationItemSelectedListener,
    ResetDialogEventListener {

    private val model: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nav_view.menu.getItem(0).isChecked = true
        nav_view.setNavigationItemSelectedListener(this)

        fab.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setPrompt("Place merchant's QR Code inside the viewfinder rectangle to initiate payment.")
            integrator.initiateScan(listOf("QR_CODE"))
        }
        fab.hide()

        setSupportActionBar(toolbar)
        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.showBalance, R.id.settings, R.id.walletHistory),
            drawer_layout
        )
        toolbar.setupWithNavController(navController, appBarConfiguration)

        model.init()
        model.getBalances()

        model.showProgressBar.observe(this, Observer { show ->
            progress_bar.visibility = if (show) VISIBLE else INVISIBLE
        })

        val triggerPaymentFilter = IntentFilter(HostCardEmulatorService.TRIGGER_PAYMENT_ACTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {

                if (navController.currentDestination?.id == R.id.promptPayment) {
                    return
                }

                val url = p1!!.extras!!.get("contractUrl") as String

                findNavController(R.id.nav_host_fragment).navigate(R.id.action_global_promptPayment)
                model.paymentManager.preparePay(url)

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

        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.dataString
            if (uri != null)
                handleTalerUri(uri, "intent")
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
        //menuInflater.inflate(R.menu.main, menu)
        return false
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
            Snackbar.make(nav_view, "QR Code scan canceled.", LENGTH_SHORT).show()
            return
        }

        val url = scanResult.contents!!
        handleTalerUri(url, "QR code")
    }

    private fun handleTalerUri(url: String, from: String) {
        when {
            url.toLowerCase(ROOT).startsWith("taler://pay/") -> {
                Log.v(TAG, "navigating!")
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_showBalance_to_promptPayment)
                model.paymentManager.preparePay(url)
            }
            url.toLowerCase(ROOT).startsWith("taler://withdraw/") -> {
                Log.v(TAG, "navigating!")
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_showBalance_to_promptWithdraw)
                model.getWithdrawalInfo(url)
            }
            url.toLowerCase(ROOT).startsWith("taler://refund/") -> {
                // TODO implement refunds
                Snackbar.make(nav_view, "Refunds are not yet implemented", LENGTH_SHORT).show()
            }
            else -> {
                Snackbar.make(
                    nav_view,
                    "URL from $from doesn't contain a supported Taler Uri.",
                    LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResetConfirmed() {
        model.dangerouslyReset()
        Snackbar.make(nav_view, "Wallet has been reset", LENGTH_SHORT).show()
    }

    override fun onResetCancelled() {
        Snackbar.make(nav_view, "Reset cancelled", LENGTH_SHORT).show()
    }

}
