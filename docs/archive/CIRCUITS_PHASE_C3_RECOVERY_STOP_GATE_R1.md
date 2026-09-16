# VoltAtlas Circuits - C3 Recovery Stop Gate R1 (Isolated-Card Checkpoint)

> **TASK:** UI-CIRCUITS-REDESIGN-V1 / C3 Recovery
> **BASELINE:** 96b3bea (C2, preserved)
> **STATE:** STOPPED UNCOMMITTED at the isolated-card review checkpoint.
> **GIT:** LOCAL ONLY. No push. No golden writes. C4 not started.

---

## 1. Rollback evidence

- Rejected CircuitCardV3 implementation reverted via targeted restore:
  `git restore --source=96b3bea --worktree --` on the 5 tracked C2 files;
  rejected `CircuitsGridScreenC3Test.kt` deleted; no `git clean`, no `reset --hard`, no stash.
- Post-rollback verification: `git diff` = 0 real diff lines (only phantom CRLF flags from
  `core.autocrlf=true`; cleared by re-adding normalized content -> staged diff also 0 lines).
- Baseline compile: BUILD SUCCESSFUL. Baseline targeted suite: 40/40 green
  (C2Test 15, Captures 10, Mapper 15).
- Incident note: scratch cleanup accidentally deleted tracked
  `scratch/update_breaker_catalog.ps1`; restored from 96b3bea in the same session.
  Working tree now contains ONLY intended C3 changes.

## 2. Exact files changed after rebuild (all uncommitted)

| File | Change |
|---|---|
| app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt | Card items block replaced with a call to new screen-local `CircuitCardV3`; imports (+SizingResult, +DsStatusBadge, +TextOverflow, +clip); private helpers: CircuitMetricCellData, CircuitMetricSection, CircuitMetricCell, CircuitScheduleChip. Top shell untouched. |
| .../circuits/CircuitUiModels.kt | Additive six-state CircuitUiStatus (PASS/WARNING/FAIL/INCOMPLETE/ERROR/STANDBY) with exact labels + DsStatusVariant pairing. Summary model untouched. |
| .../circuits/CircuitUiModelMapper.kt | resolveCircuitStatus -> non-lossy exhaustive mapping; STATUS sort comparator made exhaustive (FAIL<WARNING<ERROR<INCOMPLETE<PASS<STANDBY, id tie-breaker preserved). Filter/sort/summary otherwise untouched. |
| .../circuits/CircuitUiModelMapperTest.kt | Status-mapping test updated to six-state contract. |
| .../circuits/CircuitsVisualCaptureTest.kt | +8 ISOLATED-CARD capture tests + renderIsolatedCard/createShortCircuit fixtures (no changes to pre-existing captures). |
| .../circuits/CircuitsGridScreenC3Test.kt | NEW - 12 measured-layout tests. |

Forbidden layers untouched (verified via git status): engines, ViewModel, entities/Room/repositories,
navigation/MainLayout, exports, TransformersScreen, shared design-system defaults, committed goldens.

## 3. Simple card architecture compliance (Section 2 rules)

Root structure exactly as mandated:
`Card(wrap-content) > BoxWithConstraints { rail overlay Box(matchParentSize > CenterStart 4dp,
decorative) ; Column(fillMaxWidth, start=14dp padding for rail) }`

Not used anywhere in the card: fillMaxHeight() on card children (rail overlay only), vertical weight(),
SpaceBetween for vertical distribution, parent Row beside the rail, fixed height, unconstrained nested
BoxWithConstraints, negative offsets, rotated text, zero-width trailing columns, FlowRow for title/metrics,
empty Spacer(weight).

## 4. Collapsed card composition (matches approved mockup)

Title row: name (weight(1f,fill=false), ellipsis) + Schedule chip (normal width only, eligible only) +
intrinsic trailing block (cable size over typed status badge).
Breadcrumb: display-only `Fed from: <verified path>` full width, maxLines 2 at large font scale.
Core metrics row: Load | Ib | In - three weight(1f) cells (label/value), stacked when compact.
Engineering metrics row: Vd+Limit | Loading+Iz | Isc(3-phase)+Icu - same grid, Isc never hidden.
Compact notices: load-conflict (clickable, exact tag/callback) and unsupported-catalog (display-only)
as full-width rows between metrics and footer.
Footer: cable spec/length weight(1f) ellipsized + real expand/details affordance (non-compact) or the
relocated Schedule chip (compact+eligible; title/footer never simultaneous).
Expanded: Operations row (Schedule/Edit/Delete/Ask AI) -> extended specs (result != null only) ->
Manual Overrides panel for EVERY circuit (moved OUT of the legacy result-gate) with null-safe truthful
Auto labels (`Auto (no sizing run yet)`), empty-catalog explicit reason, Apply -> recalculate().
List already carries contentPadding(bottom = 88dp) so the FAB cannot cover override controls.

## 5. Measured layout assertions (all passing, unmerged semantics tree)

- Title/cable/badge/name present with substantial width (>60px) and normal line height (<80px):
  NO vertical character stacking.
- Title, cable size, badge, breadcrumb, footer all contained within card bounds.
- Core metric cells mutually non-overlapping; engineering metric cells mutually non-overlapping.
- Footer horizontal (width > height) at 360dp and 320dp.
- Collapsed wrap-content height bounded (80..900px band) - no giant empty regions.
- 320dp compact: name >40px wide, metrics stacked without overlap, single Schedule chip (footer).
- 412dp non-compact: three-across metric rows verified + footer expand affordance exists.
- fontScale 1.3 and 1.5 at 360dp: renders without overlap, name stays readable.
- Long-name fixture confined inside card bounds.
- PASS / FAIL / STANDBY badges render typed labels; missing result shows em dash while persisted
  load value stays visible.

Note discovered by these tests: at nominal 360dp the card content width is 336dp (screen padding),
which correctly selects the COMPACT stacked layout; the 3-across grid is exercised at 412dp.
Both paths are covered.

## 6. Override Preservation verification

| Requirement | Evidence |
|---|---|
| Reachable without sizing result | test11_expandedOverridesReachable_withoutSizingResult PASSES on a null-result circuit |
| Device type / breaker rating / cable size controls | override_device_type_btn, override_breaker_btn, override_cable_btn all assertExists |
| MODE indicator + Reset to Auto clears all three fields | reset_to_auto_btn_* present; onClick calls updateCircuit(copy(all three = null)) unchanged |
| Apply triggers recalculate | apply_override_btn_* present; onClick = viewModel.recalculate() unchanged |
| Truthful Auto labels, no invented values | Auto strings branch on result != null -> generic wording asserted in test11 |
| Catalog-backed cable options only | catalogSizes from catalogCableSizeOptions(manufacturer, cableType); empty catalog keeps selector inert + explicit reason text |
| Existing update/Room/recalculate paths | viewModel.updateCircuit(...) / viewModel.recalculate() calls byte-preserved from baseline |

## 7. Action / test-tag verification (byte-for-byte)

circuit_card_*, open_panel_schedule_chip_* (title XOR footer, single instance asserted),
open_panel_schedule_btn_* (expanded), edit_circuit_btn_*, delete_circuit_btn_,
confirm_delete_circuit_btn_* (dialog untouched), ask_assistant_circuit_btn_*,
unconfirmed_conflict_badge_* (select + navigate callback preserved), unsupported_warning_* (display-only),
reset_to_auto_btn_*, override_device_type_btn, device_type_option_*, override_breaker_btn,
breaker_option_*A, override_cable_btn, cable_option_*, apply_override_btn_*, add_circuit_fab,
reset_filters_btn (empty state), sort/filter/search shell tags (C2Test 15/15).
New additive tag only: circuit_expand_btn_* (real footer affordance).
Schedule invocation count test: exactly one navigation per tap.

## 8. Test results

- Targeted Circuits suite: 60/60 GREEN
  - CircuitsGridScreenC2Test 15/15 (top shell + summary frozen)
  - CircuitsGridScreenC3Test 12/12 (NEW measured assertions)
  - CircuitsVisualCaptureTest 18/18 (10 baseline gallery + 8 NEW isolated cards)
  - CircuitUiModelMapperTest 15/15 (six-state contract)
- Golden comparisons (not updated): 9 completed, EXACTLY the documented 5 failures
  = 2 intended Circuits top-shell deltas + 3 stale Transformer goldens. Zero Transformer drift.
- Gate wording: No unexpected failures beyond the explicitly evidenced baseline and intended C2 golden deltas.

## 9. Isolated-card captures (build/outputs/roborazzi/circuits_c2/)

IsolatedCard_PASS_Dark_360dp.png, IsolatedCard_PASS_Light_360dp.png, IsolatedCard_FAIL_Dark_360dp.png,
IsolatedCard_STANDBY_Dark_360dp.png, IsolatedCard_LongName_Dark_320dp.png,
IsolatedCard_FontScale1_5_Dark_360dp.png, IsolatedCard_ExpandedOverrides_Dark_360dp.png,
IsolatedCard_MissingResultOverrides_Dark_360dp.png

## 10. Git state

- HEAD: 96b3bea (unchanged). Nothing staged for commit beyond index-normalized identical content.
- git diff --stat (uncommitted): 5 modified files, ~909 insertions / ~511 deletions
  (CircuitsGridScreen.kt dominates: card block replacement + appended composables).
- git status --porcelain:
  M app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt
  M app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt
  M app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt
  M app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt
  M app/src/test/java/com/example/ui/screens/circuits/CircuitsVisualCaptureTest.kt
  ?? CIRCUITS_PHASE_C3_CARD_IMPLEMENTATION_PLAN_V1.md / V2.md / V3.md
  ?? CIRCUITS_PHASE_C3_VISUAL_STOP_GATE_R1.md
  ?? app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC3Test.kt

## 11. Confirmations

- All C3 work REMAINS UNCOMMITTED.
- NO new full-screen-gallery captures were authored in this recovery (the pre-existing committed
  C2 gallery tests still run green unchanged); next step after isolated-card approval is Section 9 gallery.
- NO golden writes. NO push. C4 NOT started.

**STOPPED at the isolated-card review checkpoint - awaiting approval of the card captures before any
full-screen gallery work or local C3 commit.**
