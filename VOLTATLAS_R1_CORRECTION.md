# VOLTATLAS - R-1 Correction Note

**Status:** superseded design decision, recorded deliberately.

## What was wrong

The first implementation of R-1 normalized the value *inside the model*:

```kotlin
// AssistantResponseMapper.metricOrNull  -- REVERTED
value = EngineeringDisplayFormatter.normalizeMetricValue(value, unit)
```

This made `EngineeringMetricUiModel.value` a *bare* number (`"300"`) while
`unit = "mm²"` was held separately.

## Why it was reverted

The model is a grounding artifact. `GroundedResponseValidatorTest.realEndToEnd_...` asserts:

```kotlin
assertEquals(cableResult.details.cableSize_mm2, conductorMetric.value)
```

i.e. **a metric's value must be byte-identical to what the authoritative deterministic
engine returned** (`"300 mm²"`). Rewriting the value at construction time broke that
contract: the UI model no longer reproduced the engine's own string, so the claim could no
longer be traced back to tool output by equality.

That is a real safety property, not an incidental assertion - it is what allows grounding
validation to compare a displayed number against a tool result at all.

Caught by running the existing suite, not by inspection: the regression appeared as
`expected:<300 mm²> but was:<300>` in the AI profile run.

## Where the fix lives instead

The duplication is a *rendering* defect, so it is fixed at the render boundary only:

```kotlin
// AssistantResultCard / AssistantAdditionalCards
val displayValue = EngineeringDisplayFormatter.joinValueAndUnit(metric.value, metric.unit)
```

- Model `value` = verbatim engine string (grounding contract intact).
- Model `unit` = declared unit, as before.
- Display = exactly one unit, always.

`joinValueAndUnit` strips a unit already present in the value and appends the canonical
symbol once. Verified that no raw `"${value} ${unit}"` template join remains anywhere in
`ai/ui`.

## Test coverage for the new boundary

`EngineeringDisplayFormatterTest` asserts the join directly, including the exact production
input `join("70 mm²", "mm²") == "70 mm²"`, so the regression cannot return
through the renderer even though the model is no longer rewritten.

## Consequence for mutation verification

The "metric value normalization reverted" mutation target no longer exists in the mapper;
the equivalent mutation is applied to `joinValueAndUnit` inside
`EngineeringDisplayFormatter`, where the same tests fail.
