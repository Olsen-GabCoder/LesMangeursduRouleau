package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _members = MutableLiveData<Resource<List<User>>>()
    val members: LiveData<Resource<List<User>>> = _members

    // Optionnel: Si tu veux exposer des états plus granulaires pour l'UI
    // private val _isLoading = MutableLiveData<Boolean>()
    // val isLoading: LiveData<Boolean> = _isLoading

    // private val _errorMessage = MutableLiveData<String?>()
    // val errorMessage: LiveData<String?> = _errorMessage

    init {
        fetchMembers()
    }

    fun fetchMembers() {
        viewModelScope.launch {
            // _isLoading.value = true // Si tu utilises les états granulaires
            // _errorMessage.value = null
            _members.postValue(Resource.Loading()) // Publier l'état de chargement

            userRepository.getAllUsers()
                .catch { e ->
                    Log.e("MembersViewModel", "Exception dans le flow getAllUsers", e)
                    // _isLoading.value = false
                    // _errorMessage.value = "Erreur technique: ${e.localizedMessage}"
                    _members.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    // Mettre à jour _members avec la ressource complète
                    _members.postValue(resource)

                    // Si tu utilisais les états granulaires :
                    // when (resource) {
                    //     is Resource.Loading -> _isLoading.value = true
                    //     is Resource.Success -> {
                    //         _isLoading.value = false
                    //         // _membersList.value = resource.data ?: emptyList() // Si tu avais une LiveData séparée pour la liste
                    //     }
                    //     is Resource.Error -> {
                    //         _isLoading.value = false
                    //         _errorMessage.value = resource.message
                    //     }
                    // }
                    Log.d("MembersViewModel", "Received resource: $resource")
                }
        }
    }
}