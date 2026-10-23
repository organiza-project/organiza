-- ============================================================================
-- Supabase Production & Local Seed Data
-- Deterministic, idempotent seed for cosmetics catalog and battlepass tiers.
-- Contains ZERO test/demo users to prevent production pollution.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Cosmetics Catalog (Avatar Frames, Profile Banners, Name Effects)
-- ----------------------------------------------------------------------------
INSERT INTO public.cosmetics_catalog (id, name, description, type, cost_stars, required_level, asset_or_style_key, is_active)
VALUES
    -- Avatar Frames
    ('frame_stoic_laurel', 'Stoic Laurel Wreath', 'Golden Roman laurels circling the determined mindset', 'avatarFrame', 0, 1, 'frame_stoic_laurel', true),
    ('frame_cyber_glitch', 'Neon Cyber Ring', 'Pulsing dual-layer electric violet and cyan halo', 'avatarFrame', 75, 3, 'frame_cyber_glitch', true),
    ('frame_ember_forge', 'Spartan Ember Forge', 'Molten magma border forged in morning discipline', 'avatarFrame', 150, 6, 'frame_ember_forge', true),
    ('frame_obsidian_shield', 'Obsidian Hex Shield', 'Brushed carbon-fiber defense barrier against distractions', 'avatarFrame', 250, 10, 'frame_obsidian_shield', true),
    ('frame_emperor_celestial', 'Celestial Sovereign Ring', 'Transcendent starlight halo worn by cohort champions', 'avatarFrame', 400, 14, 'frame_emperor_celestial', true),

    -- Profile Banners
    ('banner_deep_space', 'Cosmic Nebula', 'Deep ultraviolet nebula with distant stellar points', 'profileBanner', 0, 1, 'banner_deep_space', true),
    ('banner_cyber_city', 'Neon Tokyo Grid', 'Cyberpunk midnight rain horizon over illuminated towers', 'profileBanner', 100, 4, 'banner_cyber_city', true),
    ('banner_stoic_pantheon', 'Roman Pantheon Pillars', 'Marble column architecture standing timeless against chaos', 'profileBanner', 200, 8, 'banner_stoic_pantheon', true),
    ('banner_aurora_zen', 'Boreal Aurora', 'Glacial emerald ribbons dancing over still waters', 'profileBanner', 300, 12, 'banner_aurora_zen', true),
    ('banner_solar_apotheosis', 'Solar Apotheosis', 'Golden hour cresting the mountain peak of achievement', 'profileBanner', 500, 15, 'banner_solar_apotheosis', true),

    -- Name Effects
    ('effect_clean_white', 'Standard Spartan Bold', 'Crisp high-contrast pure white typography', 'nameEffect', 0, 1, 'effect_clean_white', true),
    ('effect_amber_flame', 'Amber Forge Glow', 'Radiant ember outline with warm subtle shadow bloom', 'nameEffect', 80, 5, 'effect_amber_flame', true),
    ('effect_cyber_gradient', 'Vaporwave Shift', 'Horizontal gradient transitioning from neon pink to cyan', 'nameEffect', 160, 9, 'effect_cyber_gradient', true),
    ('effect_emerald_matrix', 'Matrix Shimmer', 'Sharp emerald luminescence of relentless precision', 'nameEffect', 260, 11, 'effect_emerald_matrix', true),
    ('effect_gilded_emperor', 'Imperial Gold Spark', '24K gold mirror sheen reserved for mastery', 'nameEffect', 450, 16, 'effect_gilded_emperor', true)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    cost_stars = EXCLUDED.cost_stars,
    required_level = EXCLUDED.required_level,
    asset_or_style_key = EXCLUDED.asset_or_style_key,
    is_active = EXCLUDED.is_active;

-- ----------------------------------------------------------------------------
-- 2. Battlepass Tiers (Season 1)
-- ----------------------------------------------------------------------------
INSERT INTO public.battlepass_tiers (tier_level, required_xp, reward_type, reward_id, reward_amount)
VALUES
    (1, 0, 'crest', 'crest_spartan_initiate', 0),
    (2, 500, 'audio', 'audio_gamma_soundscape', 50),
    (3, 1200, 'theme', 'theme_midnight_obsidian', 100),
    (4, 2100, 'emblem', 'emblem_marcus_aurelius', 150),
    (5, 3200, 'badge', 'badge_spartan_cuirass', 250),
    (6, 4500, 'audio', 'audio_seneca_epistles', 350),
    (7, 6000, 'aura', 'aura_chariot_citium', 500),
    (8, 7700, 'crown', 'crown_emperor_laurel', 750),
    (9, 9600, 'theme', 'theme_stoic_fortress', 1000),
    (10, 12000, 'title', 'title_apex_sovereign', 1500)
ON CONFLICT (tier_level) DO UPDATE SET
    required_xp = EXCLUDED.required_xp,
    reward_type = EXCLUDED.reward_type,
    reward_id = EXCLUDED.reward_id,
    reward_amount = EXCLUDED.reward_amount;
