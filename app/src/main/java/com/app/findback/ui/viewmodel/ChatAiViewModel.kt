package com.app.findback.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.findback.data.repositories.GeminiRepositoryImpl
import com.app.findback.data.source.gemini.GeminiChatService
import com.app.findback.data.source.remote.FirebaseChatAiDataSource
import com.app.findback.domain.models.ChatMessage
import com.app.findback.domain.models.ChatSession
import com.app.findback.domain.models.Post
import com.app.findback.domain.repository.GeminiRepository
import kotlinx.coroutines.launch

class ChatAiViewModel : ViewModel() {
    private val chatAiRepository : GeminiRepository = GeminiRepositoryImpl()
    private val apiService = GeminiChatService()
    private val firebaseChatAiDataSource = FirebaseChatAiDataSource()

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    var messages : LiveData<List<ChatMessage>> = _messages


    //send prompt
    fun sendMessage(message: String, session: ChatSession, posts: List<Post>) {
        viewModelScope.launch {
            try {
                chatAiRepository.sendMessage(message, session, posts)
            } catch (e: Exception) {
                Log.e("CHAT_AI", "sendMessage error", e)
            }
        }
    }
    //get message
    fun getMessages(userId: String){
        viewModelScope.launch {
            try {
                chatAiRepository.getMessages(userId,{ messages ->
                    _messages.postValue(messages)
                })
            }catch (e: Exception){
                Log.e("CHAT_AI", "getMessages error", e)
            }
        }
    }
}