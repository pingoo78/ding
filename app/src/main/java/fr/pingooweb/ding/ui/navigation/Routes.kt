package fr.pingooweb.ding.ui.navigation

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{programId}"
    const val RUN = "run"
    const val SETTINGS = "settings"

    const val ARG_PROGRAM_ID = "programId"
    const val NEW_PROGRAM = "new"

    fun editor(programId: String = NEW_PROGRAM) = "editor/$programId"
}
