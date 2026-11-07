package de.markusbordihn.easynpc.paravexia.village;

/** Budget guard thresholds; budgetNanos is the allowed Easy NPC tick time per server tick. */
public record BudgetSettings(
    long budgetNanos, int windowTicks, int recoverPercent, int recoverTicks) {}
