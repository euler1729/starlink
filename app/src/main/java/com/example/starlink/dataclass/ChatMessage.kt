package com.example.starlink.dataclass

data class ChatMessage(val content: String, val type: Int) {
    companion object{
        const val TYPE_WRONG = -1
        const val TYPE_RECEIVED = 0
        const val TYPE_SEND = 1
    }
}