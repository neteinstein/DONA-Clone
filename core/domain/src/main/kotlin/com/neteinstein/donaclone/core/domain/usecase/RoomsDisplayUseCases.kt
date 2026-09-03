package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.RoomsDisplayRepository
import kotlinx.coroutines.flow.Flow

class ObserveRoomsExpandedByDefaultUseCase(
    private val repository: RoomsDisplayRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeRoomsExpandedByDefault()
}

class SetRoomsExpandedByDefaultUseCase(
    private val repository: RoomsDisplayRepository,
) {
    suspend operator fun invoke(expanded: Boolean) = repository.setRoomsExpandedByDefault(expanded)
}
