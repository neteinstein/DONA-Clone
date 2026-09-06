package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.RoomsDisplayRepository
import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import kotlinx.coroutines.flow.Flow

class ObserveRoomsExpandedByDefaultUseCase(
    private val repository: RoomsDisplayRepository,
) {
    operator fun invoke(tab: RoomsDisplayTab): Flow<Boolean> = repository.observeRoomsExpandedByDefault(tab)
}

class SetRoomsExpandedByDefaultUseCase(
    private val repository: RoomsDisplayRepository,
) {
    suspend operator fun invoke(
        tab: RoomsDisplayTab,
        expanded: Boolean,
    ) = repository.setRoomsExpandedByDefault(tab, expanded)
}

class ObserveRoomOrderUseCase(
    private val repository: RoomsDisplayRepository,
) {
    operator fun invoke(): Flow<List<Int>> = repository.observeRoomOrder()
}

class SetRoomOrderUseCase(
    private val repository: RoomsDisplayRepository,
) {
    suspend operator fun invoke(order: List<Int>) = repository.setRoomOrder(order)
}
