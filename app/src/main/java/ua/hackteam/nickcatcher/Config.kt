package ua.hackteam.nickcatcher

/**
 * ⚠️ УВАГА щодо безпеки:
 * Будь-який ключ, зашитий прямо в код Android-застосунку, можна дістати
 * розпакувавши APK (навіть без root — apktool/jadx за 2 хвилини). Це нормально
 * для приватного проєкту "для своїх", але якщо застосунок колись піде у ширший
 * розповсюд — переносьте виклик Gemini на свій маленький бекенд (наприклад,
 * Firebase Cloud Function), а звідти вже стукайтесь до Google з ключем на
 * сервері. Клієнт тоді викликає лише вашу функцію без ключа всередині.
 *
 * ⚠️ УВАГА щодо самого ключа:
 * Стандартні ключі Google AI Studio (aistudio.google.com) для Gemini API
 * зазвичай виглядають як "AIzaSy...". Ключ, який ти дав ("AQ.Ab8..."),
 * має інший формат — це схоже на тимчасовий OAuth/сесійний токен Google,
 * а не постійний API-ключ. Я не маю мережі в цьому середовищі, щоб
 * перевірити його наживо, тож можливо він не спрацює з Generative Language
 * API. Якщо після збірки отримаєш помилку 400/401 — зайди на
 * https://aistudio.google.com/apikey, створи звичайний API-ключ і встав
 * замість цього.
 */
object Config {
    const val GEMINI_API_KEY = "AQ.Ab8RN6LGmHMHhc-r_E7dN1OLhyt2yTmMCVocqN1rJPoO-aaCiw"
    const val GEMINI_MODEL = "gemini-2.5-flash"
}
