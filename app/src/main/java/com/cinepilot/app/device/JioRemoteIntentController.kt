package com.cinepilot.app.device

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/**
 * Control mechanism based on the intent action published by the Jio Bluetooth remote.
 *
 * Verified evidence for this mechanism:
 *   - The JioHome manifest declares activity `com.jio.home.cleanarchitecture.common.screen.RemoteShortcutActivity`
 *     with an intent-filter for action `com.jio.btremoteapp.action.OPEN_BT_REMOTE`
 *     and category DEFAULT. (observed in the supplied APK's AndroidManifest.xml)
 *   - On the test device, PackageManager resolves the action to
 *     `com.jio.home/.cleanarchitecture.common.screen.RemoteShortcutActivity`.
 *
 * What this mechanism is:
 *   - It opens the Jio remote application's UI on this phone.
 *
 * What this mechanism is NOT, and must never be claimed as:
 *   - It does not by itself send a key to a set-top box.
 *   - It does not establish a session with the STB.
 *   - It does not prove the STB is reachable.
 *
 * Because of that, [capabilities] is empty and [sendKey] always reports
 * [ControlResult.Unsupported]. The class exposes [launchRemoteUi] instead, which
 * is the operation we can actually verify.
 */
class JioRemoteIntentController(private val context: Context) : STBController {

    override val id: String = "jio-remote-intent"
    override val displayName: String = "Jio remote app (intent)"

    companion object {
        const val ACTION_OPEN_BT_REMOTE = "com.jio.btremoteapp.action.OPEN_BT_REMOTE"
        private const val TAG = "JioRemoteIntent"
    }

    /**
     * True when some installed package declares an activity for the remote action.
     * We query the package manager rather than trying to start the activity, so the
     * check has no side effect.
     */
    override suspend fun isAvailable(): Boolean = resolveHandler() != null

    /**
     * Empty by construction: the intent opens a UI, it does not transmit a key.
     * Claiming any [RemoteKey] here would be fabricating a capability.
     */
    override fun capabilities(): Set<RemoteKey> = emptySet()

    override suspend fun sendKey(key: RemoteKey): ControlResult = ControlResult.Unsupported(
        "The Jio remote intent opens an app UI; it cannot send '$key' to the set-top box. " +
            "No verified key-transmission mechanism is available."
    )

    /**
     * The package/activity that would handle the action, or null when nothing does.
     *
     * Requires `<queries>` in the manifest (Android 11+ package visibility).
     * Without it this returns null even when com.jio.home is installed.
     */
    fun resolveHandler(): ResolveInfo? {
        val intent = Intent(ACTION_OPEN_BT_REMOTE).addCategory(Intent.CATEGORY_DEFAULT)
        return try {
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve remote intent handler", e)
            null
        }
    }

    /**
     * Launch the Jio remote UI. This is the one operation this mechanism supports.
     * Returns Success only when an activity was genuinely started.
     *
     * Strategy: resolve the handler via PackageManager, then launch with an explicit
     * ComponentName so the system does not need to re-resolve (avoiding a second
     * package-visibility gate on Android 11+).
     */
    fun launchRemoteUi(): ControlResult {
        val handler = resolveHandler()
            ?: return ControlResult.Unsupported(
                "No installed app handles $ACTION_OPEN_BT_REMOTE. " +
                    "Ensure com.jio.home is installed and <queries> is declared in the manifest."
            )

        val component = ComponentName(
            handler.activityInfo.packageName,
            handler.activityInfo.name
        )

        // Explicit intent targeting the resolved component.
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            ControlResult.Success(
                "Opened ${handler.activityInfo.packageName}/${handler.activityInfo.name}"
            )
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "ActivityNotFoundException for $component", e)
            ControlResult.Failure(
                "Activity not found: ${component.flattenToShortString()}. " +
                    "The package may have been uninstalled or the activity removed.",
                e
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException launching $component", e)
            ControlResult.Failure(
                "Permission denied launching ${component.flattenToShortString()}. " +
                    "The target activity may not be exported or requires a signature permission.",
                e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching $component", e)
            ControlResult.Failure(
                "Could not open the Jio remote app: ${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }
    }
}