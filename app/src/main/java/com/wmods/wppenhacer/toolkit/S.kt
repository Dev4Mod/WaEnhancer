package com.wmods.wppenhacer.toolkit

/** Textos del módulo (inglés y español). Se usan en vez de recursos porque el código corre dentro de WhatsApp. */
object S {
    const val import_chat = 0
    const val import_chat_reading = 1
    const val import_chat_summary = 2
    const val import_chat_destination = 3
    const val import_chat_pick = 4
    const val import_chat_search_hint = 5
    const val import_chat_group_suffix = 6
    const val import_chat_me = 7
    const val import_chat_date_order = 8
    const val import_chat_order_auto = 9
    const val import_chat_order_dmy = 10
    const val import_chat_order_mdy = 11
    const val import_chat_order_ymd = 12
    const val import_chat_media = 13
    const val import_chat_backup = 14
    const val import_chat_note = 15
    const val import_chat_action = 16
    const val import_chat_need_chat = 17
    const val import_chat_progress_backup = 18
    const val import_chat_progress_media = 19
    const val import_chat_progress_messages = 20
    const val import_chat_done_title = 21
    const val import_chat_done_message = 22
    const val import_chat_done_media_failed = 23
    const val import_chat_done_skipped = 24
    const val import_chat_done_backup = 25
    const val import_chat_restart_now = 26
    const val import_chat_later = 27
    const val import_chat_cancelled = 28
    const val import_chat_error_title = 29
    const val import_chat_error_empty = 30
    const val import_chat_error_db_missing = 31
    const val import_chat_error_chat_missing = 32
    const val import_chat_error_nothing = 33
    const val import_chat_error_space_db = 34
    const val import_chat_error_space_media = 35
    const val import_chat_error_open = 36

    const val import_chat_new_suffix = 37

    const val import_chat_mode_existing = 38
    const val import_chat_mode_new = 39
    const val import_chat_pick_contact = 40
    const val import_chat_search_contact_hint = 41
    const val import_chat_use_number = 42

    const val import_chat_done_created = 43

    const val hub_title = 44
    const val deleted_menu = 45
    const val deleted_title = 46
    const val deleted_loading = 47
    const val deleted_empty = 48
    const val deleted_filter_hint = 49
    const val deleted_by_you = 50
    const val deleted_by_author = 51
    const val deleted_by_unknown = 52
    const val deleted_by_admin = 53
    const val deleted_by_admin_of_you = 54
    const val deleted_detail = 55
    const val deleted_detail_no_date = 56
    const val deleted_note_fallback = 57
    const val deleted_close = 58
    const val deleted_col_who = 59
    const val deleted_col_group = 60
    const val deleted_col_chat = 61
    const val deleted_col_author = 62
    const val deleted_col_text = 63
    const val deleted_col_date = 64
    const val deleted_you = 65
    const val deleted_who_admin = 66
    const val deleted_text_unavailable = 67
    const val log_banner_off = 68
    const val log_banner_on = 69
    const val log_enable = 70
    const val log_disable = 71
    const val log_confirm_title = 72
    const val log_confirm_message = 73
    const val log_enabled_toast = 74
    const val log_disabled_toast = 75
    const val log_error = 76
    const val deleted_col_sent = 77
    const val update_menu = 78
    const val update_checking = 79
    const val update_none = 80
    const val update_available = 81
    const val update_download = 82
    const val update_started = 83
    const val update_error = 84
    const val tool_import_desc = 85
    const val tool_deleted_desc = 86
    const val tool_update_desc = 87
    const val update_downloading = 88
    const val update_done = 89
    const val update_restart = 90
    const val backup_menu = 91
    const val tool_backup_desc = 92
    const val backup_create = 93
    const val backup_creating = 94
    const val backup_created = 95
    const val backup_empty = 96
    const val backup_actions_title = 97
    const val backup_restore = 98
    const val backup_delete = 99
    const val backup_restore_confirm = 100
    const val backup_restored = 101
    const val backup_deleted = 102
    const val backup_error = 103
    const val backup_location = 104
    const val backup_no_space = 105
    const val backup_safety_tag = 106
    const val export_menu = 107
    const val tool_export_desc = 108
    const val export_loading = 109
    const val export_pick_chat = 110
    const val export_search_hint = 111
    const val export_options_title = 112
    const val export_fmt_txt = 113
    const val export_fmt_html = 114
    const val export_fmt_csv = 115
    const val export_include_media = 116
    const val export_go = 117
    const val export_working = 118
    const val export_done = 119
    const val export_missing_note = 120
    const val export_error = 121
    const val export_cancelled = 122
    const val export_label_me = 123
    const val export_label_deleted = 124
    const val export_label_deleted_text = 125
    const val export_label_omitted = 126
    const val export_label_attached = 127
    const val export_no_chats = 128
    const val update_ok_title = 129
    const val update_ok_msg = 130
    const val update_fail_title = 131
    const val update_fail_msg = 132
    const val update_after_title = 133
    const val update_after_msg = 134
    const val recover_menu = 135
    const val tool_recover_desc = 136
    const val recover_pick_source = 137
    const val recover_no_sources = 138
    const val recover_source_preimport = 139
    const val recover_loading = 140
    const val recover_pick_chat = 141
    const val recover_chat_item = 142
    const val recover_nothing = 143
    const val recover_confirm = 144
    const val recover_go = 145
    const val recover_working = 146
    const val recover_done = 147
    const val recover_done_created = 148
    const val recover_done_media = 149
    const val recover_restart_hint = 150
    const val recover_error = 151
    const val deleted_tap_hint = 152
    const val deleted_open_error = 153

    private val en = arrayOf(
        "Import chat",
        "Reading file…",
        "%1\$d messages and %2\$d attachments found.\nFrom %3\$s to %4\$s.\nParticipants: %5\$s",
        "Destination chat",
        "Choose chat…",
        "Search chats",
        "%1\$s (group)",
        "Which participant are you?",
        "Date order in the file",
        "Automatic (%1\$s)",
        "Day/Month",
        "Month/Day",
        "Year/Month/Day",
        "Import attachments (%1\$d files)",
        "Back up the database first (recommended)",
        "Only messages older than the first message of the chat are imported, so nothing already there changes. Imported messages exist only on this device. WhatsApp must restart when finished.",
        "Import",
        "Choose the destination chat first",
        "Backing up the database… %1\$d%%",
        "Copying attachments… %1\$d / %2\$d",
        "Importing messages… %1\$d / %2\$d",
        "Import finished",
        "Imported %1\$d messages (%2\$d with attachments).%3\$s",
        "\n%1\$d attachments could not be copied and were imported as text.",
        "\n%1\$d messages were skipped because they are not older than the first message of the chat.",
        "\nBackup: %1\$s",
        "Restart now",
        "Later",
        "Import cancelled. Nothing was changed.",
        "Could not import",
        "No messages were found. Make sure the file is a chat exported from WhatsApp (.txt or .zip).",
        "WhatsApp database not found.",
        "The chosen chat no longer exists.",
        "All the messages are newer than the first message of the chat, so there is nothing to import.",
        "Not enough free space for the database backup. Free some space or turn the backup off.",
        "Not enough free space for the attachments.",
        "The file could not be read: %1\$s",
        "%1\$s (new chat)",
        "Existing chat",
        "Create a new chat with a contact",
        "Choose contact…",
        "Search a contact or type a number",
        "Use number +%1\$s (new chat)",
        "\nNew chat created. It is listed by the date of its last message (%1\$s), so it may be far down the list: search for the contact by name.",
        "WaToolkit",
        "Who deletes messages",
        "Deleted messages",
        "Reading deleted messages…",
        "No deleted messages found.",
        "Filter by contact or chat",
        "You deleted a message",
        "%1\$s deleted a message",
        "A message was deleted",
        "%1\$s (admin) deleted a message from %2\$s",
        "%1\$s (admin) deleted a message of yours",
        "%1\$s · sent %2\$s · deleted %3\$s",
        "%1\$s · sent %2\$s",
        "This WhatsApp does not record who deleted each message or when; only the deleted messages are listed (in individual chats the author is the one who deleted it).",
        "Close",
        "Deleted by",
        "Group",
        "Chat",
        "Message from",
        "Message",
        "Deleted",
        "You",
        "%1\$s (admin)",
        "(text not available)",
        "Message texts are not kept by WhatsApp once deleted. Enable the text log to see the text of future deleted messages.",
        "Text log active · %1\$d messages saved",
        "Enable text log",
        "Disable",
        "Enable text log?",
        "This adds a small table (waimport_log) and two triggers to WhatsApp's message database so the text of each new message is also kept locally. The last 90 days are copied now. Nothing leaves your phone, and you can remove it at any time with \"Disable\". Making a backup first is recommended.",
        "Text log enabled (%1\$d messages saved)",
        "Text log disabled and removed",
        "Could not change the text log: %1\$s",
        "Sent",
        "Check for updates",
        "Checking for updates…",
        "You have the latest version (%1\$s)",
        "Version %1\$s is available (you have %2\$s). Download it?",
        "Download",
        "Downloading… when it finishes, tap the notification to install it, then restart WhatsApp.",
        "Could not check for updates: %1\$s",
        "Bring an exported chat (TXT/ZIP) into WhatsApp, with attachments",
        "See who deleted which message, where, and its text",
        "Look for a new version and download it",
        "Downloading and installing…",
        "Updated. Restart WhatsApp to apply it.",
        "Restart now",
        "Backups",
        "Save and restore WhatsApp's databases",
        "Create backup now",
        "Creating backup…",
        "Backup created: %1\$s",
        "There are no backups yet.",
        "Backup of %1\$s",
        "Restore",
        "Delete",
        "Restore the backup of %1\$s? Your current chats will be replaced by that copy (a safety copy of the current state is made first, so you can undo it). WhatsApp will restart.",
        "Backup restored. Restarting WhatsApp…",
        "Backup deleted",
        "Backup failed: %1\$s",
        "Saved in: %1\$s",
        "Not enough free space for the backup",
        "safety copy",
        "Export chat",
        "Save a chat as TXT, HTML or CSV, with attachments",
        "Loading chats…",
        "Choose a chat to export",
        "Search chat…",
        "Export options",
        "Text (.txt, same format as WhatsApp)",
        "Web page (.html)",
        "Spreadsheet (.csv)",
        "Include attachments (creates a ZIP)",
        "Export",
        "Exporting… %1\$d / %2\$d",
        "Exported %1\$d messages and %2\$d attachments.",
        "\n%1\$d attachments were not found on this phone.",
        "Export failed: %1\$s",
        "Export cancelled",
        "Me",
        "🚫 This message was deleted",
        "%1\$s (deleted message)",
        "<Media omitted>",
        "file attached",
        "No chats found.",
        "✅ Update installed",
        "WaToolkit %1\$s was installed correctly.\n\nRestart WhatsApp now to start using it.",
        "❌ Update failed",
        "The new version was NOT installed.\n\nDetails: %1\$s",
        "✅ WaToolkit updated",
        "Updated successfully to version %1\$s (it was %2\$s). Everything is ready.",
        "Recover chat",
        "Bring back a chat or deleted messages from an old backup",
        "Choose the backup to recover from",
        "There are no backups yet. Create one first in Backups.",
        "before import",
        "Reading backup…",
        "Choose the chat to recover",
        "%1\$s  ·  %2\$d messages",
        "Nothing to recover: this chat already has all the messages of that backup.",
        "Recover %1\$d messages in \"%2\$s\"? (%3\$d from the backup already exist and are skipped.) A safety copy of the current state is made first.",
        "Recover",
        "Recovering… %1\$d / %2\$d",
        "✅ %1\$d messages recovered.",
        "\nThe chat no longer existed and was created again.",
        "\n%1\$d attachments are no longer on this phone, so they can't be shown.",
        "\n\nRestart WhatsApp to see the changes.",
        "Recovery failed: %1\$s",
        "Tap a message to open it in its chat.",
        "Could not open the chat.",
    )

    private val es = arrayOf(
        "Importar chat",
        "Leyendo archivo…",
        "%1\$d mensajes y %2\$d adjuntos encontrados.\nDel %3\$s al %4\$s.\nParticipantes: %5\$s",
        "Chat de destino",
        "Elegir chat…",
        "Buscar chats",
        "%1\$s (grupo)",
        "¿Cuál de los participantes eres tú?",
        "Orden de las fechas del archivo",
        "Automático (%1\$s)",
        "Día/Mes",
        "Mes/Día",
        "Año/Mes/Día",
        "Importar adjuntos (%1\$d archivos)",
        "Hacer antes una copia de la base de datos (recomendado)",
        "Solo se importan los mensajes anteriores al primero del chat, así que lo que ya hay no cambia. Los mensajes importados solo existen en este dispositivo. Al terminar hay que reiniciar WhatsApp.",
        "Importar",
        "Elige primero el chat de destino",
        "Copiando la base de datos… %1\$d%%",
        "Copiando adjuntos… %1\$d / %2\$d",
        "Importando mensajes… %1\$d / %2\$d",
        "Importación terminada",
        "Se importaron %1\$d mensajes (%2\$d con adjunto).%3\$s",
        "\n%1\$d adjuntos no se pudieron copiar y se importaron como texto.",
        "\n%1\$d mensajes se omitieron por no ser anteriores al primero del chat.",
        "\nCopia de seguridad: %1\$s",
        "Reiniciar ahora",
        "Más tarde",
        "Importación cancelada. No se ha cambiado nada.",
        "No se pudo importar",
        "No se encontró ningún mensaje. Comprueba que el archivo es un chat exportado desde WhatsApp (.txt o .zip).",
        "No se encontró la base de datos de WhatsApp.",
        "El chat elegido ya no existe.",
        "Todos los mensajes son posteriores al primero del chat, así que no hay nada que importar.",
        "No hay espacio libre para la copia de la base de datos. Libera espacio o desactiva la copia.",
        "No hay espacio libre para los adjuntos.",
        "No se pudo leer el archivo: %1\$s",
        "%1\$s (chat nuevo)",
        "Chat existente",
        "Crear un chat nuevo con un contacto",
        "Elegir contacto…",
        "Busca un contacto o escribe un número",
        "Usar el número +%1\$s (chat nuevo)",
        "\nChat nuevo creado. Aparece según la fecha de su último mensaje (%1\$s), así que puede estar muy abajo: búscalo por el nombre del contacto.",
        "WaToolkit",
        "Quién borra mensajes",
        "Mensajes borrados",
        "Leyendo mensajes borrados…",
        "No se han encontrado mensajes borrados.",
        "Filtrar por contacto o chat",
        "Borraste un mensaje",
        "%1\$s borró un mensaje",
        "Se borró un mensaje",
        "%1\$s (admin) borró un mensaje de %2\$s",
        "%1\$s (admin) borró un mensaje tuyo",
        "%1\$s · enviado %2\$s · borrado %3\$s",
        "%1\$s · enviado %2\$s",
        "Este WhatsApp no guarda quién borró cada mensaje ni cuándo: solo se listan los mensajes borrados (en un chat individual lo borró su autor).",
        "Cerrar",
        "Borrado por",
        "Grupo",
        "Chat",
        "Mensaje de",
        "Mensaje",
        "Borrado",
        "Tú",
        "%1\$s (admin)",
        "(texto no disponible)",
        "WhatsApp no conserva el texto de un mensaje borrado. Activa el registro de texto para ver el texto de los mensajes que se borren a partir de ahora.",
        "Registro de texto activo · %1\$d mensajes guardados",
        "Activar registro de texto",
        "Desactivar",
        "¿Activar el registro de texto?",
        "Se añade una pequeña tabla (waimport_log) y dos triggers a la base de datos de mensajes de WhatsApp para guardar también en local el texto de cada mensaje nuevo. Ahora se copian los últimos 90 días. Nada sale del teléfono y puedes quitarlo cuando quieras con \"Desactivar\". Se recomienda hacer antes una copia de seguridad.",
        "Registro de texto activado (%1\$d mensajes guardados)",
        "Registro de texto desactivado y eliminado",
        "No se pudo cambiar el registro de texto: %1\$s",
        "Enviado",
        "Buscar actualizaciones",
        "Buscando actualizaciones…",
        "Tienes la última versión (%1\$s)",
        "Hay una versión nueva: %1\$s (tienes la %2\$s). ¿Descargarla?",
        "Descargar",
        "Descargando… al terminar, toca la notificación para instalarla y reinicia WhatsApp.",
        "No se pudo buscar actualizaciones: %1\$s",
        "Trae un chat exportado (TXT/ZIP) a WhatsApp, con adjuntos",
        "Mira quién borró qué mensaje, dónde y su texto",
        "Busca una versión nueva y la descarga",
        "Descargando e instalando…",
        "Actualizado. Reinicia WhatsApp para aplicarlo.",
        "Reiniciar ahora",
        "Copias de seguridad",
        "Guarda y restaura las bases de datos de WhatsApp",
        "Crear copia ahora",
        "Creando copia…",
        "Copia creada: %1\$s",
        "Todavía no hay copias.",
        "Copia del %1\$s",
        "Restaurar",
        "Borrar",
        "¿Restaurar la copia del %1\$s? Tus chats actuales se sustituirán por los de esa copia (antes se hace una copia de seguridad del estado actual, así que se puede deshacer). WhatsApp se reiniciará.",
        "Copia restaurada. Reiniciando WhatsApp…",
        "Copia borrada",
        "Error con la copia: %1\$s",
        "Se guardan en: %1\$s",
        "No hay espacio libre suficiente para la copia",
        "copia previa",
        "Exportar chat",
        "Guarda un chat en TXT, HTML o CSV, con adjuntos",
        "Cargando chats…",
        "Elige el chat a exportar",
        "Buscar chat…",
        "Opciones de exportación",
        "Texto (.txt, mismo formato que WhatsApp)",
        "Página web (.html)",
        "Hoja de cálculo (.csv)",
        "Incluir adjuntos (crea un ZIP)",
        "Exportar",
        "Exportando… %1\$d / %2\$d",
        "Se exportaron %1\$d mensajes y %2\$d adjuntos.",
        "\n%1\$d adjuntos no se encontraron en este teléfono.",
        "Error al exportar: %1\$s",
        "Exportación cancelada",
        "Yo",
        "🚫 Se eliminó este mensaje",
        "%1\$s (mensaje eliminado)",
        "<Multimedia omitido>",
        "archivo adjunto",
        "No se encontraron chats.",
        "✅ Actualización instalada",
        "WaToolkit %1\$s se ha instalado correctamente.\n\nReinicia WhatsApp ahora para empezar a usarla.",
        "❌ No se pudo actualizar",
        "La versión nueva NO se ha instalado.\n\nDetalles: %1\$s",
        "✅ WaToolkit actualizado",
        "Actualizado correctamente a la versión %1\$s (antes la %2\$s). Todo listo.",
        "Recuperar chat",
        "Recupera un chat o mensajes borrados desde una copia antigua",
        "Elige la copia de la que recuperar",
        "Todavía no hay copias. Crea una antes en Copias de seguridad.",
        "antes de importar",
        "Leyendo la copia…",
        "Elige el chat a recuperar",
        "%1\$s  ·  %2\$d mensajes",
        "Nada que recuperar: este chat ya tiene todos los mensajes de esa copia.",
        "¿Recuperar %1\$d mensajes en «%2\$s»? (%3\$d de la copia ya existen y se omiten.) Antes se hace una copia de seguridad del estado actual.",
        "Recuperar",
        "Recuperando… %1\$d / %2\$d",
        "✅ Se recuperaron %1\$d mensajes.",
        "\nEl chat ya no existía y se ha vuelto a crear.",
        "\n%1\$d adjuntos ya no están en el teléfono y no se podrán ver.",
        "\n\nReinicia WhatsApp para ver los cambios.",
        "Error al recuperar: %1\$s",
        "Toca un mensaje para abrirlo en su chat.",
        "No se pudo abrir el chat.",
    )

    /** Texto en el idioma del dispositivo; los argumentos usan el formato de String.format. */
    fun get(id: Int, vararg args: Any): String {
        val table = if (java.util.Locale.getDefault().language == "es") es else en
        val raw = table[id]
        return if (args.isEmpty()) raw else String.format(java.util.Locale.getDefault(), raw, *args)
    }
}
