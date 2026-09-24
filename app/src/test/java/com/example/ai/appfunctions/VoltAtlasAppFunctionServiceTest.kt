package com.example.ai.appfunctions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoltAtlasAppFunctionServiceTest {

    @Test
    fun manifest_declaresVoltAtlasAppFunctionService_withRequiredSecurityContracts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val intent = Intent(VoltAtlasAppFunctionService.ACTION_APP_FUNCTION_SERVICE).apply {
            setPackage(context.packageName)
        }

        val resolveInfos = packageManager.queryIntentServices(
            intent,
            PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        )

        assertEquals("Expected exactly one AppFunctionService registration in manifest", 1, resolveInfos.size)

        val serviceInfo = resolveInfos[0].serviceInfo
        assertNotNull("ServiceInfo must be present", serviceInfo)
        assertEquals(VoltAtlasAppFunctionService::class.java.name, serviceInfo.name)
        assertTrue("Service must be exported for system AppFunction routing", serviceInfo.exported)
        assertEquals(
            "Service must require BIND_APP_FUNCTION_SERVICE permission",
            "android.permission.BIND_APP_FUNCTION_SERVICE",
            serviceInfo.permission
        )
    }

    @Test
    fun onBind_returnsLocalBinder_andExposesAppFunctions() {
        val service = VoltAtlasAppFunctionService()
        val intent = Intent(VoltAtlasAppFunctionService.ACTION_APP_FUNCTION_SERVICE)

        val binder = service.onBind(intent)
        assertNotNull("Binder must not be null", binder)
        assertTrue("Binder must be a LocalBinder", binder is VoltAtlasAppFunctionService.LocalBinder)

        val localBinder = binder as VoltAtlasAppFunctionService.LocalBinder
        assertEquals(service, localBinder.getService())
        assertNotNull("AppFunctions instance must not be null", service.appFunctions)
    }
}
