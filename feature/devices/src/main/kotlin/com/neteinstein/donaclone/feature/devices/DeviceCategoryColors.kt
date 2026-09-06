package com.neteinstein.donaclone.feature.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryAmberContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryAmberContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryAmberOnContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryAmberOnContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBlueContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBlueContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBlueOnContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBlueOnContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBrownContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBrownContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBrownOnContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryBrownOnContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryPurpleContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryPurpleContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryPurpleOnContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryPurpleOnContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryRedContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryRedContainerLight
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryRedOnContainerDark
import com.neteinstein.donaclone.core.designsystem.theme.DonaCategoryRedOnContainerLight

/** The hero-header container/content colors for a device detail screen, per [DeviceCategory]. */
data class DeviceCategoryColors(
    val container: Color,
    val onContainer: Color,
)

/**
 * Picks light/dark accent colors from the actually-applied theme (luminance of the resolved
 * background), not [androidx.compose.foundation.isSystemInDarkTheme], so this stays correct
 * whether dark mode came from the system or from the app's own manual theme-mode override.
 */
@Composable
fun colorsForCategory(category: DeviceCategory): DeviceCategoryColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (category) {
        DeviceCategory.LIGHT, DeviceCategory.DIMMER ->
            if (isDark) {
                DeviceCategoryColors(DonaCategoryAmberContainerDark, DonaCategoryAmberOnContainerDark)
            } else {
                DeviceCategoryColors(DonaCategoryAmberContainerLight, DonaCategoryAmberOnContainerLight)
            }
        DeviceCategory.VALVE, DeviceCategory.FLOOD_SENSOR ->
            if (isDark) {
                DeviceCategoryColors(DonaCategoryBlueContainerDark, DonaCategoryBlueOnContainerDark)
            } else {
                DeviceCategoryColors(DonaCategoryBlueContainerLight, DonaCategoryBlueOnContainerLight)
            }
        DeviceCategory.LOCK, DeviceCategory.SIREN, DeviceCategory.CHIME ->
            if (isDark) {
                DeviceCategoryColors(DonaCategoryRedContainerDark, DonaCategoryRedOnContainerDark)
            } else {
                DeviceCategoryColors(DonaCategoryRedContainerLight, DonaCategoryRedOnContainerLight)
            }
        DeviceCategory.CREPUSCULAR_SENSOR ->
            if (isDark) {
                DeviceCategoryColors(DonaCategoryPurpleContainerDark, DonaCategoryPurpleOnContainerDark)
            } else {
                DeviceCategoryColors(DonaCategoryPurpleContainerLight, DonaCategoryPurpleOnContainerLight)
            }
        DeviceCategory.DOOR_SENSOR, DeviceCategory.GATE_SENSOR, DeviceCategory.GARAGE_DOOR ->
            if (isDark) {
                DeviceCategoryColors(DonaCategoryBrownContainerDark, DonaCategoryBrownOnContainerDark)
            } else {
                DeviceCategoryColors(DonaCategoryBrownContainerLight, DonaCategoryBrownOnContainerLight)
            }
        DeviceCategory.SHUTTER, DeviceCategory.OUTLET, DeviceCategory.TEMPERATURE,
        DeviceCategory.METER, DeviceCategory.GENERIC_SENSOR, DeviceCategory.UNKNOWN,
        ->
            DeviceCategoryColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
