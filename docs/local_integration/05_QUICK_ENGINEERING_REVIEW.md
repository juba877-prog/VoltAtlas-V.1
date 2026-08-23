# Quick Engineering Hub Verification & Parity Audit

## 1. Module Inventory & Engine Routing

The Quick Engineering Hub hosts 8 standalone calculation scratchpads that operate purely in-memory without mutating active project persistence:

| Module ID | Screen Name | Engine / Formula Reference | Model File | Test File | Parity Verified |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `cable_sizing` | Quick Cable Sizing | `CableSizingEngine` & `Catalog` | Stateless | `QuickCableCalcTest.kt` | **PASS** |
| `voltage_drop` | Quick Voltage Drop V2 | Standard IEC 60364-5-52 Ohm/km | `VoltageDropV2Models.kt` | `VoltageDropV2CalculationTest.kt`, `VoltageDropV2ProvenanceTest.kt` | **PASS** |
| `load_current` | Quick Load Current V2 | I = P / (sqrt(3) * V * cosPhi) / I = S / (sqrt(3) * V) | `LoadCurrentV2Models.kt` | `LoadCurrentV2CalculationTest.kt` | **PASS** |
| `power_triangle`| Power Triangle | S = sqrt(P^2 + Q^2), PF = P / S | `ElectricalConverters.kt` | `ElectricalConvertersTest.kt` | **PASS** |
| `pfc` | Power Factor Correction V2 | Q_c = P * (tanPhi1 - tanPhi2) | `PfcV2Models.kt` | `PfcV2CalculationTest.kt` | **PASS** |
| `transformer_quick` | Quick Transformer | Standard IEC 60076 tables | `ElectricalConverters.kt` | `ElectricalConvertersTest.kt` | **PASS** |
| `transformer_sc` | Transformer Short Circuit | I_sc = I_fl / %Z | `ShortCircuitEngine.kt` | `ShortCircuitEngineTest.kt` | **PASS** |
| `lighting` | Lighting Lumen Calc | Lumen Method: N = (E * A) / (F * UF * MF) | `LightingEngine.kt` | `LightingEngineTest.kt` | **PASS** |

## 2. Demand Factors & Standards Consistency

All integrated calculators strictly enforce standard VoltAtlas demand factors:
- `LTG = 0.90`
- `SO = 0.60`
- `PWR = 0.80`
- `MTR = 0.80`
- `HVAC = 0.90`
- `MISC = 1.00`
