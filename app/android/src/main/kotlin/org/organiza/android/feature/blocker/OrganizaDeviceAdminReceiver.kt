package org.organiza.android.feature.blocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import org.organiza.android.OrganizaApp
import org.organiza.android.R

class OrganizaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    override fun onDisabled(context: Context, intent: Intent) {
        OrganizaApp.from(context).core.checkTamper()
    }
}
