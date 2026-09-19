package com.example.ai.appfunctions

import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verification test suite for VoltAtlasAppFunctionService.
 * Validates:
 * 1. Manifest declaration, intent-filter action, and security permission guards.
 * 2. Service lifecycle (create, bind, unbind, destroy).
 * 3. End-to-end execution trace of all exposed AppFunction calculations via bound service.
 */
@RunWith(RobolectricTestRunner::class)
class VoltAtlasAppFunctionServiceTest {

    @Test
    fun testManifestDeclaration_registeredWithRequiredPermissionAndExported() {
        val context = RuntimeEnvironment.getApplication()
        val packageManager = context.packageManager

        val intent = Intent(VoltAtlasAppFunctionService.ACTION_APP_FUNCTION_SERVICE).apply {
            setPackage(context.packageName)
        }

        val resolveInfos = packageManager.queryIntentServices(
            intent,
            PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        )

        assertFalse("AppFunctionService must be registered in AndroidManifest.xml", resolveInfos.isEmpty())

        val serviceInfo = resolveInfos.first().serviceInfo
        assertEquals(VoltAtlasAppFunctionService::class.java.name, serviceInfo.name)
        assertTrue("AppFunctionService must be exported for system framework binding", serviceInfo.exported)
        assertEquals(
            "AppFunctionService must require BIND_APP_FUNCTION_SERVICE permission",
            "android.permission.BIND_APP_FUNCTION_SERVICE",
            serviceInfo.permission
        )
    }

    @Test
    fun testServiceBinding_andExecutionTrace_allFunctionsInvokedSuccessfully() {
        runBlocking {
        val serviceController = Robolectric.buildService(VoltAtlasAppFunctionService::class.java)
        val service = serviceController.create().get()

        val bindIntent = Intent(VoltAtlasAppFunctionService.ACTION_APP_FUNCTION_SERVICE)
        val binder = service.onBind(bindIntent)

        assertNotNull("Binder must not be null upon onBind", binder)
        assertTrue("Binder must be an instance of LocalBinder", binder is VoltAtlasAppFunctionService.LocalBinder)

        val localBinder = binder as VoltAtlasAppFunctionService.LocalBinder
        val boundService = localBinder.getService()
        assertSame(service, boundService)

        val appFunctions = boundService.appFunctions
        assertNotNull("AppFunctions instance must be non-null", appFunctions)

        // ── Real Invocation Trace 1: Cable Sizing ──
        val cableParams = CableSizingParams(
            loadCurrentAmps = 63.0,
            voltageV = 400.0,
            cableLengthMeters = 50.0,
            installationMethod = "C",
            ambientTempC = 40,
            powerFactor = 0.85,
            isThreePhase = true,
            maxVdPct = 3.0
        )
        val cableResult = appFunctions.calculateCableSize(cableParams)
        println("[AppFunction Trace] Cable Sizing: In=${cableParams.loadCurrentAmps}A -> Breaker=${cableResult.protectiveDeviceRatingAmps}A, Cable=${cableResult.recommendedSizeMm2}, VD=${cableResult.voltageDropPct}% (${cableResult.status})")
        assertEquals("PASS", cableResult.status)
        assertTrue(cableResult.protectiveDeviceRatingAmps >= 63.0)
        assertTrue(cableResult.voltageDropPct <= 3.0)

        // ── Real Invocation Trace 2: Motor Starter Sizing ──
        val motorParams = MotorStarterParams(
            motorPowerKw = 22.0,
            lineVoltageV = 400.0,
            powerFactor = 0.85,
            efficiencyPct = 91.0,
            isThreePhase = true
        )
        val motorResult = appFunctions.calculateMotorStarter(motorParams)
        println("[AppFunction Trace] Motor Starter: ${motorParams.motorPowerKw}kW -> FLC=${motorResult.flcAmps}A, Method=${motorResult.recommendedMethod}, Contactor=${motorResult.contactorRatingAmps}A")
        assertTrue(motorResult.flcAmps in 38.0..48.0)
        assertTrue(motorResult.contactorRatingAmps > motorResult.flcAmps)

        // ── Real Invocation Trace 3: Power Factor Correction ──
        val pfcParams = PfcParams(
            activePowerKw = 150.0,
            initialPowerFactor = 0.78,
            targetPowerFactor = 0.95,
            lineVoltageV = 400.0
        )
        val pfcResult = appFunctions.calculatePowerFactorCorrection(pfcParams)
        println("[AppFunction Trace] Power Factor: ${pfcParams.activePowerKw}kW (PF ${pfcParams.initialPowerFactor}->${pfcParams.targetPowerFactor}) -> Required=${pfcResult.requiredKvar}kVAR, Bank=${pfcResult.recommendedBankKvar}kVAR")
        assertTrue(pfcResult.requiredKvar in 70.0..85.0)
        assertTrue(pfcResult.currentReductionPct > 10.0)

        // ── Real Invocation Trace 4: Transformer Short Circuit ──
        val txFaultParams = TransformerFaultParams(
            transformerRatingKva = 1600.0,
            primaryVoltageV = 11000.0,
            secondaryVoltageV = 400.0,
            impedancePercentZ = 6.0
        )
        val txFaultResult = appFunctions.calculateTransformerShortCircuit(txFaultParams)
        println("[AppFunction Trace] Transformer Fault: ${txFaultParams.transformerRatingKva}kVA (${txFaultParams.impedancePercentZ}%Z) -> Secondary FLC=${txFaultResult.secondaryFlcAmps}A, Secondary Isc=${txFaultResult.secondaryFaultKa}kA")
        assertTrue(txFaultResult.secondaryFlcAmps in 2200.0..2400.0)
        assertTrue(txFaultResult.secondaryFaultKa in 35.0..42.0)

        // Cleanup service
        serviceController.unbind().destroy()
        }
    }
}
