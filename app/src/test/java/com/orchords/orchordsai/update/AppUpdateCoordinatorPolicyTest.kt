package com.orchords.orchordsai.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCoordinatorPolicyTest {
    @Test
    fun `distribution classification is evidence based and fails unknown closed`() {
        assertEquals(
            AppDistribution.PLAY_STORE,
            classifyDistribution(InstallSourceEvidence(installerPackageName = "com.android.vending")),
        )
        assertEquals(
            AppDistribution.MANAGED_ENTERPRISE,
            classifyDistribution(
                InstallSourceEvidence(
                    installedByPolicy = true,
                    installerPackageName = "com.android.vending",
                )
            ),
        )
        assertEquals(
            AppDistribution.DIRECT_ORCHORDS_APK,
            classifyDistribution(InstallSourceEvidence(directDistributionTrusted = true)),
        )
        assertEquals(AppDistribution.UNKNOWN, classifyDistribution(InstallSourceEvidence()))
        assertEquals(UpdateAdapterKind.NONE, adapterFor(AppDistribution.UNKNOWN))
    }

    @Test
    fun `install requires verified release trust and adapter appropriate state`() {
        val direct = AppUpdateSnapshot(
            distribution = AppDistribution.DIRECT_ORCHORDS_APK,
            mode = AppUpdateMode.NOTIFY,
            state = AppUpdateState.READY_TO_INSTALL,
        )
        assertFalse(canBeginInstall(direct))
        assertTrue(canBeginInstall(direct.copy(releaseTrust = ReleaseTrustState.VERIFIED)))
        assertFalse(
            canBeginInstall(
                direct.copy(
                    state = AppUpdateState.UPDATE_AVAILABLE,
                    releaseTrust = ReleaseTrustState.VERIFIED,
                )
            )
        )

        val play = AppUpdateSnapshot(
            distribution = AppDistribution.PLAY_STORE,
            mode = AppUpdateMode.NOTIFY,
            state = AppUpdateState.UPDATE_AVAILABLE,
            releaseTrust = ReleaseTrustState.VERIFIED,
        )
        assertTrue(canBeginInstall(play))
        assertFalse(canBeginInstall(play.copy(releaseTrust = ReleaseTrustState.BLOCKED)))
    }

    @Test
    fun `state transitions do not skip directly from available to completed`() {
        assertTrue(canTransition(AppUpdateState.CHECKING, AppUpdateState.UPDATE_AVAILABLE))
        assertFalse(canTransition(AppUpdateState.UPDATE_AVAILABLE, AppUpdateState.COMPLETED))
        assertTrue(canTransition(AppUpdateState.INSTALLING, AppUpdateState.WAITING_USER_ACTION))
        assertTrue(canTransition(AppUpdateState.FAILED_RETRYABLE, AppUpdateState.CHECKING))
    }
}
