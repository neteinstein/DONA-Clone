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
