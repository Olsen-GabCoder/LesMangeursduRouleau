package com.lesmangeursdurouleau.app.ui.readings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.domain.usecase.permissions.GrantEditPermissionUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EnterSecretCodeViewModel @Inject constructor(
    private val grantEditPermissionUseCase: GrantEditPermissionUseCase
) : ViewModel() {

    private val _grantPermissionResult = MutableSharedFlow<Resource<Unit>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val grantPermissionResult: SharedFlow<Resource<Unit>> = _grantPermissionResult.asSharedFlow()

    fun validateAndGrantPermission(enteredCode: String) {
        viewModelScope.launch {
            _grantPermissionResult.emit(Resource.Loading())
            val result = grantEditPermissionUseCase(enteredCode)
            _grantPermissionResult.emit(result)

        }
    }
}