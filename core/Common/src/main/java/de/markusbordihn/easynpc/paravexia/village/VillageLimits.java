package de.markusbordihn.easynpc.paravexia.village;

/** Server-side caps a village spec must respect, taken from village.cfg. */
public record VillageLimits(
    int maxNpcsPerVillage, int maxVillageNpcs, int maxVillageRadius, int maxWakeRadius) {}
