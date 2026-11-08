import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

// One entry per feature doc. The `scenes` ids must resolve against the
// generated registry/scenes.json — scripts/check-registry.mjs enforces it.
const AREA = z.enum([
  'browse',
  'view-play',
  'edit',
  'discover',
  'organize-secure',
  'cloud',
  'customize',
  'utilities',
  'edit-legacy',
]);

const features = defineCollection({
  loader: glob({ pattern: '**/*.mdx', base: './src/content/features' }),
  schema: z.object({
    title: z.string(),
    summary: z.string(),
    area: AREA,
    // In-app path trace rendered as breadcrumb chips, e.g. ["Settings", "Appearance"].
    path: z.array(z.string()).min(1),
    // Ids of related feature pages.
    related: z.array(z.string()).default([]),
    // docs-showcase scene ids this feature renders. Empty until a scene ships.
    scenes: z.array(z.string()).default([]),
    order: z.number().int().default(0),
  }),
});

const pages = defineCollection({
  loader: glob({ pattern: '**/*.mdx', base: './src/content/pages' }),
  schema: z.object({
    title: z.string(),
    summary: z.string(),
  }),
});

export const collections = { features, pages };
