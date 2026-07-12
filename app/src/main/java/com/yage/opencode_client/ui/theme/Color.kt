package com.yage.opencode_client.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Quiet Tech design language — mirrors the iOS client's DesignTokens.
//
// Single electric-blue identity color; gold reserved strictly for the "AI is
// working" state. Everything else is a neutral gray scale.
// ─────────────────────────────────────────────────────────────────────────────

/** Primary brand — vivid electric blue #3B82F6, matching the iOS client. Same in both themes. */
val BrandPrimary = Color(0xFF3B82F6)
val BrandPrimaryLight = Color(0xFF3B82F6)
/** Gold #D9A621 — the ONLY secondary emphasis, reserved for the transient "AI working" state. */
val BrandGold = Color(0xFFD9A621)
/** Stop button red — lighter and less harsh than Material's default error red. */
val StopRed = Color(0xFFE5484D)

// Dark scheme neutrals (dark is the primary canvas)
val BgDark = Color(0xFF0B0C0E)
val SurfaceDark = Color(0xFF1A1D21)
val ComposerDark = Color(0xFF141619)
val OnSurfaceDark = Color(0xFFE6E8EB)
val OnSurfaceVariantDark = Color(0xFF9BA1A8)
val OutlineDark = Color(0xFF2A2E33)

// Light scheme neutrals
val BgLight = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF0F1F3)
val ComposerLight = Color(0xFFF4F5F6)
val OnSurfaceLight = Color(0xFF15171A)
val OnSurfaceVariantLight = Color(0xFF60656B)
val OutlineLight = Color(0xFFE2E4E7)

/** Subtle tints for write/patch tool cards. */
val ToolWritePatchBackground = Color(0xFFF5F9FF)
val ToolWritePatchBackgroundDark = Color(0xFF252D3D)

// Functional colors (diff viewer, git file status) — carry semantic meaning, not part of accent system.
val AddedLine = Color(0xFFE8F5E9)
val DeletedLine = Color(0xFFFFEBEE)
val ModifiedFile = Color(0xFFFFA726)
val AddedFile = Color(0xFF66BB6A)
val DeletedFile = Color(0xFFEF5350)
val UntrackedFile = Color(0xFF90A4AE)
