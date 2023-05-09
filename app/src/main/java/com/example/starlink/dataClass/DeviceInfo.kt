package com.example.starlink.dataClass

data class DeviceInfo(var name:String, val address: String, val chatList: MutableList<ChatMessage>, var new: Int)