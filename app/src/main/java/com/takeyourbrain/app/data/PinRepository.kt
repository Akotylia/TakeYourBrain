package com.takeyourbrain.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Отдельный файл DataStore для PIN, чтобы не смешивать с обычными настройками
private val Context.securityStore by preferencesDataStore(name = "security")

/** Результат проверки PIN. */
sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsLeft: Int) : PinCheck
    data class LockedOut(val secondsLeft: Long) : PinCheck
}

/**
 * PIN-код. Сам PIN нигде не хранится: только "отпечаток" (хэш PBKDF2) и случайная соль.
 * Из отпечатка PIN восстановить нельзя, можно только проверить, совпадает ли введённый.
 * После 5 ошибок подряд ввод блокируется на минуту (защита от перебора).
 */
class PinRepository(private val context: Context) {

    private object Keys {
        val HASH = stringPreferencesKey("pin_hash")
        val SALT = stringPreferencesKey("pin_salt")
        val FAILED = intPreferencesKey("pin_failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
        val LENGTH = intPreferencesKey("pin_length")
    }

    /** Задан ли PIN. */
    val isPinSet: Flow<Boolean> = context.securityStore.data.map { it[Keys.HASH] != null }

    /**
     * Сколько цифр в PIN: клавиатура проверяет сама, как только введено столько цифр.
     * null — PIN создан до этапа 13 (длина 4–6 неизвестна): один раз нужна кнопка "Готово".
     */
    val pinLength: Flow<Int?> = context.securityStore.data.map { it[Keys.LENGTH] }

    /** Сохранить новый PIN (при первом запуске или при смене). */
    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        context.securityStore.edit {
            it[Keys.HASH] = hash.toBase64()
            it[Keys.SALT] = salt.toBase64()
            it[Keys.LENGTH] = pin.length
            it[Keys.FAILED] = 0
            it.remove(Keys.LOCKED_UNTIL)
        }
    }

    /** Проверить PIN с учётом блокировки после частых ошибок. */
    suspend fun check(pin: String): PinCheck {
        val prefs = context.securityStore.data.first()
        val now = System.currentTimeMillis()
        val lockedUntil = prefs[Keys.LOCKED_UNTIL] ?: 0L
        if (now < lockedUntil) return PinCheck.LockedOut((lockedUntil - now + 999) / 1000)

        val savedHash = prefs[Keys.HASH]?.fromBase64() ?: return PinCheck.Ok // PIN ещё не задан
        val salt = prefs[Keys.SALT]?.fromBase64() ?: return PinCheck.Ok

        // Сравниваем "за одинаковое время", чтобы по скорости ответа нельзя было ничего угадать
        if (MessageDigest.isEqual(hash(pin, salt), savedHash)) {
            context.securityStore.edit {
                it[Keys.FAILED] = 0
                it[Keys.LENGTH] = pin.length // старый PIN: теперь длина известна
            }
            return PinCheck.Ok
        }

        val failed = (prefs[Keys.FAILED] ?: 0) + 1
        return if (failed >= MAX_ATTEMPTS) {
            context.securityStore.edit {
                it[Keys.FAILED] = 0
                it[Keys.LOCKED_UNTIL] = now + LOCKOUT_MS
            }
            PinCheck.LockedOut(LOCKOUT_MS / 1000)
        } else {
            context.securityStore.edit { it[Keys.FAILED] = failed }
            PinCheck.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    /** PBKDF2: много раз "перемешиваем" PIN с солью — так перебор становится медленным. */
    private suspend fun hash(pin: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        /** Новый PIN — всегда 4 цифры (решение 2026-10-28). Старые бывают до 6. */
        const val LENGTH = 4
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 6
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 60_000L
    }
}
