package com.example.myapplication.ai.conversation

data class TemporalObservationInput(
    val requiredInput: RequiredInput,
    val allowedUserMoves: List<AllowedUserMove>
)

object TemporalObservationInputs {
    fun fromUnresolvedComponents(
        datePhraseUnresolved: Boolean,
        timePhraseUnresolved: Boolean
    ): TemporalObservationInput {
        return when {
            datePhraseUnresolved && !timePhraseUnresolved -> TemporalObservationInput(
                RequiredInput.EXACT_DATE,
                listOf(AllowedUserMove.PROVIDE_DATE, AllowedUserMove.CANCEL, AllowedUserMove.REQUEST_HELP)
            )
            timePhraseUnresolved && !datePhraseUnresolved -> TemporalObservationInput(
                RequiredInput.EXACT_TIME,
                listOf(AllowedUserMove.PROVIDE_TIME, AllowedUserMove.CANCEL, AllowedUserMove.REQUEST_HELP)
            )
            else -> TemporalObservationInput(
                RequiredInput.RETRY,
                listOf(AllowedUserMove.RETRY, AllowedUserMove.CANCEL, AllowedUserMove.REQUEST_HELP)
            )
        }
    }
}
