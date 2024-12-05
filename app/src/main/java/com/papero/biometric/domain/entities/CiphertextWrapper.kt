package com.papero.biometric.domain.entities

data class CiphertextWrapper(
    val ciphertext: ByteArray,
    val initializationVector: ByteArray
)
