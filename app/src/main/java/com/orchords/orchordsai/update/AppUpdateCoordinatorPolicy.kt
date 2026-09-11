package com.orchords.orchordsai.update

enum class AppDistribution {
    PLAY_STORE,
    DIRECT_ORCHORDS_APK,
    MANAGED_ENTERPRISE,
    UNKNOWN,
}

enum class AppUpdateMode {
    MANUAL,
    NOTIFY,
    AUTO_DOWNLOAD,
    AUTO_INSTALL_WHEN_PERMITTED,
}

enum class AppUpdateState {
    IDLE,
    CHECK_SCHEDULED,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    NOT_ELIGIBLE_YET_ROLLOUT,
    PAUSED_BY_USER,
    BLOCKED_BAD_RELEASE,
    WAITING_NETWORK_POLICY,
    DOWNLOADING,
    DOWNLOADED,
    VERIFYING,
    READY_TO_INSTALL,
    INSTALLING,
    WAITING_USER_ACTION,
    RESTART_REQUIRED,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
}

enum class ReleaseTrustState { UNKNOWN, VERIFIED, BLOCKED }

enum class UpdateAdapterKind { PLAY, DIRECT_APK, MANAGED, NONE }

data class InstallSourceEvidence(
    val installerPackageName: String? = null,
    val initiatingPackageName: String? = null,
    val installedByPolicy: Boolean = false,
    val directDistributionTrusted: Boolean = false,
)

data class AppUpdateSnapshot(
    val distribution: AppDistribution,
    val mode: AppUpdateMode,
    val state: AppUpdateState,
    val releaseTrust: ReleaseTrustState = ReleaseTrustState.UNKNOWN,
    val attemptId: String? = null,
    val targetVersionCode: Long? = null,
)

private val PLAY_INSTALLERS = setOf("com.android.vending")

fun classifyDistribution(evidence: InstallSourceEvidence): AppDistribution = when {
    evidence.installedByPolicy -> AppDistribution.MANAGED_ENTERPRISE
    evidence.installerPackageName in PLAY_INSTALLERS || evidence.initiatingPackageName in PLAY_INSTALLERS ->
        AppDistribution.PLAY_STORE
    evidence.directDistributionTrusted -> AppDistribution.DIRECT_ORCHORDS_APK
    else -> AppDistribution.UNKNOWN
}

fun adapterFor(distribution: AppDistribution): UpdateAdapterKind = when (distribution) {
    AppDistribution.PLAY_STORE -> UpdateAdapterKind.PLAY
    AppDistribution.DIRECT_ORCHORDS_APK -> UpdateAdapterKind.DIRECT_APK
    AppDistribution.MANAGED_ENTERPRISE -> UpdateAdapterKind.MANAGED
    AppDistribution.UNKNOWN -> UpdateAdapterKind.NONE
}

fun canBeginInstall(snapshot: AppUpdateSnapshot): Boolean {
    if (snapshot.releaseTrust != ReleaseTrustState.VERIFIED) return false
    return when (snapshot.distribution) {
        AppDistribution.PLAY_STORE -> snapshot.state == AppUpdateState.UPDATE_AVAILABLE ||
            snapshot.state == AppUpdateState.READY_TO_INSTALL
        AppDistribution.DIRECT_ORCHORDS_APK,
        AppDistribution.MANAGED_ENTERPRISE -> snapshot.state == AppUpdateState.READY_TO_INSTALL
        AppDistribution.UNKNOWN -> false
    }
}

fun canTransition(from: AppUpdateState, to: AppUpdateState): Boolean {
    if (from == to) return true
    return to in when (from) {
        AppUpdateState.IDLE -> setOf(AppUpdateState.CHECK_SCHEDULED, AppUpdateState.CHECKING)
        AppUpdateState.CHECK_SCHEDULED -> setOf(AppUpdateState.CHECKING, AppUpdateState.PAUSED_BY_USER)
        AppUpdateState.CHECKING -> setOf(
            AppUpdateState.UP_TO_DATE,
            AppUpdateState.UPDATE_AVAILABLE,
            AppUpdateState.NOT_ELIGIBLE_YET_ROLLOUT,
            AppUpdateState.BLOCKED_BAD_RELEASE,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.UP_TO_DATE,
        AppUpdateState.NOT_ELIGIBLE_YET_ROLLOUT -> setOf(AppUpdateState.CHECKING, AppUpdateState.CHECK_SCHEDULED)
        AppUpdateState.UPDATE_AVAILABLE -> setOf(
            AppUpdateState.WAITING_NETWORK_POLICY,
            AppUpdateState.DOWNLOADING,
            AppUpdateState.VERIFYING,
            AppUpdateState.INSTALLING,
            AppUpdateState.PAUSED_BY_USER,
            AppUpdateState.BLOCKED_BAD_RELEASE,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.PAUSED_BY_USER -> setOf(AppUpdateState.CHECKING, AppUpdateState.IDLE)
        AppUpdateState.BLOCKED_BAD_RELEASE -> setOf(AppUpdateState.CHECKING, AppUpdateState.IDLE)
        AppUpdateState.WAITING_NETWORK_POLICY -> setOf(
            AppUpdateState.DOWNLOADING,
            AppUpdateState.PAUSED_BY_USER,
            AppUpdateState.FAILED_RETRYABLE,
        )
        AppUpdateState.DOWNLOADING -> setOf(
            AppUpdateState.DOWNLOADED,
            AppUpdateState.PAUSED_BY_USER,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.DOWNLOADED -> setOf(AppUpdateState.VERIFYING, AppUpdateState.FAILED_TERMINAL)
        AppUpdateState.VERIFYING -> setOf(
            AppUpdateState.READY_TO_INSTALL,
            AppUpdateState.BLOCKED_BAD_RELEASE,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.READY_TO_INSTALL -> setOf(
            AppUpdateState.INSTALLING,
            AppUpdateState.WAITING_USER_ACTION,
            AppUpdateState.BLOCKED_BAD_RELEASE,
            AppUpdateState.FAILED_RETRYABLE,
        )
        AppUpdateState.INSTALLING -> setOf(
            AppUpdateState.WAITING_USER_ACTION,
            AppUpdateState.RESTART_REQUIRED,
            AppUpdateState.COMPLETED,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.WAITING_USER_ACTION -> setOf(
            AppUpdateState.INSTALLING,
            AppUpdateState.RESTART_REQUIRED,
            AppUpdateState.FAILED_RETRYABLE,
            AppUpdateState.FAILED_TERMINAL,
        )
        AppUpdateState.RESTART_REQUIRED -> setOf(AppUpdateState.COMPLETED, AppUpdateState.FAILED_RETRYABLE)
        AppUpdateState.COMPLETED,
        AppUpdateState.FAILED_TERMINAL -> setOf(AppUpdateState.IDLE, AppUpdateState.CHECKING)
        AppUpdateState.FAILED_RETRYABLE -> setOf(AppUpdateState.CHECKING, AppUpdateState.IDLE)
    }
}
