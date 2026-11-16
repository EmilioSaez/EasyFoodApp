package app.luxion.fitarchiveapp.ai

/**
 * Conjunto de piezas de la IA que cada plataforma crea una vez y pasa a `App`: almacén de la clave, ajustes y servicio.
 * Todo vive fuera de la base de datos exportable.
 */
class AiModule(
    val vault: AiKeyVault,
    val settings: AiSettingsStore,
    val service: AiService,
    /** Enlaces que el usuario ha corregido en «Foto del plato» y «Apuntar por texto». */
    val aliases: app.luxion.fitarchiveapp.plate.FoodAliasStore = app.luxion.fitarchiveapp.plate.FoodAliasStore(InMemoryAiPreferences()),
) {
    companion object {
        fun create(
            vault: AiKeyVault,
            preferences: AiPreferences,
            today: () -> String,
            client: ClaudeClient = ClaudeClient(apiKey = { vault.read() }),
        ): AiModule {
            val settings = AiSettingsStore(preferences)
            return AiModule(vault, settings, AiService(vault, settings, today, client), app.luxion.fitarchiveapp.plate.FoodAliasStore(preferences))
        }

        /** Módulo sin persistencia (tests y vistas previas): sin clave, la IA queda desactivada. */
        fun inMemory(today: () -> String): AiModule = create(InMemoryAiKeyVault(), InMemoryAiPreferences(), today)
    }
}
