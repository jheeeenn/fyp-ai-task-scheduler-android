package com.example.myapplication.voice

sealed class CreateDraftMove {
    object ConfirmSave : CreateDraftMove()
    object RejectSave : CreateDraftMove()
    data class ChangeField(val field: CreateDraftField, val value: String? = null) : CreateDraftMove()
    data class ProvideField(val field: CreateDraftField, val value: String) : CreateDraftMove()
    data class ApplyUnspecifiedCorrection(val value: String) : CreateDraftMove()
    object Cancel : CreateDraftMove()
    object RequestHelp : CreateDraftMove()
    object Unknown : CreateDraftMove()
}
