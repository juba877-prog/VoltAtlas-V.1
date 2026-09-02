package com.example.sld

/**
 * IEC 60617 Symbol Source of Truth (Roadmap §1.1).
 *
 * Single formal mapping from every [SldNodeType] to its canonical IEC 60617
 * symbol form, the current VoltAtlas implementation that renders it, and the
 * result of the one-time conformity audit (Gate: symbol library formalization).
 *
 * Policy:
 *  - VoltAtlas follows IEC 60617 "box-logic" simplified symbols exclusively.
 *    ANSI/IEEE 315 pictorial device renderings are NOT permitted on the SLD
 *    canvas (mixing the two conventions is the leading cause of field
 *    misreads). Gallery/decoration symbols must follow the same rule.
 *  - Any new [SldNodeType] MUST add an entry to [SldSymbolLibrary.nodeTypeSymbols]
 *    before its symbol is drawn; [SldSymbolLibraryAuditTest] enforces coverage
 *    so the mapping can never silently drift from the enum again.
 *  - This file documents the audit as of HEAD `cc6daac`; it intentionally
 *    contains ZERO drawing logic. Corrections happen in [SldSymbols], then the
 *    audit status here is updated in the same commit.
 */
enum class SymbolAuditStatus {
    /** Rendering matches the IEC 60617 canonical form. */
    CONFORMANT,
    /** Rendering matches the canonical form; deviations are additive/annotation-level only. */
    CONFORMANT_WITH_NOTES,
    /** Recognizable simplified form, but missing canonical elements or under-differentiated. */
    PARTIAL,
    /** Rendering follows a non-IEC (e.g. ANSI/IEEE pictorial) convention — requires correction. */
    DIVERGENT,
    /** Deliberate non-IEC placeholder (only [SldNodeType.UNKNOWN] may use this). */
    PLACEHOLDER
}

/** Audit record for one [SldNodeType]. */
data class IecSymbolSpec(
    val nodeType: SldNodeType,
    /** Canonical IEC 60617 symbol name (as used in the IEC 60617 database). */
    val canonicalSymbol: String,
    /** IEC 60617 part / family reference (part numbers are stable; sheet IDs omitted). */
    val iecReference: String,
    /** The canonical graphical form an implementer must reproduce. */
    val canonicalForm: String,
    /** The VoltAtlas function/route that renders this node type today. */
    val voltAtlasImplementation: String,
    val auditStatus: SymbolAuditStatus,
    val notes: String = ""
)

object SldSymbolLibrary {

    /**
     * Authoritative SldNodeType → IEC 60617 mapping. Exhaustive over the enum;
     * enforced by [com.example.sld.SldSymbolLibraryAuditTest].
     */
    val nodeTypeSymbols: Map<SldNodeType, IecSymbolSpec> = listOf(
        IecSymbolSpec(
            nodeType = SldNodeType.UTILITY_SOURCE,
            canonicalSymbol = "Network / grid supply source",
            iecReference = "IEC 60617-6 (production, conversion and distribution of electrical energy)",
            canonicalForm = "Circle containing a sine-wave (AC) mark; supply voltage annotated beside it.",
            voltAtlasImplementation = "SldSymbols.drawUtilitySource (circle + sine path)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.TRANSFORMER,
            canonicalSymbol = "Transformer, two windings",
            iecReference = "IEC 60617-4 (transformers, reactors)",
            canonicalForm = "Two overlapping circles on the conductor axis; winding vector group (e.g. Dyn11) and %Z are ANNOTATIONS, not symbol geometry.",
            voltAtlasImplementation = "SldSymbols.drawTransformer (two overlapping circles)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Winding connection / grounding method not yet rendered — tracked as Roadmap §1.2 toggleable data fields (impedancePct already exists in SldDataBlock)."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.GENERATOR,
            canonicalSymbol = "Generator, general (rotating machine)",
            iecReference = "IEC 60617-6 (rotating machines)",
            canonicalForm = "Circle with the letter 'G'.",
            voltAtlasImplementation = "SldSymbols.drawGenerator (circle + 'G' stroke glyph)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.ATS,
            canonicalSymbol = "Change-over / transfer switching contact (break-before-make)",
            iecReference = "IEC 60617-7 (switchgear, controlgear and protective devices)",
            canonicalForm = "Two fixed input contacts, one common output contact, and a movable blade resting on exactly one input; open state shown by blade position.",
            voltAtlasImplementation = "SldSymbols.drawTransferSwitch via AtsSymbolGeometry (geometry-locked to routed wire termini)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "VoltAtlas adds an enclosure roundrect around the contact set (visibility/hit-area aid) and shows state via solid vs dashed blade. Terminals are locked to the routed wire termini so the symbol can never drift from the wires."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.UPS,
            canonicalSymbol = "Static converter composition: rectifier + battery + inverter (block form acceptable)",
            iecReference = "IEC 60617-6 (static converters / secondary cells)",
            canonicalForm = "Rectifier → battery → inverter chain, or an agreed block marked UPS with AC/DC terminal marks.",
            voltAtlasImplementation = "SldSymbols.drawUps (square block, diagonal, AC sine + DC bar marks)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Block shorthand is acceptable box-logic; AC sine (output) and DC bar (battery) terminal marks are present."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.CAPACITOR_BANK,
            canonicalSymbol = "Capacitor, general",
            iecReference = "IEC 60617-4 (passive components)",
            canonicalForm = "Two parallel plates perpendicular to the conductor.",
            voltAtlasImplementation = "SldSymbols.drawCapacitorBank (two parallel plates)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Single-capacitor form stands for the bank; IEC multiplicity ticks / neutral marking not drawn."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.BUS,
            canonicalSymbol = "Busbar",
            iecReference = "IEC 60617-3 (conductors and connecting devices)",
            canonicalForm = "Thick conductor line (weight clearly heavier than feeder wires).",
            voltAtlasImplementation = "Inline heavy rail in SldSymbols.drawSymbol (BUS branch)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.MDB,
            canonicalSymbol = "Main distribution board (switchboard assembly with busbar)",
            iecReference = "IEC 60617-3 busbar + switchboard assembly conventions",
            canonicalForm = "Box-logic: heavy busbar rail with outgoing taps, optionally enclosed; device symbols overlay the outgoing circuits.",
            voltAtlasImplementation = "SldSymbols.drawSymbol MDB branch (heavy rail) + SldCanvas enclosure with MAIN_PANEL_ENCLOSURE_PAD + drawBreaker overlays",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Icon-level rail matches box-logic; the canvas-level main-panel enclosure is a VoltAtlas layout aid consistent with assembly conventions."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.SMDB,
            canonicalSymbol = "Sub-main distribution board (assembly with busbar)",
            iecReference = "IEC 60617-3 busbar + switchboard assembly conventions",
            canonicalForm = "Same box-logic family as MDB; visually distinguishable by annotation/size, not by a different symbol.",
            voltAtlasImplementation = "SldSymbols.drawSymbol SMDB branch (heavy rail only)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "Rendered identically to DB/MCC at icon level and receives no canvas enclosure pad; differentiation relies on label + tier only. Acceptable IEC-wise but under-differentiated — revisit when Roadmap §2.3 busbar ports land."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.DB,
            canonicalSymbol = "Distribution board (assembly with busbar)",
            iecReference = "IEC 60617-3 busbar + switchboard assembly conventions",
            canonicalForm = "Same box-logic family as MDB; enclosure optional at small scales.",
            voltAtlasImplementation = "SldSymbols.drawSymbol DB branch (heavy rail only)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "See SMDB note."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.MCC,
            canonicalSymbol = "Motor control centre (assembly with starter feeder units)",
            iecReference = "IEC 60617-3 busbar + switchboard assembly conventions",
            canonicalForm = "Enclosure with busbar and starter-unit feeders (motor symbols on outgoing circuits).",
            voltAtlasImplementation = "SldSymbols.drawSymbol MCC branch (heavy rail only)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "Rendered identically to DB/SMDB; canonical practice distinguishes MCC by starter units on outgoing circuits."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.BUS_TIE,
            canonicalSymbol = "Circuit-breaker coupler between two busbar sections (tie breaker)",
            iecReference = "IEC 60617-7 (circuit-breaker, general) + IEC 60617-3 busbar conventions",
            canonicalForm = "Circuit-breaker symbol (switch contact with automatic-trip mark) bridging the gap between two busbar rails; open/closed state shown by contact position.",
            voltAtlasImplementation = "SldSymbols.drawSymbol BUS_TIE branch (heavy rail) — positioned on the shared busbar centerline between coupled boards by RiserLayoutEngine.positionBusTiesAndSyncBuses",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "The tie node reuses the heavy-rail branch today; a dedicated breaker glyph between the rails is tracked. Interlock scheme and operating state ride in SldDataBlock.statusText."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.SYNC_BUS,
            canonicalSymbol = "Synchronising busbar (paralleling section)",
            iecReference = "IEC 60617-3 busbar conventions",
            canonicalForm = "Thick conductor line (same canonical form as a normal busbar); parallel/synchronising role is annotation, not symbol geometry.",
            voltAtlasImplementation = "SldSymbols.drawSymbol SYNC_BUS branch (heavy rail) — synthesized by ElectricalNetworkGraphBuilder from shared syncBusId/parallelingGroupId",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Identical heavy-rail form to BUS; the synchronising role is conveyed by GEN_SYNC_CONNECTION wires and the node label, matching IEC annotation-only practice."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.FINAL_CIRCUIT,
            canonicalSymbol = "Outgoing circuit to load (conductor with direction arrow)",
            iecReference = "IEC 60617-3 (conductors) / IEC 60617-6",
            canonicalForm = "Conductor segment terminated by a direction arrow toward the load; protective device symbol may be inserted inline.",
            voltAtlasImplementation = "SldSymbols.drawLoadArrow + SldSymbols.drawBreaker overlays (per-feeder in SldCanvas)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Breaker insertion and its rating annotation are canvas-level; the arrow itself matches the canonical outgoing-circuit form."
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.LOAD,
            canonicalSymbol = "General load / consuming device",
            iecReference = "IEC 60617-6 (consumers / terminal markings)",
            canonicalForm = "Arrow or box representing the point of utilization; motor variant uses the 'M' circle.",
            voltAtlasImplementation = "SldSymbols.drawLoadArrow (arrow form)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        IecSymbolSpec(
            nodeType = SldNodeType.UNKNOWN,
            canonicalSymbol = "No IEC equivalent — deliberately non-canonical",
            iecReference = "n/a",
            canonicalForm = "Placeholder square, intended to be visually 'wrong' so misclassification is obvious.",
            voltAtlasImplementation = "SldSymbols.drawSymbol UNKNOWN branch (stroke square)",
            auditStatus = SymbolAuditStatus.PLACEHOLDER,
            notes = "Must never be persisted as a classified node; ElectricalNetworkGraphBuilder should classify or mark DATA_REQUIRED instead."
        )
    ).associateBy { it.nodeType }

    /**
     * Extended-library inventory: [SldSymbols.IecSymbolType] entries, including
     * those with no dedicated [SldNodeType] (device/decoration symbols used on
     * the canvas, in the symbol gallery, or pending future node types).
     * Audited with the same discipline as the node-type mapping.
     */
    data class ExtendedSymbolSpec(
        val symbolType: SldSymbols.IecSymbolType,
        val iecReference: String,
        val canonicalForm: String,
        val voltAtlasImplementation: String,
        val auditStatus: SymbolAuditStatus,
        val notes: String = ""
    )

    val extendedSymbols: List<ExtendedSymbolSpec> = listOf(
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.AIR_CIRCUIT_BREAKER_ACB,
            iecReference = "IEC 60617-7 (circuit-breaker, general)",
            canonicalForm = "Switch contact with '×' at the moving contact (automatic trip function mark).",
            voltAtlasImplementation = "SldSymbols.drawAcb (enclosure box + diagonal + terminal arcs)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "Box + diagonal shorthand diverges from the canonical contact-with-× form; candidate for a small symbol correction pass alongside drawBreaker/drawMccb."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.MOLDED_CASE_BREAKER_MCCB,
            iecReference = "IEC 60617-7 (circuit-breaker, general)",
            canonicalForm = "Switch contact with '×' at the moving contact; thermal release annotations optional.",
            voltAtlasImplementation = "SldSymbols.drawMccb (rectangle + thermal-magnetic cross)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "Same correction family as ACB."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.SWITCH_DISCONNECTOR,
            iecReference = "IEC 60617-7 (disconnector / isolator)",
            canonicalForm = "Open contact with a short perpendicular bar at the moving contact end (isolation function mark).",
            voltAtlasImplementation = "SldSymbols.drawSwitchDisconnector (terminal dots + lever + isolation bar)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Isolation bar is drawn slightly diagonal rather than strictly perpendicular to the contact path."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.BUSWAY,
            iecReference = "IEC 60617-3 (busbar trunking)",
            canonicalForm = "Trunking outline with conductor lines inside.",
            voltAtlasImplementation = "SldSymbols.drawBusway (trunking box + 3 conductor lines)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Rendered; has no SldNodeType yet (SldEdgeType.BUSWAY exists) — the pending 'Busway SLD marker' requirement."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.DIGITAL_MULTI_METER_DMM,
            iecReference = "IEC 60617-8 (measuring instruments, lamps)",
            canonicalForm = "Instrument circle/square with unit letter; pictorial device renderings are NOT IEC box-logic.",
            voltAtlasImplementation = "SldSymbols.drawDmm (pictorial device: screen, readout lines, buttons)",
            auditStatus = SymbolAuditStatus.DIVERGENT,
            notes = "ANSI/IEEE-style device pictorial — the exact convention mix Roadmap §1.1 warns about. Gallery-only today; must not appear on the SLD canvas, or be redrawn as an instrument circle."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.INDICATION_LAMP,
            iecReference = "IEC 60617-8 (signal lamp)",
            canonicalForm = "Circle with '×' inside.",
            voltAtlasImplementation = "SldSymbols.drawIndicationLamp (circle + ×)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.DIGITAL_KWH_METER,
            iecReference = "IEC 60617-8 (electric energy meter)",
            canonicalForm = "Instrument circle (or square) with 'kWh' unit mark.",
            voltAtlasImplementation = "SldSymbols.drawKwhMeter (box + counter line)",
            auditStatus = SymbolAuditStatus.PARTIAL,
            notes = "Missing the 'kWh' unit mark; the box form is acceptable but the unit letter is the IEC differentiator."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.EARTHING,
            iecReference = "IEC 60617-2 (symbols of general application — earth/ground)",
            canonicalForm = "Vertical stem with three shrinking horizontal bars.",
            voltAtlasImplementation = "SldSymbols.drawEarthing (stem + 3 stepped bars)",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.MOTOR_LOAD,
            iecReference = "IEC 60617-6 (rotating machines — motor, general)",
            canonicalForm = "Circle with letter 'M'.",
            voltAtlasImplementation = "SldSymbols.drawMotorLoad (circle + 'M' + load arrow)",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "The appended load arrow is a VoltAtlas readability aid, not part of the canonical symbol."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.LOAD_ARROW,
            iecReference = "IEC 60617-3 (conductors with direction marking)",
            canonicalForm = "Conductor arrowhead.",
            voltAtlasImplementation = "SldSymbols.drawLoadArrow",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.TRANSFORMER,
            iecReference = "See nodeTypeSymbols[TRANSFORMER]",
            canonicalForm = "Two overlapping circles.",
            voltAtlasImplementation = "SldSymbols.drawTransformer",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Same verdict as the node-type mapping: winding vector group / %Z remain label-level annotations."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.GENERATOR,
            iecReference = "See nodeTypeSymbols[GENERATOR]",
            canonicalForm = "Circle with 'G'.",
            voltAtlasImplementation = "SldSymbols.drawGenerator",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.ATS,
            iecReference = "See nodeTypeSymbols[ATS]",
            canonicalForm = "Two input contacts + movable blade + common output.",
            voltAtlasImplementation = "SldSymbols.drawTransferSwitch / drawAts",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Same verdict as the node-type mapping: VoltAtlas enclosure roundrect + solid/dashed blade state convention."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.CAPACITOR_BANK,
            iecReference = "See nodeTypeSymbols[CAPACITOR_BANK]",
            canonicalForm = "Two parallel plates.",
            voltAtlasImplementation = "SldSymbols.drawCapacitorBank",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Same verdict as the node-type mapping: single-capacitor form stands for the bank."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.UTILITY_SOURCE,
            iecReference = "See nodeTypeSymbols[UTILITY_SOURCE]",
            canonicalForm = "Circle with sine-wave mark.",
            voltAtlasImplementation = "SldSymbols.drawUtilitySource",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.UPS,
            iecReference = "See nodeTypeSymbols[UPS]",
            canonicalForm = "Converter/battery/inverter block.",
            voltAtlasImplementation = "SldSymbols.drawUps",
            auditStatus = SymbolAuditStatus.CONFORMANT_WITH_NOTES,
            notes = "Same verdict as the node-type mapping: block shorthand with AC/DC terminal marks."
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.ATS_3_TERMINAL,
            iecReference = "IEC 60617-7 (change-over / transfer switchgear with mechanical interlock)",
            canonicalForm = "Two fixed input terminals, movable contact arm, common output, and mechanical interlock.",
            voltAtlasImplementation = "SldSymbols.drawAts3Terminal",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.UPS_STATIC_ONLINE,
            iecReference = "IEC 60617-6 (static double-conversion converter with bypass)",
            canonicalForm = "Rectifier bridge triangle, inverter triangle, DC link, and static bypass line.",
            voltAtlasImplementation = "SldSymbols.drawUpsStaticOnline",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.TIE_BREAKER_NO,
            iecReference = "IEC 60617-7 (circuit-breaker in normally-open configuration)",
            canonicalForm = "Horizontal bus stubs with open breaker contact gap and interlock bracket.",
            voltAtlasImplementation = "SldSymbols.drawTieBreakerNo",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.TRANSFORMER_DYN11,
            iecReference = "IEC 60617-4 (two-winding transformer with Dyn11 vector markings)",
            canonicalForm = "Two overlapping circles with primary Delta (Δ) and secondary Star (Y) glyphs.",
            voltAtlasImplementation = "SldSymbols.drawTransformerDyn11",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.GENERATOR_GENSET,
            iecReference = "IEC 60617-6 (rotating synchronous generator with AC wave)",
            canonicalForm = "Stator circle with inner 'G' and AC sine wave mark.",
            voltAtlasImplementation = "SldSymbols.drawGeneratorGenset",
            auditStatus = SymbolAuditStatus.CONFORMANT
        ),
        ExtendedSymbolSpec(
            symbolType = SldSymbols.IecSymbolType.CT_VT_EARTH,
            iecReference = "IEC 60617-4 (instrument CT/VT) & IEC 60617-2 (earth pit)",
            canonicalForm = "Toroidal CT ring on conductor and stepped earth pit bars.",
            voltAtlasImplementation = "SldSymbols.drawCtVtEarth",
            auditStatus = SymbolAuditStatus.CONFORMANT
        )
    )

    /** Lookup helper; throws if a node type was added without an audit entry. */
    fun specFor(nodeType: SldNodeType): IecSymbolSpec =
        requireNotNull(nodeTypeSymbols[nodeType]) {
            "SldSymbolLibrary is missing an IEC 60617 audit entry for $nodeType — add it before rendering this node type."
        }
}