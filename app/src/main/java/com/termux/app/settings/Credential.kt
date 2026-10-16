package com.termux.app.settings

/**
 * One stored credential: an account the user owns, that the agent is allowed to use.
 *
 * The [envVar] is the point of the whole thing. It is the name the corresponding CLI already looks
 * for — `gh` reads `GH_TOKEN`, `vercel` reads `VERCEL_TOKEN`, `supabase` reads
 * `SUPABASE_ACCESS_TOKEN` — so exporting the value under that exact name means the CLI works the
 * moment it is sourced, with no wrapper script and no per-tool configuration.
 */
data class Credential(
    /** Stable storage key. Never shown to the user. */
    val id: String,
    /** Human label, e.g. "GitHub". */
    val label: String,
    /** The environment variable name the value is exported as. */
    val envVar: String,
    /** Where to get one, when this is a builtin. */
    val url: String = "",
    /** False for entries the user added themselves. */
    val builtin: Boolean = true,
) {
    /**
     * A hint for the row, phrased as what the key is *for* rather than a label restated.
     *
     * "Deploy previews and project linking" tells the user why they are being asked for something
     * that does not say "Vercel"; "Vercel" again would not.
     */
    val purpose: String get() = PURPOSE[envVar].orEmpty()
}

/**
 * The services offered out of the box.
 *
 * This is a curated list, not a directory. Twelve is the point where it stops being a list you read
 * and starts being a list you scan; anything past that is what [com.termux.app.settings.SettingsScreen]'s
 * custom entry is for. The selection is what a phone-based coding agent is plausibly asked to touch
 * in a week: the three deployment backends, the model providers, and the handful of APIs that show
 * up in finished apps.
 */
object CredentialCatalog {

    val BUILTINS: List<Credential> = listOf(
        Credential(
            id = "github",
            label = "GitHub",
            envVar = "GH_TOKEN",
            url = "https://github.com/settings/tokens",
        ),
        Credential(
            id = "vercel",
            label = "Vercel",
            envVar = "VERCEL_TOKEN",
            url = "https://vercel.com/account/tokens",
        ),
        Credential(
            id = "supabase",
            label = "Supabase",
            envVar = "SUPABASE_ACCESS_TOKEN",
            url = "https://supabase.com/dashboard/account/tokens",
        ),
        Credential(
            id = "anthropic",
            label = "Anthropic",
            envVar = "ANTHROPIC_API_KEY",
            url = "https://console.anthropic.com/settings/keys",
        ),
        Credential(
            id = "openai",
            label = "OpenAI",
            envVar = "OPENAI_API_KEY",
            url = "https://platform.openai.com/api-keys",
        ),
        Credential(
            id = "google",
            label = "Google AI Studio",
            envVar = "GEMINI_API_KEY",
            url = "https://aistudio.google.com/apikey",
        ),
        Credential(
            id = "openrouter",
            label = "OpenRouter",
            envVar = "OPENROUTER_API_KEY",
            url = "https://openrouter.ai/keys",
        ),
        Credential(
            id = "cloudflare",
            label = "Cloudflare",
            envVar = "CLOUDFLARE_API_TOKEN",
            url = "https://dash.cloudflare.com/profile/api-tokens",
        ),
        Credential(
            id = "figma",
            label = "Figma",
            envVar = "FIGMA_ACCESS_TOKEN",
            url = "https://www.figma.com/developers/api#access-tokens",
        ),
        Credential(
            id = "stripe",
            label = "Stripe",
            envVar = "STRIPE_SECRET_KEY",
            url = "https://dashboard.stripe.com/apikeys",
        ),
        Credential(
            id = "sentry",
            label = "Sentry",
            envVar = "SENTRY_AUTH_TOKEN",
            url = "https://sentry.io/settings/account/api/auth-tokens/",
        ),
        Credential(
            id = "resend",
            label = "Resend",
            envVar = "RESEND_API_KEY",
            url = "https://resend.com/api-keys",
        ),
    )

    /** What each variable is for, shown under the field. */
    private val PURPOSE = mapOf(
        "GH_TOKEN" to "Push, pull and open PRs. Also makes the `gh` CLI work.",
        "GITHUB_TOKEN" to "Push, pull and open PRs.",
        "VERCEL_TOKEN" to "Deploy previews and link projects. Makes `vercel` work.",
        "SUPABASE_ACCESS_TOKEN" to "Manage linked projects. Makes `supabase` work.",
        "ANTHROPIC_API_KEY" to "Claude models, if you would rather not go through opencode's provider list.",
        "OPENAI_API_KEY" to "OpenAI models.",
        "GEMINI_API_KEY" to "Gemini models.",
        "OPENROUTER_API_KEY" to "One key covering many models.",
        "CLOUDFLARE_API_TOKEN" to "Workers, Pages and R2.",
        "FIGMA_ACCESS_TOKEN" to "Read design files, export assets.",
        "STRIPE_SECRET_KEY" to "Payments, for apps you build with billing.",
        "SENTRY_AUTH_TOKEN" to "Upload source maps and read issues.",
        "RESEND_API_KEY" to "Send email from the apps you build.",
    )

    fun builtin(id: String): Credential? = BUILTINS.firstOrNull { it.id == id }

    /**
     * A storage id for a user-created entry.
     *
     * Slugified so the id is safe to use as a preference key and readable when debugging, with a
     * timestamp so two entries called "API" do not collide.
     */
    fun customId(label: String): String {
        val slug = label.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
            .take(24)
        return "custom-${if (slug.isEmpty()) "key" else slug}-${System.currentTimeMillis()}"
    }
}