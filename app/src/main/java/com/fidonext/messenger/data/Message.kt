package com.fidonext.messenger.data

data class Message(
    val id: Long,
    val content: String,
    val timestamp: String,
    val isSent: Boolean,
    val encrypted: Boolean = false
)
