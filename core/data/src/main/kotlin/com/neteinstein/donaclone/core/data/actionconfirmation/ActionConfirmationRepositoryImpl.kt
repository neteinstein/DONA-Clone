package com.neteinstein.donaclone.core.data.actionconfirmation

import com.neteinstein.donaclone.core.database.prefs.ActionConfirmationPreferences
import com.neteinstein.donaclone.core.domain.repository.ActionConfirmationRepository
import kotlinx.coroutines.flow.Flow

class ActionConfirmationRepositoryImpl(
    private val actionConfirmationPreferences: ActionConfirmationPreferences,
) : ActionConfirmationRepository {
    override fun observeActionConfirmationEnabled(): Flow<Boolean> = actionConfirmationPreferences.actionConfirmationEnabled

    override suspend fun setActionConfirmationEnabled(enabled: Boolean) =
        actionConfirmationPreferences.setActionConfirmationEnabled(enabled)
}
