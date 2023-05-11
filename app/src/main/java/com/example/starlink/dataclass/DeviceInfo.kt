package com.example.starlink.dataclass

data class DeviceInfo(var name:String, val address: String, val chatList: MutableList<ChatMessage>, var deviceCnt: Int)