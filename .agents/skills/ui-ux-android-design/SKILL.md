---
name: ui-ux-android-design
description: Design for Android following Material Design 3 Expressive, Material You, and Jetpack Compose patterns. Use for Android-specific design.
argument-hint: "[screen or feature name]"
allowed-tools: Read, Glob, Grep
---

# Android Design Expert (Material Design 3 Expressive)

Design for Android following Material Design 3: $ARGUMENTS

## Expert Knowledge

You are an Android design specialist with mastery of:
- Material Design 3 Expressive (2026)
- Material You dynamic color
- Jetpack Compose design patterns
- Adaptive layouts for all form factors
- Android 16 design features

## Material 3 Expressive Principles

### 1. Expressive Design Foundation

Material 3 Expressive emphasizes:
- **Containment**: Rounded containers signify interactive elements
- **Color**: Vibrant, emotion-driven palettes
- **Shape**: Soft, friendly rounded corners
- **Motion**: Purposeful, delightful animations
- **Size**: Generous touch targets and spacing

```
Key insight: Expressive design uses visual structure
(containment, contrast, color) to make interfaces
more usable, not just more beautiful.
```

### 2. Dynamic Color (Material You)

**Color Roles**:
```
Primary        → Main brand/action color
On Primary     → Text/icons on primary
Primary Container → Secondary prominence
On Primary Container → Text on container

Secondary     → Less prominent elements
Tertiary      → Accent/contrast color

Surface       → Background layers
On Surface    → Primary text
Surface Variant → Cards, sheets
On Surface Variant → Secondary text

Error         → Error states
Outline       → Borders
```

**Tonal Palettes**: Generate from seed color
- Primary tones: 0-100 (0=black, 100=white)
- 13 tonal values per palette
- Automatic light/dark theme generation

### 3. Typography System

**Type Scale**:
| Role | Size | Weight | Line Height | Use |
|------|------|--------|-------------|-----|
| Display Large | 57sp | Regular | 64sp | Hero text |
| Display Medium | 45sp | Regular | 52sp | Large headers |
| Display Small | 36sp | Regular | 44sp | Section headers |
| Headline Large | 32sp | Regular | 40sp | Page titles |
| Headline Medium | 28sp | Regular | 36sp | Card titles |
| Headline Small | 24sp | Regular | 32sp | Subsection |
| Title Large | 22sp | Regular | 28sp | App bar |
| Title Medium | 16sp | Medium | 24sp | List items |
| Title Small | 14sp | Medium | 20sp | Labels |
| Body Large | 16sp | Regular | 24sp | Primary body |
| Body Medium | 14sp | Regular | 20sp | Secondary body |
| Body Small | 12sp | Regular | 16sp | Captions |
| Label Large | 14sp | Medium | 20sp | Buttons |
| Label Medium | 12sp | Medium | 16sp | Chips |
| Label Small | 11sp | Medium | 16sp | Fine print |

**Font**: Roboto, or Google Sans for brand

### 4. Layout Grid

**Margins & Gutters**:
| Device | Margins | Gutter |
|--------|---------|--------|
| Phone | 16dp | 16dp |
| Tablet (600-840dp) | 24dp | 24dp |
| Desktop (840dp+) | 24dp | 24dp |

**Columns**:
- Phone: 4 columns
- Tablet: 8 columns
- Desktop: 12 columns

### 5. Shape System

**Corner Radii**:
| Size | Radius | Use |
|------|--------|-----|
| None | 0dp | Dividers |
| Extra Small | 4dp | Text fields |
| Small | 8dp | Chips, snackbars |
| Medium | 12dp | Cards |
| Large | 16dp | FABs, sheets |
| Extra Large | 28dp | Large FABs |
| Full | 50% | Circular buttons |

### 6. Elevation & Shadows

**Elevation Levels**:
| Level | Elevation | Use |
|-------|-----------|-----|
| 0 | 0dp | Surface |
| 1 | 1dp | Cards (resting) |
| 2 | 3dp | Elevated buttons |
| 3 | 6dp | FAB (resting) |
| 4 | 8dp | Navigation drawer |
| 5 | 12dp | FAB (pressed) |

**Surface Tint**: Instead of shadows, M3 uses tonal elevation (surface tint overlay)

### 7. Material Components

**Buttons**:
- Filled (primary action)
- Filled Tonal (secondary action)
- Outlined (tertiary action)
- Text (lowest emphasis)
- Elevated (needs separation)
- FAB (primary floating action)

**Navigation**:
- Navigation Bar (3-5 destinations)
- Navigation Rail (tablets, landscape)
- Navigation Drawer (many destinations)
- Top App Bar (contextual actions)

**Selection**:
- Checkbox
- Radio Button
- Switch
- Chips (filter, input, suggestion)
- Segmented Button

**Containers**:
- Card (elevated, filled, outlined)
- Dialog
- Bottom Sheet
- Side Sheet
- Snackbar

### 8. States & Interactions

**State Layers** (overlay on components):
| State | Opacity |
|-------|---------|
| Hover | 8% |
| Focus | 12% |
| Pressed | 12% |
| Dragged | 16% |

**Ripple Effect**: Always provide touch feedback

### 9. Motion

**Duration**:
- Short 1: 50ms (fades)
- Short 2: 100ms (simple)
- Medium 1: 200ms (standard)
- Medium 2: 250ms (complex)
- Long 1: 300ms (elaborate)
- Long 2: 400ms (dramatic)

**Easing**:
- Emphasized: For prominent elements
- Emphasized Decelerate: For entering
- Emphasized Accelerate: For exiting
- Standard: For most transitions
- Standard Decelerate: For entering
- Standard Accelerate: For exiting

### 10. Adaptive Design

**Window Size Classes**:
| Class | Width | Typical Device |
|-------|-------|----------------|
| Compact | <600dp | Phone portrait |
| Medium | 600-840dp | Tablet portrait, Foldable |
| Expanded | >840dp | Tablet landscape, Desktop |

**Canonical Layouts**:
- List-detail (master-detail)
- Feed (scrolling content)
- Supporting pane (side panel)

### 11. Edge-to-Edge Design

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```

- Draw behind system bars
- Handle insets properly
- Tint status/navigation bars appropriately

### 12. Accessibility

**Required**:
- contentDescription for all images/icons
- Touch targets ≥ 48dp × 48dp
- 8dp minimum spacing between targets
- Color contrast 4.5:1 (text), 3:1 (UI)
- Support TalkBack
- Support Switch Access
- Respect font scaling

## Design Deliverables

For: $ARGUMENTS

Provide:
1. Screen layouts with system bars
2. Component specifications
3. Color scheme (light/dark)
4. Typography usage
5. Motion specifications
6. Accessibility requirements
7. Jetpack Compose code examples
